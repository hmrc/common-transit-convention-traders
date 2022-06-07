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
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.connectors.PersistenceConnector
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.errors.PersistenceError
import v2.models.responses.DeclarationResponse

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class DeparturesServiceSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "Submitting a Departure Declaration" - {

    val validRequest: Source[ByteString, NotUsed]   = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
    val invalidRequest: Source[ByteString, NotUsed] = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    val mockConnector: PersistenceConnector = mock[PersistenceConnector]

    // Because we're using AnyVal, Mockito doesn't really like it, so we have to put the underlying type down, then cast to the value type...
    when(mockConnector.post(any[String].asInstanceOf[EORINumber], eqTo(validRequest))(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(DeclarationResponse(MovementId("ABC"), MessageId("123"))))
    when(mockConnector.post(any[String].asInstanceOf[EORINumber], eqTo(invalidRequest))(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.failed(upstreamErrorResponse))

    val sut = new DeparturesServiceImpl(mockConnector)

    "on a successful submission, should return a Right" in {
      val result                                                  = sut.saveDeclaration(EORINumber("1"), validRequest)
      val expected: Either[PersistenceError, DeclarationResponse] = Right(DeclarationResponse(MovementId("ABC"), MessageId("123")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      val result                                                  = sut.saveDeclaration(EORINumber("1"), invalidRequest)
      val expected: Either[PersistenceError, DeclarationResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

}
