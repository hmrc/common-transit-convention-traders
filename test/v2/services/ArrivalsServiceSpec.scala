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
import v2.models.XmlPayload
import v2.models.errors.PersistenceError
import v2.models.request.MessageType
import v2.models.responses.ArrivalResponse
import v2.models.responses.MovementResponse

import java.nio.charset.StandardCharsets
import v2.models.responses.MessageSummary

import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ArrivalsServiceSpec
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
  val sut                                 = new ArrivalsServiceImpl(mockConnector)

  override def beforeEach(): Unit =
    reset(mockConnector)

  "Create Arrival notification" - {

    val validRequest: Source[ByteString, NotUsed]   = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
    val invalidRequest: Source[ByteString, NotUsed] = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful creation, should return a Right" in {
      when(mockConnector.postArrival(EORINumber(any[String]), eqTo(validRequest))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(ArrivalResponse(MovementId("ABC"), MessageId("123"))))
      val result                                              = sut.createArrival(EORINumber("1"), validRequest)
      val expected: Either[PersistenceError, ArrivalResponse] = Right(ArrivalResponse(MovementId("ABC"), MessageId("123")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed creation, should return a Left with an UnexpectedError" in {
      when(mockConnector.postArrival(EORINumber(any[String]), eqTo(invalidRequest))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                                              = sut.createArrival(EORINumber("1"), invalidRequest)
      val expected: Either[PersistenceError, ArrivalResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
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
        when(mockConnector.getArrivalMessageIds(EORINumber(any()), MovementId(any()), any())(any(), any()))
          .thenReturn(Future.successful(expected))

        val result = sut.getArrivalMessageIds(eori, arrivalId, receivedSince)
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
        when(mockConnector.getArrivalMessageIds(EORINumber(any()), MovementId(any()), any())(any(), any()))
          .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

        val result = sut.getArrivalMessageIds(eori, arrivalId, receivedSince)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.ArrivalNotFound(arrivalId))
        }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      Gen.option(arbitrary[OffsetDateTime])
    ) {
      (eori, arrivalId, receivedSince) =>
        val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
        when(mockConnector.getArrivalMessageIds(EORINumber(any()), MovementId(any()), any())(any(), any()))
          .thenReturn(Future.failed(error))

        val result = sut.getArrivalMessageIds(eori, arrivalId, receivedSince)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
        }
    }

  }

  "Getting a list of Arrivals (Movement) by EORI" - {

    "when an arrival (movement) is found, should return a Right" in forAll(Gen.listOfN(3, arbitrary[MovementResponse]), Gen.option(arbitrary[OffsetDateTime])) {

      (expected, updatedSinceMaybe) =>
        when(mockConnector.getArrivalsForEori(EORINumber("1"), updatedSinceMaybe))
          .thenReturn(Future.successful(expected))

        val result = sut.getArrivalsForEori(EORINumber("1"), updatedSinceMaybe)
        whenReady(result.value) {
          _ mustBe Right(expected)
        }
    }

    "when an arrival is not found, should return a Left with an ArrivalsNotFound" in forAll(Gen.option(arbitrary[OffsetDateTime])) {
      updatedSinceMaybe =>
        when(mockConnector.getArrivalsForEori(EORINumber("1"), updatedSinceMaybe))
          .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

        val result = sut.getArrivalsForEori(EORINumber("1"), updatedSinceMaybe)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.ArrivalsNotFound(EORINumber("1")))
        }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in forAll(Gen.option(arbitrary[OffsetDateTime])) {
      updatedSinceMaybe =>
        val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
        when(mockConnector.getArrivalsForEori(EORINumber("1"), updatedSinceMaybe))
          .thenReturn(Future.failed(error))

        val result = sut.getArrivalsForEori(EORINumber("1"), updatedSinceMaybe)
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

      when(mockConnector.getArrivalMessage(EORINumber(any()), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.successful(successResponse))

      val result = sut.getArrivalMessage(EORINumber("1"), MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Right(successResponse)
      }
    }

    "when a message is not found, should return a Left with an MessageNotFound" in {
      when(mockConnector.getArrivalMessage(EORINumber(any()), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getArrivalMessage(EORINumber("1"), MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MessageNotFound(MovementId("1234567890abcdef"), MessageId("1234567890abcdef")))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getArrivalMessage(EORINumber(any()), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getArrivalMessage(EORINumber("1"), MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
      }
    }

  }
}
