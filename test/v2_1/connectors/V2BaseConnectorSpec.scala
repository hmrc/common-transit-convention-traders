/*
 * Copyright 2023 HM Revenue & Customs
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

package v2_1.connectors

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import config.AppConfig
import models.common.EORINumber
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.HeaderNames
import play.api.http.Status.ACCEPTED
import play.api.http.Status.BAD_GATEWAY
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.CREATED
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.NOT_FOUND
import play.api.http.Status.NO_CONTENT
import play.api.http.Status.OK
import play.api.http.Status.SERVICE_UNAVAILABLE
import play.api.libs.json.JsResult
import play.api.libs.json.Json
import play.api.libs.json.Reads
import play.api.test.Helpers.await
import play.api.test.Helpers.defaultAwaitTimeout
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.RequestBuilder
import v2_1.base.TestCommonGenerators
import v2_1.base.TestActorSystem
import v2_1.models.AuditType
import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import v2_1.models.request.MessageType

import scala.annotation.nowarn
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

@nowarn("msg=discarded non-Unit value")
class V2BaseConnectorSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with TestCommonGenerators
    with ScalaCheckDrivenPropertyChecks
    with ScalaFutures
    with OptionValues
    with TestActorSystem {

  object Harness extends V2BaseConnector

  "the validation URL for an IE015 message type on localhost should be as expected" in {
    val urlPath = Harness.validationRoute(MessageType.DeclarationData)

    urlPath.toString() mustBe "/transit-movements-validator/messages/IE015/validation"
  }

  "the post departure movements URL on localhost for a given EORI should be as expected" in {
    val eori    = arbitrary[EORINumber].sample.value
    val urlPath = Harness.postMovementUrl(eori, MovementType.Departure)

    urlPath.toString() mustBe s"/transit-movements/traders/${eori.value}/movements/departures"
  }

  "the get movement IDs URL on localhost for a given EORI, departure ID and no filter should be as expected" in {
    val eori        = arbitrary[EORINumber].sample.value
    val departureId = arbitrary[MovementId].sample.value
    val urlPath     = Harness.getMessagesUrl(eori, MovementType.Departure, departureId)

    urlPath.toString() mustBe s"/transit-movements/traders/${eori.value}/movements/departures/${departureId.value}/messages"
  }

  "the get single departure movement URL on localhost for a given EORI, departure ID and message ID should be as expected" in {
    val eori        = arbitrary[EORINumber].sample.value
    val departureId = arbitrary[MovementId].sample.value
    val messageId   = arbitrary[MessageId].sample.value
    val urlPath     = Harness.getMessageUrl(eori, MovementType.Departure, departureId, messageId)

    urlPath.toString() mustBe s"/transit-movements/traders/${eori.value}/movements/departures/${departureId.value}/messages/${messageId.value}"
  }

  "the get single departure URL on localhost for a given EORI or departure ID should be as expected" in {
    val eori        = arbitrary[EORINumber].sample.value
    val departureId = arbitrary[MovementId].sample.value
    val urlPath     = Harness.getMovementUrl(eori, MovementType.Departure, departureId)

    urlPath.toString() mustBe s"/transit-movements/traders/${eori.value}/movements/departures/${departureId.value}"
  }

  "the get all departures on localhost for a given EORI should be as expected" in {
    val eori    = arbitrary[EORINumber].sample.value
    val urlPath = Harness.getAllMovementsUrl(eori, MovementType.Departure)

    urlPath.toString() mustBe s"/transit-movements/traders/${eori.value}/movements/departures"
  }

  "the router message submission URL on localhost for a given EORI, departure ID or message ID should be as expected" in forAll(arbitrary[MessageType]) {
    messageType =>
      val eori        = arbitrary[EORINumber].sample.value
      val departureId = arbitrary[MovementId].sample.value
      val messageId   = arbitrary[MessageId].sample.value
      val urlPath     = Harness.routerRoute(eori, messageType, departureId, messageId)
      urlPath
        .toString() mustBe s"/transit-movements-router/traders/${eori.value}/movements/${messageType.movementType.urlFragment}/${departureId.value}/messages/${messageId.value}"
  }

  "the large message callback URL on localhost for a given EORI, movement ID and message ID should be as expected" in forAll(arbitrary[MovementType]) {
    movementType =>
      val eori       = arbitrary[EORINumber].sample.value
      val movementId = arbitrary[MovementId].sample.value
      val messageId  = arbitrary[MessageId].sample.value
      val urlPath    = Harness.attachLargeMessageRoute(eori, movementType, movementId, messageId)
      urlPath
        .toString() mustBe s"/traders/${eori.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}"
  }

  "the update message submission URL on localhost for a given EORI, movement ID and message ID should be as expected" in forAll(arbitrary[MovementType]) {
    movementType =>
      val eori       = arbitrary[EORINumber].sample.value
      val movementId = arbitrary[MovementId].sample.value
      val messageId  = arbitrary[MessageId].sample.value
      val urlPath    = Harness.updateMessageRoute(eori, movementType, movementId, messageId)
      urlPath
        .toString() mustBe s"/transit-movements/traders/${eori.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}"
  }

  "the auditing URL on localhost for a given audit type should be as expected" in forAll(arbitrary[AuditType]) {
    auditType =>
      val urlPathSmall = Harness.auditingRoute(auditType)
      urlPathSmall.toString() mustBe s"/transit-movements-auditing/audit/${auditType.name}"
  }

  "the conversion URL on localhost for a given message type should be as expected" in forAll(arbitrary[MessageType]) {
    messageType =>
      val urlPath = Harness.conversionRoute(messageType)

      urlPath.toString() mustBe s"/transit-movements-converter/messages/${messageType.code}"
  }

  "the post departure movements URL on localhost for a given departureId should be as expected" in {
    val departureId = arbitrary[MovementId].sample.value
    val urlPath     = Harness.postMessageUrl(departureId)

    urlPath.toString() mustBe s"/transit-movements/traders/movements/${departureId.value}/messages"
  }

  "the post arrival notification URL on localhost for a given EORI should be as expected" in {
    val eori    = arbitrary[EORINumber].sample.value
    val urlPath = Harness.postMovementUrl(eori, MovementType.Arrival)

    urlPath.toString() mustBe s"/transit-movements/traders/${eori.value}/movements/arrivals"
  }

  "the get Arrival movement IDs URL on localhost for a given EORI, arrival ID and no filter should be as expected" in {
    val eori      = arbitrary[EORINumber].sample.value
    val arrivalId = arbitrary[MovementId].sample.value
    val urlPath   = Harness.getMessagesUrl(eori, MovementType.Arrival, arrivalId)

    urlPath.toString() mustBe s"/transit-movements/traders/${eori.value}/movements/arrivals/${arrivalId.value}/messages"
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

    "withInternalAuthToken adds the authorization header" in forAll(Gen.alphaNumStr) {
      token =>
        implicit val appConfig: AppConfig = mock[AppConfig]
        when(appConfig.internalAuthToken).thenReturn(token)

        val sut = mock[RequestBuilder]
        // any here, verify later
        when(sut.setHeader(any())).thenReturn(sut)

        sut.withInternalAuthToken
        verify(sut, times(1)).setHeader(ArgumentMatchers.eq(Seq((HeaderNames.AUTHORIZATION -> token))): _*)
    }

    "withMovementId adds the audit movement Id header for movement value" in forAll(arbitrary[MovementId]) {
      movementId =>
        val sut = mock[RequestBuilder]
        // any here, verify later
        when(sut.setHeader(any())).thenReturn(sut)

        sut.withMovementId(Some(movementId))
        verify(sut, times(1)).setHeader(ArgumentMatchers.eq(Seq(("X-Audit-Meta-Movement-Id" -> movementId.value))): _*)
    }

    "withMovementId ignore the audit movement Id header for None value" in {
      val sut = mock[RequestBuilder]
      // any here, verify later
      when(sut.setHeader(any())).thenReturn(sut)

      sut.withMovementId(None)
      verify(sut, times(0)).setHeader(Seq(any()): _*)
    }

    "withMovementType adds the audit movement type header for movement type value" in forAll(arbitrary[MovementType]) {
      movementType =>
        val sut = mock[RequestBuilder]
        // any here, verify later
        when(sut.setHeader(any())).thenReturn(sut)

        sut.withMovementType(Some(movementType))
        verify(sut, times(1)).setHeader(ArgumentMatchers.eq(Seq(("X-Audit-Meta-Movement-Type" -> movementType.movementType))): _*)
    }

    "withMovementType ignore the audit movement type header for None value" in {
      val sut = mock[RequestBuilder]
      // any here, verify later
      when(sut.setHeader(any())).thenReturn(sut)

      sut.withMovementType(None)
      verify(sut, times(0)).setHeader(Seq(any()): _*)
    }

    "withEoriNumber adds the audit Eori number header for eori number value" in forAll(arbitrary[EORINumber]) {
      eoriNumber =>
        val sut = mock[RequestBuilder]
        // any here, verify later
        when(sut.setHeader(any())).thenReturn(sut)

        sut.withEoriNumber(Some(eoriNumber))
        verify(sut, times(1)).setHeader(ArgumentMatchers.eq(Seq(("X-Audit-Meta-EORI" -> eoriNumber.value))): _*)
    }

    "withEoriNumber ignore the audit Eori number header for None value" in {
      val sut = mock[RequestBuilder]
      // any here, verify later
      when(sut.setHeader(any())).thenReturn(sut)

      sut.withEoriNumber(None)
      verify(sut, times(0)).setHeader(Seq(any()): _*)
    }

    "withMessageId adds the audit message Id header for message Id value" in forAll(arbitrary[MessageId]) {
      messageId =>
        val sut = mock[RequestBuilder]
        // any here, verify later
        when(sut.setHeader(any())).thenReturn(sut)

        sut.withMessageId(Some(messageId))
        verify(sut, times(1)).setHeader(ArgumentMatchers.eq(Seq(("X-Audit-Meta-Message-Id" -> messageId.value))): _*)
    }

    "withMessageId ignore the audit message Id header None value" in {
      val sut = mock[RequestBuilder]
      // any here, verify later
      when(sut.setHeader(any())).thenReturn(sut)

      sut.withMessageId(None)
      verify(sut, times(0)).setHeader(Seq(any()): _*)
    }

    "withMessageType adds the audit message type header for message type value" in forAll(arbitrary[MessageType]) {
      messageType =>
        val sut = mock[RequestBuilder]
        // any here, verify later
        when(sut.setHeader(any())).thenReturn(sut)

        sut.withMessageType(Some(messageType))
        verify(sut, times(1)).setHeader(ArgumentMatchers.eq(Seq(("X-Audit-Meta-Message-Type" -> messageType.code))): _*)
    }

    "withMessageType ignore the audit message type header for None value" in forAll(arbitrary[MessageType]) {
      _ =>
        val sut = mock[RequestBuilder]
        // any here, verify later
        when(sut.setHeader(any())).thenReturn(sut)

        sut.withMessageType(None)
        verify(sut, times(0)).setHeader(Seq(any()): _*)
    }

    "executeAndExpect returns a unit when the expected response is returned" in forAll(
      Gen.oneOf(Seq(ACCEPTED, CREATED, OK, NO_CONTENT))
    ) {
      status =>
        val response = mock[HttpResponse]
        when(response.status).thenReturn(status)
        val sut = mock[RequestBuilder]
        when(sut.execute[HttpResponse](any(), any())).thenReturn(Future.successful(response))

        whenReady(sut.executeAndExpect(status)) {
          unit => unit mustBe a[Unit]
        }

    }

    "executeAndExpect returns an error when the expected response is not returned" in forAll(
      Gen.oneOf(Seq(INTERNAL_SERVER_ERROR, BAD_REQUEST, BAD_GATEWAY, SERVICE_UNAVAILABLE, NOT_FOUND))
    ) {
      status =>
        val response = mock[HttpResponse]
        when(response.status).thenReturn(status)
        when(response.body).thenReturn("error")
        val sut = mock[RequestBuilder]
        when(sut.execute[HttpResponse](any(), any())).thenReturn(Future.successful(response))

        sut
          .executeAndExpect(ACCEPTED)
          .map(
            _ => fail("This should have failed")
          )
          .recover {
            case UpstreamErrorResponse("error", `status`, _, _) => // success
            case x: TestFailedException                         => x
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
