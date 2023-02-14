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
import v2.models.MovementType
import v2.models.XmlPayload
import v2.models.errors.PersistenceError
import v2.models.request.MessageType
import v2.models.responses.MessageSummary
import v2.models.responses.MovementResponse
import v2.models.responses.MovementSummary
import v2.models.responses.UpdateMovementResponse

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class MovementsServiceSpec
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
      when(mockConnector.postMovement(EORINumber(any[String]), any(), eqTo(Some(validRequest)))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(MovementResponse(MovementId("ABC"), MessageId("123"))))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Departure, Some(validRequest))
      val expected: Either[PersistenceError, MovementResponse] = Right(MovementResponse(MovementId("ABC"), MessageId("123")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      when(mockConnector.postMovement(EORINumber(any[String]), any(), eqTo(Some(invalidRequest)))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Departure, Some(invalidRequest))
      val expected: Either[PersistenceError, MovementResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

  }

  "Submitting a Departure Declaration for Large Messages" - {

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful submission, should return a Right" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      when(mockConnector.postMovement(EORINumber(any[String]), any(), any())(eqTo(hc), any[ExecutionContext]))
        .thenReturn(Future.successful(MovementResponse(MovementId("ABC"), MessageId("123"))))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Departure, None)
      val expected: Either[PersistenceError, MovementResponse] = Right(MovementResponse(MovementId("ABC"), MessageId("123")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      when(mockConnector.postMovement(EORINumber(any[String]), any(), any())(eqTo(hc), any[ExecutionContext]))
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Departure, None)
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
        when(mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any())(any(), any()))
          .thenReturn(Future.successful(expected))

        val result = sut.getMessages(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"), dateTime.sample.get)
        whenReady(result.value) {
          _ mustBe Right(expected)
        }
    }

    "when a message is not found, should return a Left with an MessageNotFound" in {
      when(mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any())(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMessages(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"), dateTime.sample.get)
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MovementNotFound(MovementId("1234567890abcdef"), MovementType.Departure))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any())(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getMessages(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"), dateTime.sample.get)
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

      when(mockConnector.getMessage(EORINumber(any()), any(), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.successful(successResponse))

      val result = sut.getMessage(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Right(successResponse)
      }
    }

    "when a message is not found, should return a Left with an MessageNotFound" in {
      when(mockConnector.getMessage(EORINumber(any()), any(), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMessage(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MessageNotFound(MovementId("1234567890abcdef"), MessageId("1234567890abcdef")))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getMessage(EORINumber(any()), any(), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getMessage(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
      }
    }

  }

  "Getting a Single Departure (Movement)" - {

    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "when a departure (movement) is found, should return a Right" in {
      val successResponse = MovementSummary(
        _id = MovementId("1234567890abcdef"),
        enrollmentEORINumber = EORINumber("GB123"),
        movementEORINumber = EORINumber("GB456"),
        movementReferenceNumber = Some(MovementReferenceNumber("MRN001")),
        created = now,
        updated = now
      )

      when(mockConnector.getMovement(EORINumber(any()), any(), MovementId(any()))(any(), any()))
        .thenReturn(Future.successful(successResponse))

      val result = sut.getMovement(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Right(successResponse)
      }
    }

    "when a departure is not found, should return DepartureNotFound" in {
      when(mockConnector.getMovement(EORINumber(any()), any(), MovementId(any()))(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMovement(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MovementNotFound(MovementId("1234567890abcdef"), MovementType.Departure))
      }
    }

    "on any other error, should return an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getMovement(EORINumber(any()), any(), MovementId(any()))(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getMovement(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"))
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
      val result                                                     = sut.updateMovement(MovementId("abc"), MovementType.Departure, MessageType.DeclarationInvalidationRequest, validRequest)
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

      val result = sut.updateMovement(MovementId("1234567890abcdef"), MovementType.Departure, MessageType.DeclarationInvalidationRequest, validRequest)
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MovementNotFound(MovementId("1234567890abcdef"), MovementType.Departure))
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
      val result                                                     = sut.updateMovement(MovementId("abc"), MovementType.Departure, MessageType.DeclarationInvalidationRequest, invalidRequest)
      val expected: Either[PersistenceError, UpdateMovementResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

  "Getting a list of Departures (Movement) by EORI" - {

    "when a departure (movement) is found, should return a Right" in forAll(
      Gen.listOfN(3, arbitrary[MovementSummary]),
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      arbitrary[EORINumber]
    ) {

      (expected, updatedSinceMaybe, movementEORI, eori) =>
        when(mockConnector.getMovements(eori, MovementType.Departure, updatedSinceMaybe, movementEORI))
          .thenReturn(Future.successful(expected))

        val result = sut.getMovements(eori, MovementType.Departure, updatedSinceMaybe, movementEORI)
        whenReady(result.value) {
          _ mustBe Right(expected)
        }
    }

    "when a departure is not found, should return a Left with an MovementsNotFound" in forAll(
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      arbitrary[EORINumber]
    ) {
      (updatedSinceMaybe, movementEORI, eori) =>
        when(mockConnector.getMovements(eori, MovementType.Departure, updatedSinceMaybe, movementEORI))
          .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

        val result = sut.getMovements(eori, MovementType.Departure, updatedSinceMaybe, movementEORI)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.MovementsNotFound(eori, MovementType.Departure))
        }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in forAll(
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      arbitrary[EORINumber]
    ) {
      (updatedSinceMaybe, movementEORI, eori) =>
        val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
        when(mockConnector.getMovements(eori, MovementType.Departure, updatedSinceMaybe, movementEORI))
          .thenReturn(Future.failed(error))

        val result = sut.getMovements(eori, MovementType.Departure, updatedSinceMaybe, movementEORI)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
        }
    }
  }

  "Create Arrival notification" - {

    val validRequest: Source[ByteString, NotUsed]   = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
    val invalidRequest: Source[ByteString, NotUsed] = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful creation, should return a Right" in {
      when(mockConnector.postMovement(EORINumber(any[String]), any(), eqTo(Some(validRequest)))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(MovementResponse(MovementId("ABC"), MessageId("123"))))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Arrival, Some(validRequest))
      val expected: Either[PersistenceError, MovementResponse] = Right(MovementResponse(MovementId("ABC"), MessageId("123")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed creation, should return a Left with an UnexpectedError" in {
      when(mockConnector.postMovement(EORINumber(any[String]), any(), eqTo(Some(invalidRequest)))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Arrival, Some(invalidRequest))
      val expected: Either[PersistenceError, MovementResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

  "Create Arrival notification for Large Messages" - {

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful creation, should return a Right" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      when(mockConnector.postMovement(EORINumber(any[String]), any(), any())(eqTo(hc), any[ExecutionContext]))
        .thenReturn(Future.successful(MovementResponse(MovementId("ABC"), MessageId("123"))))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Arrival, None)
      val expected: Either[PersistenceError, MovementResponse] = Right(MovementResponse(MovementId("ABC"), MessageId("123")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed creation, should return a Left with an UnexpectedError" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      when(mockConnector.postMovement(EORINumber(any[String]), any(), any())(eqTo(hc), any[ExecutionContext]))
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Arrival, None)
      val expected: Either[PersistenceError, MovementResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

  "Getting a list of Arrival message IDs" - {

    "when an arrival is found, should return a Right of the sequence of message IDs" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.listOfN(3, arbitrary[MessageSummary])
    ) {
      (eori, arrivalId, receivedSince, expected) =>
        when(mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any())(any(), any()))
          .thenReturn(Future.successful(expected))

        val result = sut.getMessages(eori, MovementType.Arrival, arrivalId, receivedSince)
        whenReady(result.value) {
          _ mustBe Right(expected)
        }
    }

    "when an arrival is not found, should return a Left with ArrivalNotFound" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      Gen.option(arbitrary[OffsetDateTime])
    ) {
      (eori, arrivalId, receivedSince) =>
        when(mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any())(any(), any()))
          .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

        val result = sut.getMessages(eori, MovementType.Arrival, arrivalId, receivedSince)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.MovementNotFound(arrivalId, MovementType.Arrival))
        }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      Gen.option(arbitrary[OffsetDateTime])
    ) {
      (eori, arrivalId, receivedSince) =>
        val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
        when(mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any())(any(), any()))
          .thenReturn(Future.failed(error))

        val result = sut.getMessages(eori, MovementType.Arrival, arrivalId, receivedSince)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
        }
    }

  }

  "Getting a list of Arrivals (Movement) by EORI" - {

    "when an arrival (movement) is found, should return a Right" in forAll(
      Gen.listOfN(3, arbitrary[MovementSummary]),
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      arbitrary[EORINumber]
    ) {

      (expected, updatedSinceMaybe, movementEORI, eori) =>
        when(mockConnector.getMovements(eori, MovementType.Arrival, updatedSinceMaybe, movementEORI))
          .thenReturn(Future.successful(expected))

        val result = sut.getMovements(eori, MovementType.Arrival, updatedSinceMaybe, movementEORI)
        whenReady(result.value) {
          _ mustBe Right(expected)
        }
    }

    "when an arrival is not found, should return a Left with an MovementsNotFound" in forAll(
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      arbitrary[EORINumber]
    ) {
      (updatedSinceMaybe, movementEORI, eori) =>
        when(mockConnector.getMovements(eori, MovementType.Arrival, updatedSinceMaybe, movementEORI))
          .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

        val result = sut.getMovements(eori, MovementType.Arrival, updatedSinceMaybe, movementEORI)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.MovementsNotFound(eori, MovementType.Arrival))
        }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in forAll(
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      arbitrary[EORINumber]
    ) {
      (updatedSinceMaybe, movementEORI, eori) =>
        val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
        when(mockConnector.getMovements(eori, MovementType.Arrival, updatedSinceMaybe, movementEORI))
          .thenReturn(Future.failed(error))

        val result = sut.getMovements(eori, MovementType.Arrival, updatedSinceMaybe, movementEORI)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
        }
    }

  }

  "Getting a Single Arrival Message" - {

    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "when a message is found, should return a Right" in {
      val successResponse = MessageSummary(
        MessageId("1234567890abcdef"),
        now,
        MessageType.ArrivalNotification,
        Some(XmlPayload("<test></test>"))
      )

      when(mockConnector.getMessage(EORINumber(any()), any(), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.successful(successResponse))

      val result = sut.getMessage(EORINumber("1"), MovementType.Arrival, MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Right(successResponse)
      }
    }

    "when a message is not found, should return a Left with an MessageNotFound" in {
      when(mockConnector.getMessage(EORINumber(any()), any(), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMessage(EORINumber("1"), MovementType.Arrival, MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MessageNotFound(MovementId("1234567890abcdef"), MessageId("1234567890abcdef")))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getMessage(EORINumber(any()), any(), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getMessage(EORINumber("1"), MovementType.Arrival, MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
      }
    }

  }

  "Updating arrival with arrivalId and messageType" - {

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
        .thenReturn(Future.successful(UpdateMovementResponse(MessageId("1234567890abcdsd"))))
      val result                                                     = sut.updateMovement(MovementId("1234567890abcdef"), MovementType.Arrival, MessageType.UnloadingRemarks, validRequest)
      val expected: Either[PersistenceError, UpdateMovementResponse] = Right(UpdateMovementResponse(MessageId("1234567890abcdsd")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on an arrival is not found, should return ArrivalNotFound" in {
      when(
        mockConnector.postMessage(MovementId(any[String]), any[MessageType], eqTo(validRequest))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.updateMovement(MovementId("1234567890abcdef"), MovementType.Arrival, MessageType.UnloadingRemarks, validRequest)
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MovementNotFound(MovementId("1234567890abcdef"), MovementType.Arrival))
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
      val result                                                     = sut.updateMovement(MovementId("1234567890abcdef"), MovementType.Arrival, MessageType.UnloadingRemarks, invalidRequest)
      val expected: Either[PersistenceError, UpdateMovementResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }
}
