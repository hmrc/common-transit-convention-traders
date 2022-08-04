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
import v2.connectors.PersistenceConnector
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.errors.PersistenceError
import v2.models.request.MessageType
import v2.models.responses.DeclarationResponse
import v2.models.responses.MessageResponse

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class DeparturesServiceSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach {

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
        .thenReturn(Future.successful(DeclarationResponse(MovementId("ABC"), MessageId("123"))))
      val result                                                  = sut.saveDeclaration(EORINumber("1"), validRequest)
      val expected: Either[PersistenceError, DeclarationResponse] = Right(DeclarationResponse(MovementId("ABC"), MessageId("123")))
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

  "Getting a Single Message" - {

    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "when a message is found, should return a Right" in {
      val successResponse = MessageResponse(
        MessageId("1234567890abcdef"),
        now,
        now,
        MessageType.DepartureDeclaration,
        None,
        None,
        Some("<test></test>")
      )

      when(mockConnector.getDepartureMessage(EORINumber(any()), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.successful(successResponse))

      val result = sut.getMessage(EORINumber("1"), MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Right(successResponse)
      }
    }

    "when a message is not found, should return a Left with an MessageNotFound" in {
      when(mockConnector.getDepartureMessage(EORINumber(any()), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMessage(EORINumber("1"), MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MessageNotFound(MovementId("1234567890abcdef"), MessageId("1234567890abcdef")))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getDepartureMessage(EORINumber(any()), MovementId(any()), MessageId(any()))(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getMessage(EORINumber("1"), MovementId("1234567890abcdef"), MessageId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
      }
    }

  }

}
