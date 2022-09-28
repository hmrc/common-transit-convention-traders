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
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.INTERNAL_SERVER_ERROR
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.base.TestActorSystem
import v2.connectors.ConversionConnector
import v2.models.HeaderType
import v2.models.HeaderTypes.jsonToXml
import v2.models.errors.ConversionError
import v2.models.request.MessageType

import java.nio.charset.StandardCharsets
import scala.concurrent._

class ConversionServiceSpec extends AnyFreeSpec with Matchers with MockitoSugar with ScalaFutures with TestActorSystem {

  "On converting a message" - {
    "a successful conversion, should return a Right" in new Setup {
      val result = sut.convert(MessageType.DeclarationData, jsonPayload, jsonToXml)
      whenReady(result.value) {
        _.right.get
          .reduce(_ ++ _)
          .map(_.utf8String)
          .runWith(Sink.last)
          .map(
            _ mustBe "a response from the converter"
          )
      }
      verify(mockConnector, times(1)).post(any(), any(), any())(any(), any(), any())
    }

    "a failed conversion, should return a Left" in new Setup {
      when(mockConnector.post(any[MessageType], any[Source[ByteString, _]](), any[HeaderType])(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result = sut.convert(MessageType.DeclarationData, jsonPayload, jsonToXml)
      whenReady(result.value) {
        _.left.get mustBe ConversionError.UnexpectedError(Some(upstreamErrorResponse))
      }
      verify(mockConnector, times(1)).post(any(), any(), any())(any(), any(), any())
    }
  }

  trait Setup {
    implicit val ec: ExecutionContext = materializer.executionContext
    implicit val hc: HeaderCarrier    = HeaderCarrier()

    lazy val upstreamErrorResponse: Throwable          = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)
    lazy val mockResponse: Source[ByteString, NotUsed] = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
    lazy val jsonPayload: Source[ByteString, NotUsed]  = Source.single(ByteString("{}", StandardCharsets.UTF_8))

    lazy val mockConnector = mock[ConversionConnector]
    when(mockConnector.post(any[MessageType], any[Source[ByteString, _]](), any())(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
      .thenReturn(Future(mockResponse))

    lazy val sut = new ConversionServiceImpl(mockConnector)
  }

}
