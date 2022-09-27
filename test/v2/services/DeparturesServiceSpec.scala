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
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.base.CommonGenerators
import v2.connectors.PersistenceConnector
import v2.models.DepartureId
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementReferenceNumber
import v2.models.errors.PersistenceError
import v2.models.request.MessageType
import v2.models.responses.DeclarationResponse
import v2.models.responses.DepartureResponse
import v2.models.responses.MessageResponse
import v2.models.responses.MessageSummary

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class DeparturesServiceSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with CommonGenerators
    with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockConnector: PersistenceConnector = mock[PersistenceConnector]
  val sut                                 = new DeparturesServiceImpl(mockConnector)

  override def beforeEach(): Unit =
    reset(mockConnector)

  "Submitting a Departure Declaration" - {

    val validRequest: Source[ByteString, NotUsed]   = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
    val invalidRequest: Source[ByteString, NotUsed] = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful submission, should return a Right" in {
      when(mockConnector.post(EORINumber(any[String]), eqTo(validRequest))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(DeclarationResponse(DepartureId("ABC"), MessageId("123"))))
      val result                                                  = sut.saveDeclaration(EORINumber("1"), validRequest)
      val expected: Either[PersistenceError, DeclarationResponse] = Right(DeclarationResponse(DepartureId("ABC"), MessageId("123")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      when(mockConnector.post(EORINumber(any[String]), eqTo(invalidRequest))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                                                  = sut.saveDeclaration(EORINumber("1"), invalidRequest)
      val expected: Either[PersistenceError, DeclarationResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

  "Getting a list of Departure message IDs" - {

    val dateTime = Gen.option(arbitrary[OffsetDateTime])

    "when a departure is found, should return a Right of the sequence of message IDs" in {
      val expected = (for {
        messageId1 <- arbitrary[MessageId]
        messageId2 <- arbitrary[MessageId]
        messageId3 <- arbitrary[MessageId]
      } yield Seq(
        genMessageSummary(Some(messageId1)).arbitrary.sample.value,
        genMessageSummary(Some(messageId2)).arbitrary.sample.value,
        genMessageSummary(Some(messageId3)).arbitrary.sample.value
      )).sample.value

      when(mockConnector.getDepartureMessageIds(EORINumber(any()), DepartureId(any()), any())(any(), any()))
        .thenReturn(Future.successful(expected))

      val result = sut.getMessageIds(EORINumber("1"), DepartureId("1234567890abcdef"), dateTime.sample.get)
      whenReady(result.value) {
        _ mustBe Right(expected)
      }
    }

    "when a message is not found, should return a Left with an MessageNotFound" in {
      when(mockConnector.getDepartureMessageIds(EORINumber(any()), DepartureId(any()), any())(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMessageIds(EORINumber("1"), DepartureId("1234567890abcdef"), dateTime.sample.get)
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.DepartureNotFound(DepartureId("1234567890abcdef")))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getDepartureMessageIds(EORINumber(any()), DepartureId(any()), any())(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getMessageIds(EORINumber("1"), DepartureId("1234567890abcdef"), dateTime.sample.get)
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
        MessageType.DepartureDeclaration,
        Some("<test></test>")
      )

      when(mockConnector.getDepartureMessage(EORINumber(any()), DepartureId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.successful(successResponse))

      val result = sut.getMessage(EORINumber("1"), DepartureId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Right(successResponse)
      }
    }

    "when a message is not found, should return a Left with an MessageNotFound" in {
      when(mockConnector.getDepartureMessage(EORINumber(any()), DepartureId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMessage(EORINumber("1"), DepartureId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MessageNotFound(DepartureId("1234567890abcdef"), MessageId("1234567890abcdef")))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getDepartureMessage(EORINumber(any()), DepartureId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getMessage(EORINumber("1"), DepartureId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
      }
    }

  }

  "Getting a Single Departure (Movement)" - {

    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "when a departure (movement) is found, should return a Right" in {
      val successResponse = DepartureResponse(
        _id = DepartureId("1234567890abcdef"),
        enrollmentEORINumber = EORINumber("GB123"),
        movementEORINumber = EORINumber("GB456"),
        movementReferenceNumber = Some(MovementReferenceNumber("MRN001")),
        created = now,
        updated = now
      )

      when(mockConnector.getDeparture(EORINumber(any()), DepartureId(any()))(any(), any()))
        .thenReturn(Future.successful(successResponse))

      val result = sut.getDeparture(EORINumber("1"), DepartureId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Right(successResponse)
      }
    }

    "when a departure is not found, should return DepartureNotFound" in {
      when(mockConnector.getDeparture(EORINumber(any()), DepartureId(any()))(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getDeparture(EORINumber("1"), DepartureId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.DepartureNotFound(DepartureId("1234567890abcdef")))
      }
    }

    "on any other error, should return an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getDeparture(EORINumber(any()), DepartureId(any()))(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getDeparture(EORINumber("1"), DepartureId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
      }
    }

  }
}
