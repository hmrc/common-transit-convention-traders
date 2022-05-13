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
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.connectors.ValidationConnector
import v2.models.ValidationError
import v2.models.request.MessageType
import v2.models.responses.ValidationResponse

import java.nio.charset.StandardCharsets
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ValidationServiceSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val SchemaValidXml: Source[ByteString, NotUsed]       = Source.single(ByteString("<schemaValid></schemaValid>", StandardCharsets.UTF_8))
  val SchemaInvalidXml: Source[ByteString, NotUsed]     = Source.single(ByteString("<schemaInvalid></schemaInvalid>", StandardCharsets.UTF_8))
  val InvalidXml: Source[ByteString, NotUsed]           = Source.single(ByteString("invalid", StandardCharsets.UTF_8))
  val UpstreamError: Source[ByteString, NotUsed]        = Source.single(ByteString("error", StandardCharsets.UTF_8))
  val InternalServiceError: Source[ByteString, NotUsed] = Source.single(ByteString("exception", StandardCharsets.UTF_8))

  private val upstreamErrorResponse =
    UpstreamErrorResponse(Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> "Internal Server Error").toString, INTERNAL_SERVER_ERROR)
  private val internalException = new IllegalStateException("arbitrary failure")

  val fakeValidationConnector: ValidationConnector = new ValidationConnector {

    override def validate(messageType: MessageType, xmlStream: Source[ByteString, _])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
      xmlStream match {
        case SchemaValidXml       => Future.successful(Json.toJson(ValidationResponse(Seq.empty)))
        case SchemaInvalidXml     => Future.successful(Json.toJson(ValidationResponse(Seq("nope"))))
        case InvalidXml           => Future.failed(UpstreamErrorResponse(Json.obj("code" -> "BAD_REQUEST", "message" -> "Invalid XML").toString, BAD_REQUEST))
        case UpstreamError        => Future.failed(upstreamErrorResponse)
        case InternalServiceError => Future.failed(internalException)
      }
  }

  "validating XML" - {

    "should return a right with no validation errors" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXML(MessageType.DepartureDeclaration, SchemaValidXml)

      Await.result(result.value, 5.seconds) mustBe Right(())
    }

    "non-conforming XML should return a left with a SchemaValidationError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXML(MessageType.DepartureDeclaration, SchemaInvalidXml)

      Await.result(result.value, 5.seconds) mustBe Left(ValidationError.SchemaValidationError(Seq("nope")))
    }

    "a string that is not XML should return a left with a XmlParseError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXML(MessageType.DepartureDeclaration, InvalidXml)

      Await.result(result.value, 5.seconds) mustBe Left(ValidationError.XmlParseError)
    }

    "an upstream error should return a left with a OtherError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXML(MessageType.DepartureDeclaration, UpstreamError)

      Await.result(result.value, 5.seconds) mustBe Left(ValidationError.OtherError(Some(upstreamErrorResponse)))
    }

    "an internal exception should return a left with a OtherError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXML(MessageType.DepartureDeclaration, InternalServiceError)

      Await.result(result.value, 5.seconds) mustBe Left(ValidationError.OtherError(Some(internalException)))
    }

  }

}
