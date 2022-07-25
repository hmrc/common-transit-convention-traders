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
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.JsError
import play.api.libs.json.JsResult
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.connectors.ConversionConnector
import v2.models.errors.ConversionError
import v2.models.request.MessageType

import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.charset.StandardCharsets
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ConversionServiceSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val SchemaValidJson: Source[ByteString, NotUsed]      = Source.single(ByteString("{'CC015': ''", StandardCharsets.UTF_8))
  val SchemaInvalidJson: Source[ByteString, NotUsed]    = Source.single(ByteString("{'schemaInvalid': ''", StandardCharsets.UTF_8))
  val SchemaValidXml: Source[ByteString, NotUsed]       = Source.single(ByteString(<CC015></CC015>.mkString, StandardCharsets.UTF_8))
  val UpstreamError: Source[ByteString, NotUsed]        = Source.single(ByteString("error", StandardCharsets.UTF_8))
  val InternalServiceError: Source[ByteString, NotUsed] = Source.single(ByteString("exception", StandardCharsets.UTF_8))

  private val upstreamErrorResponse =
    UpstreamErrorResponse(Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> "Internal Server Error").toString, INTERNAL_SERVER_ERROR)

  private val internalException = JsResult.Exception(JsError("arbitrary failure"))

  val fakeConversionConnector: ConversionConnector = new ConversionConnector {

    override def post(messageType: MessageType, xmlStream: Source[ByteString, _])(implicit
      hc: HeaderCarrier,
      ec: ExecutionContext
    ): Future[Source[ByteString, _]] =
      xmlStream match {
        case SchemaValidJson      => Future.successful(SchemaValidXml)
        case SchemaInvalidJson    => Future.failed(upstreamErrorResponse)
        case UpstreamError        => Future.failed(upstreamErrorResponse)
        case InternalServiceError => Future.failed(internalException)
      }
  }

  "Convert JSON to XML" - {

    "should return a right with no conversion errors" in {
      val sut    = new ConversionServiceImpl(fakeConversionConnector)
      val result = sut.convertXmlToJson(MessageType.DepartureDeclaration, SchemaValidJson)

      whenReady(result.value) {
        _ mustBe Right(SchemaValidXml)
      }
    }

    "non-conforming JSON should return a left with a SchemaFailedToValidateError" in {
      val sut    = new ConversionServiceImpl(fakeConversionConnector)
      val result = sut.convertXmlToJson(MessageType.DepartureDeclaration, SchemaInvalidJson)

      whenReady(result.value) {
        _ mustBe Left(ConversionError.UnexpectedError(Some(upstreamErrorResponse)))
      }
    }

    "an upstream error should return a left with an UnexpectedError" in {
      val sut    = new ConversionServiceImpl(fakeConversionConnector)
      val result = sut.convertXmlToJson(MessageType.DepartureDeclaration, UpstreamError)

      Await.result(result.value, 5.seconds) mustBe Left(ConversionError.UnexpectedError(Some(upstreamErrorResponse)))
    }

    "an internal exception should return a left with an UnexpectedError" in {
      val sut    = new ConversionServiceImpl(fakeConversionConnector)
      val result = sut.convertXmlToJson(MessageType.DepartureDeclaration, InternalServiceError)

      Await.result(result.value, 5.seconds) mustBe Left(ConversionError.UnexpectedError(Some(internalException)))
    }

  }

}
