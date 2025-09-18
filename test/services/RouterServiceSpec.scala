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

package services

import base.TestCommonGenerators
import connectors.RouterConnector
import models.Version
import models.Version.V2_1
import models.Version.V3_0
import models.SubmissionRoute
import models.common.errors.RouterError
import models.common.EORINumber
import models.common.LocalReferenceNumber
import models.common.MessageId
import models.common.MovementId
import models.request.MessageType
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.CONFLICT
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse

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

  val version: Version = Gen.oneOf(V2_1, V3_0).sample.value

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

  val sut = new RouterService(mockConnector)

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
      Gen.oneOf(SubmissionRoute.values.toIndexedSeq)
    ) {
      (messageType, eori, movementId, messageId, submissionRoute) =>
        when(
          mockConnector.post(
            eqTo(messageType),
            EORINumber(eqTo(eori.value)),
            MovementId(eqTo(movementId.value)),
            MessageId(eqTo(messageId.value)),
            eqTo(validRequest),
            eqTo(version)
          )(
            any[ExecutionContext],
            any[HeaderCarrier]
          )
        )
          .thenReturn(Future.successful(submissionRoute))

        val result                                         = sut.send(messageType, eori, movementId, messageId, validRequest, version)
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
          eqTo(invalidRequest),
          eqTo(version)
        )(
          any[ExecutionContext],
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                              = sut.send(MessageType.DeclarationData, EORINumber("1"), MovementId("1"), MessageId("1"), invalidRequest, version)
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
          eqTo(unrecognisedOfficeRequest),
          eqTo(version)
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
      val result = sut.send(MessageType.DeclarationData, EORINumber("1"), MovementId("1"), MessageId("1"), unrecognisedOfficeRequest, version)
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
          eqTo(invalidRequest),
          eqTo(version)
        )(
          any[ExecutionContext],
          any[HeaderCarrier]
        )
      )
        .thenReturn(Future.failed(lrnDuplicateErrorResponse))
      val result                              = sut.send(MessageType.DeclarationData, EORINumber("1"), MovementId("1"), MessageId("1"), invalidRequest, version)
      val expected: Either[RouterError, Unit] = Left(RouterError.DuplicateLRN(LocalReferenceNumber("1234")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }
}
