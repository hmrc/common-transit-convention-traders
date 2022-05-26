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
import cats.data.EitherT
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
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

class DeparturesPersistenceServiceSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "Submitting a Departure Declaration" - {

    val ValidRequest: Source[ByteString, NotUsed]   = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
    val InvalidRequest: Source[ByteString, NotUsed] = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    val mockConnector: PersistenceConnector = mock[PersistenceConnector]
    when(mockConnector.sendDepartureDeclaration(anyString().asInstanceOf[EORINumber], eqTo(ValidRequest))(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.successful(DeclarationResponse(MovementId("ABC"), MessageId("123"))))
    when(mockConnector.sendDepartureDeclaration(anyString().asInstanceOf[EORINumber], eqTo(InvalidRequest))(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(Future.failed(upstreamErrorResponse))

    val sut = new DeparturesPersistenceServiceImpl(mockConnector)

    "on a successful submission, should return a Right" in {
      val result = sut.saveDeclaration(EORINumber("1"), ValidRequest)
      result mustBe EitherT(Future.successful(Right(DeclarationResponse(MovementId("ABC"), MessageId("123")))))
    }

    "on a failed submission, should return a Left with an OtherError" in {
      val result = sut.saveDeclaration(EORINumber("1"), InvalidRequest)
      result mustBe EitherT(Future.successful(Left(PersistenceError.OtherError(Some(upstreamErrorResponse)))))
    }
  }

}
