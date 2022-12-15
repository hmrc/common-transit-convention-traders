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

package v2.services

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
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
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.base.CommonGenerators
import v2.connectors.PersistenceConnector
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementReferenceNumber
import v2.models.XmlPayload
import v2.models.errors.PersistenceError
import v2.models.request.MessageType
import v2.models.responses.MovementResponse
import v2.models.responses.MovementResponse
import v2.models.responses.MessageSummary
import v2.models.responses.UpdateMovementResponse

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class DeparturesServiceSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with CommonGenerators
    with ScalaCheckDrivenPropertyChecks
    with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockConnector: PersistenceConnector = mock[PersistenceConnector]
  val sut                                 = new MovementsServiceImpl(mockConnector)

  override def beforeEach(): Unit =
    reset(mockConnector)

  "Submitting a Departure Declaration" - {

    val validRequest: Source[ByteString, NotUsed]   = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
    val invalidRequest: Source[ByteString, NotUsed] = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful submission, should return a Right" in {
      when(mockConnector.postMovement(EORINumber(any[String]), eqTo(validRequest))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(MovementResponse(MovementId("ABC"), MessageId("123"))))
      val result                                               = sut.createMovement(EORINumber("1"), validRequest)
      val expected: Either[PersistenceError, MovementResponse] = Right(MovementResponse(MovementId("ABC"), MessageId("123")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      when(mockConnector.postMovement(EORINumber(any[String]), eqTo(invalidRequest))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                                               = sut.createMovement(EORINumber("1"), invalidRequest)
      val expected: Either[PersistenceError, MovementResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

  "Getting a list of Departure message IDs" - {

    val dateTime = Gen.option(arbitrary[OffsetDateTime])

    "when a departure is found, should return a Right of the sequence of message IDs" in forAll(Gen.listOfN(3, arbitrary[MessageSummary])) {
      expected =>
        when(mockConnector.getMessages(EORINumber(any()), MovementId(any()), any())(any(), any()))
          .thenReturn(Future.successful(expected))

        val result = sut.getMessages(EORINumber("1"), MovementId("1234567890abcdef"), dateTime.sample.get)
        whenReady(result.value) {
          _ mustBe Right(expected)
        }
    }

    "when a message is not found, should return a Left with an MessageNotFound" in {
      when(mockConnector.getMessages(EORINumber(any()), MovementId(any()), any())(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMessages(EORINumber("1"), MovementId("1234567890abcdef"), dateTime.sample.get)
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MovementNotFound(MovementId("1234567890abcdef")))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getMessages(EORINumber(any()), MovementId(any()), any())(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getMessages(EORINumber("1"), MovementId("1234567890abcdef"), dateTime.sample.get)
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
      }
    }

  }

  "Getting a Single Message" - {

    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "when a message is found, should return a Right" in {
      val successResponse = MessageSummary(
        MessageId("1234567890abcdef"),
        now,
        MessageType.DeclarationData,
        Some(XmlPayload("<test></test>"))
      )

      when(mockConnector.getMessages(EORINumber(any()), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.successful(successResponse))

      val result = sut.getMessage(EORINumber("1"), MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Right(successResponse)
      }
    }

    "when a message is not found, should return a Left with an MessageNotFound" in {
      when(mockConnector.getMessages(EORINumber(any()), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMessage(EORINumber("1"), MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MessageNotFound(MovementId("1234567890abcdef"), MessageId("1234567890abcdef")))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getMessages(EORINumber(any()), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getMessage(EORINumber("1"), MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
      }
    }

  }

  "Getting a Single Departure (Movement)" - {

    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "when a departure (movement) is found, should return a Right" in {
      val successResponse = MovementResponse(
        _id = MovementId("1234567890abcdef"),
        enrollmentEORINumber = EORINumber("GB123"),
        movementEORINumber = EORINumber("GB456"),
        movementReferenceNumber = Some(MovementReferenceNumber("MRN001")),
        created = now,
        updated = now
      )

      when(mockConnector.getMovement(EORINumber(any()), MovementId(any()))(any(), any()))
        .thenReturn(Future.successful(successResponse))

      val result = sut.getMovement(EORINumber("1"), MovementId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Right(successResponse)
      }
    }

    "when a departure is not found, should return DepartureNotFound" in {
      when(mockConnector.getMovement(EORINumber(any()), MovementId(any()))(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMovement(EORINumber("1"), MovementId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MovementNotFound(MovementId("1234567890abcdef")))
      }
    }

    "on any other error, should return an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getMovement(EORINumber(any()), MovementId(any()))(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getMovement(EORINumber("1"), MovementId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
      }
    }

  }

  "Updating departure with departureId and messageType" - {

    val validRequest: Source[ByteString, NotUsed]   = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
    val invalidRequest: Source[ByteString, NotUsed] = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful submission, should return a Right" in {
      when(
        mockConnector.postMessage(MovementId(any[String]), any[MessageType], eqTo(validRequest))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      )
        .thenReturn(Future.successful(UpdateMovementResponse(MessageId("123"))))
      val result                                                     = sut.updateMovement(MovementId("abc"), MessageType.DeclarationInvalidationRequest, validRequest)
      val expected: Either[PersistenceError, UpdateMovementResponse] = Right(UpdateMovementResponse(MessageId("123")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a departure is not found, should return DepartureNotFound" in {
      when(
        mockConnector.postMessage(MovementId(any[String]), any[MessageType], eqTo(validRequest))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.updateMovement(MovementId("1234567890abcdef"), MessageType.DeclarationInvalidationRequest, validRequest)
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MovementNotFound(MovementId("1234567890abcdef")))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      when(
        mockConnector.postMessage(MovementId(any[String]), any[MessageType], eqTo(invalidRequest))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      )
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                                                     = sut.updateMovement(MovementId("abc"), MessageType.DeclarationInvalidationRequest, invalidRequest)
      val expected: Either[PersistenceError, UpdateMovementResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

  "Getting a list of Departures (Movement) by EORI" - {

    "when a departure (movement) is found, should return a Right" in forAll(
      Gen.listOfN(3, arbitrary[MovementResponse]),
      Gen.option(arbitrary[OffsetDateTime]),
      arbitrary[EORINumber]
    ) {

      (expected, updatedSinceMaybe, eori) =>
        when(mockConnector.getMovements(eori, updatedSinceMaybe))
          .thenReturn(Future.successful(expected))

        val result = sut.getMovements(eori, updatedSinceMaybe)
        whenReady(result.value) {
          _ mustBe Right(expected)
        }
    }

    "when a departure is not found, should return a Left with an DeparturesNotFound" in forAll(
      Gen.option(arbitrary[OffsetDateTime]),
      arbitrary[EORINumber]
    ) {
      (updatedSinceMaybe, eori) =>
        when(mockConnector.getMovements(eori, updatedSinceMaybe))
          .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

        val result = sut.getMovements(eori, updatedSinceMaybe)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.DeparturesNotFound(eori))
        }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in forAll(
      Gen.option(arbitrary[OffsetDateTime]),
      arbitrary[EORINumber]
    ) {
      (updatedSinceMaybe, eori) =>
        val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
        when(mockConnector.getMovements(eori, updatedSinceMaybe))
          .thenReturn(Future.failed(error))

        val result = sut.getMovements(eori, updatedSinceMaybe)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
        }
    }

  }

}
