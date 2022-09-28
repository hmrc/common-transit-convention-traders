/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package v2.connectors

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.bouncycastle.util.test.TestFailedException
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status.ACCEPTED
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.OK
import play.api.libs.json.JsResult
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.test.Helpers.await
import play.api.test.Helpers.defaultAwaitTimeout
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.RequestBuilder
import v2.base.CommonGenerators
import v2.base.TestActorSystem
import v2.models.AuditType
import v2.models.DepartureId
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.request.MessageType

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

class V2BaseConnectorSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with CommonGenerators
    with ScalaCheckDrivenPropertyChecks
    with ScalaFutures
    with OptionValues
    with TestActorSystem {

  object Harness extends V2BaseConnector

  "the validation URL for an IE015 message type on localhost should be as expected" in {
    val urlPath = Harness.validationRoute(MessageType.IE015)

    urlPath.toString() mustBe "/transit-movements-validator/messages/IE015/validation"
  }

  "the post departure movements URL on localhost for a given EORI should be as expected" in {
    val eori    = arbitrary[EORINumber].sample.value
    val urlPath = Harness.movementsPostDepartureDeclaration(eori)

    urlPath.toString() mustBe s"/transit-movements/traders/${eori.value}/movements/departures/"
  }

  "the get movement IDs URL on localhost for a given EORI, departure ID and no filter should be as expected" in {
    val eori        = arbitrary[EORINumber].sample.value
    val departureId = arbitrary[DepartureId].sample.value
    val urlPath     = Harness.movementsGetDepartureMessageIds(eori, departureId)

    urlPath.toString() mustBe s"/transit-movements/traders/${eori.value}/movements/departures/${departureId.value}/messages/"
  }

  "the get single departure movement URL on localhost for a given EORI, departure ID and message ID should be as expected" in {
    val eori        = arbitrary[EORINumber].sample.value
    val departureId = arbitrary[DepartureId].sample.value
    val messageId   = arbitrary[MessageId].sample.value
    val urlPath     = Harness.movementsGetDepartureMessage(eori, departureId, messageId)

    urlPath.toString() mustBe s"/transit-movements/traders/${eori.value}/movements/departures/${departureId.value}/messages/${messageId.value}/"
  }

  "the get single departure URL on localhost for a given EORI or departure ID should be as expected" in {
    val eori        = arbitrary[EORINumber].sample.value
    val departureId = arbitrary[DepartureId].sample.value
    val urlPath     = Harness.movementsGetDeparture(eori, departureId)

    urlPath.toString() mustBe s"/transit-movements/traders/${eori.value}/movements/departures/${departureId.value}/"
  }

  "the get all departures on localhost for a given EORI should be as expected" in {
    val eori    = arbitrary[EORINumber].sample.value
    val urlPath = Harness.movementsGetAllDepartures(eori)

    urlPath.toString() mustBe s"/transit-movements/traders/${eori.value}/movements/"
  }

  "the router message submission URL on localhost for a given EORI, departure ID or message ID should be as expected" in forAll(arbitrary[MessageType]) {
    messageType =>
      val eori        = arbitrary[EORINumber].sample.value
      val departureId = arbitrary[DepartureId].sample.value
      val messageId   = arbitrary[MessageId].sample.value
      val urlPath     = Harness.routerRoute(eori, messageType, departureId, messageId)
      urlPath
        .toString() mustBe s"/transit-movements-router/traders/${eori.value}/movements/${messageType.movementType}/${departureId.value}/messages/${messageId.value}/"
  }

  "the auditing URL on localhost for a given audit type should be as expected" in forAll(arbitrary[AuditType]) {
    auditType =>
      val urlPath = Harness.auditingRoute(auditType)

      urlPath.toString() mustBe s"/transit-movements-auditing/audit/${auditType.name}"
  }

  "the conversion URL on localhost for a given message type should be as expected" in forAll(arbitrary[MessageType]) {
    messageType =>
      val urlPath = Harness.conversionRoute(messageType)

      urlPath.toString() mustBe s"/transit-movements-converter/messages/${messageType.code}"
  }

  "HttpResponseHelpers" - new V2BaseConnector() {

    case class TestObject(string: String, int: Int)

    implicit val reads: Reads[TestObject] = Json.reads[TestObject]

    "successfully converting a relevant object using 'as' returns the object" in {
      val sut = mock[HttpResponse]
      when(sut.json).thenReturn(
        Json.obj(
          "string" -> "string",
          "int"    -> 1
        )
      )

      whenReady(sut.as[TestObject]) {
        _ mustBe TestObject("string", 1)
      }
    }

    "unsuccessfully converting an object using 'as' returns a failed future" in {
      val sut = mock[HttpResponse]
      when(sut.json).thenReturn(
        Json.obj(
          "no"  -> "string",
          "no2" -> 1
        )
      )

      val result = sut
        .as[TestObject]
        .map(
          _ => fail("This should have failed")
        )
        .recover {
          case _: JsResult.Exception  => // success
          case x: TestFailedException => x
          case thr                    => fail(s"Test failed in an unexpected way: $thr")
        }

      // we just want the future to complete
      await(result)
    }

    "errorFromStream creates the appropriate failed Future" in {
      val string1  = Gen.alphaNumStr.sample.value
      val string2  = Gen.alphaNumStr.sample.value
      val expected = string1 + string2

      val sut = mock[HttpResponse]
      when(sut.bodyAsSource).thenAnswer(
        _ =>
          Source.fromIterator(
            () => Seq(ByteString(string1), ByteString(string2)).iterator
          )
      )

      when(sut.status).thenReturn(INTERNAL_SERVER_ERROR)

      val result = sut
        .errorFromStream[HttpResponse]
        .map(
          _ => fail("This should have failed")
        )
        .recover {
          case UpstreamErrorResponse(`expected`, INTERNAL_SERVER_ERROR, _, _) => // success
          case x: TestFailedException                                         => x
          case thr =>
            fail(s"Test failed in an unexpected way: $thr")
        }

      // we just want the future to complete
      await(result)
    }

    "error returns a simple upstream error response" in {
      val expected = Gen.alphaNumStr.sample.value
      val sut      = mock[HttpResponse]
      when(sut.body).thenReturn(expected)
      when(sut.status).thenReturn(INTERNAL_SERVER_ERROR)

      val result = sut
        .error[TestObject]
        .map(
          _ => fail("This should have failed")
        )
        .recover {
          case UpstreamErrorResponse(`expected`, INTERNAL_SERVER_ERROR, _, _) => // success
          case x: TestFailedException                                         => x
          case thr =>
            fail(s"Test failed in an unexpected way: $thr")
        }

      await(result)
    }

  }

  "RequestBuilderHelpers" - new V2BaseConnector {

    "executeAccepted returns a unit when accepted" in {
      val response = mock[HttpResponse]
      when(response.status).thenReturn(ACCEPTED)
      val sut = mock[RequestBuilder]
      when(sut.execute[HttpResponse](any(), any())).thenReturn(Future.successful(response))

      whenReady(sut.executeAccepted) {
        unit => unit mustBe a[Unit]
      }

    }

    "executeAccepted returns an error when not accepted" in {
      val response = mock[HttpResponse]
      when(response.status).thenReturn(INTERNAL_SERVER_ERROR)
      when(response.body).thenReturn("error")
      val sut = mock[RequestBuilder]
      when(sut.execute[HttpResponse](any(), any())).thenReturn(Future.successful(response))

      sut.executeAccepted
        .map(
          _ => fail("This should have failed")
        )
        .recover {
          case UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR, _, _) => // success
          case x: TestFailedException                                      => x
          case thr =>
            fail(s"Test failed in an unexpected way: $thr")
        }

    }

    "executeAsStream returns the body as a source when OK" in {
      val response = mock[HttpResponse]
      val source   = Source.empty[ByteString]
      when(response.status).thenReturn(OK)
      when(response.bodyAsSource).thenAnswer(
        _ => source
      )
      val sut = mock[RequestBuilder]
      when(sut.stream[HttpResponse](any(), any())).thenReturn(Future.successful(response))

      whenReady(sut.executeAsStream) {
        _ mustBe source
      }

    }

    "executeAsStream returns an error for an appropriate status code" in {
      val response = mock[HttpResponse]
      when(response.status).thenReturn(INTERNAL_SERVER_ERROR)
      when(response.bodyAsSource).thenAnswer(
        _ => Source.single(ByteString("error"))
      )
      val sut = mock[RequestBuilder]
      when(sut.stream[HttpResponse](any(), any())).thenReturn(Future.successful(response))

      sut.executeAsStream
        .map(
          _ => fail("This should have failed")
        )
        .recover {
          case UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR, _, _) => // success
          case x: TestFailedException                                      => x
          case thr =>
            fail(s"Test failed in an unexpected way: $thr")
        }

    }

  }

}
