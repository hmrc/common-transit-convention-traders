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

package v2.services

import models.common.EORINumber
import models.common.LocalReferenceNumber
import models.common.MessageId
import models.common.MovementId
import models.common.errors.RouterError
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.CONFLICT
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.base.TestCommonGenerators
import v2.connectors.RouterConnector
import v2.models.SubmissionRoute
import v2.models.request.MessageType

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class RouterServiceSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with TestCommonGenerators
    with ScalaCheckDrivenPropertyChecks
    with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

  val lrnDuplicateErrorResponse: Throwable = UpstreamErrorResponse(
    Json.stringify(
      Json.obj(
        "code"    -> "CONFLICT",
        "message" -> "LRN 1234 was previously used",
        "lrn"     -> "1234"
      )
    ),
    CONFLICT
  )

  val mockConnector: RouterConnector = mock[RouterConnector]

  val sut = new RouterServiceImpl(mockConnector)

  val validRequest: Source[ByteString, NotUsed] = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))

  val unrecognisedOfficeRequest: Source[ByteString, NotUsed] =
    Source.single(
      ByteString(
        <CustomsOfficeOfDeparture>
          <referenceNumber>AB234567</referenceNumber>
        </CustomsOfficeOfDeparture>.mkString,
        StandardCharsets.UTF_8
      )
    )
  val invalidRequest: Source[ByteString, NotUsed] = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))

  override def beforeEach(): Unit =
    reset(mockConnector)

  "Submitting a Departure Declaration" - {

    "on a successful submission, should return a Right" in forAll(
      arbitrary[MessageType],
      arbitrary[EORINumber],
      arbitrary[MovementId],
      arbitrary[MessageId],
      Gen.oneOf(SubmissionRoute.values)
    ) {
      (messageType, eori, movementId, messageId, submissionRoute) =>
        when(
          mockConnector.post(
            eqTo(messageType),
            EORINumber(eqTo(eori.value)),
            MovementId(eqTo(movementId.value)),
            MessageId(eqTo(messageId.value)),
            eqTo(validRequest)
          )(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
        )
          .thenReturn(Future.successful(submissionRoute))

        val result                                         = sut.send(messageType, eori, movementId, messageId, validRequest)
        val expected: Either[RouterError, SubmissionRoute] = Right(submissionRoute)
        whenReady(result.value) {
          _ mustBe expected
        }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {

      when(
        mockConnector.post(
          any[MessageType],
          any[String].asInstanceOf[EORINumber],
          any[String].asInstanceOf[MovementId],
          any[String].asInstanceOf[MessageId],
          eqTo(invalidRequest)
        )(
          any[ExecutionContext],
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                              = sut.send(MessageType.DeclarationData, EORINumber("1"), MovementId("1"), MessageId("1"), invalidRequest)
      val expected: Either[RouterError, Unit] = Left(RouterError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed submission with an invalid office error, should return a Left with an UnrecognisedOffice" in {

      when(
        mockConnector.post(
          any[MessageType],
          any[String].asInstanceOf[EORINumber],
          any[String].asInstanceOf[MovementId],
          any[String].asInstanceOf[MessageId],
          eqTo(unrecognisedOfficeRequest)
        )(
          any[ExecutionContext],
          any[HeaderCarrier]
        )
      )
        .thenReturn(
          Future.failed(
            UpstreamErrorResponse(
              Json.stringify(
                Json.obj(
                  "code"    -> "INVALID_OFFICE",
                  "message" -> "invalid office",
                  "field"   -> "CustomsOfficeOfDeparture",
                  "office"  -> "AB012345"
                )
              ),
              BAD_REQUEST
            )
          )
        )
      val result                              = sut.send(MessageType.DeclarationData, EORINumber("1"), MovementId("1"), MessageId("1"), unrecognisedOfficeRequest)
      val expected: Either[RouterError, Unit] = Left(RouterError.UnrecognisedOffice("AB012345", "CustomsOfficeOfDeparture"))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed submission with Duplicate lrn error, should return a Left with an DuplicateLRN" in {

      when(
        mockConnector.post(
          any[MessageType],
          any[String].asInstanceOf[EORINumber],
          any[String].asInstanceOf[MovementId],
          any[String].asInstanceOf[MessageId],
          eqTo(invalidRequest)
        )(
          any[ExecutionContext],
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.failed(lrnDuplicateErrorResponse))
      val result                              = sut.send(MessageType.DeclarationData, EORINumber("1"), MovementId("1"), MessageId("1"), invalidRequest)
      val expected: Either[RouterError, Unit] = Left(RouterError.DuplicateLRN(LocalReferenceNumber("1234")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

}
