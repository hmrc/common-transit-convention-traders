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

package v2_1.services

import cats.data.NonEmptyList
import models.common.errors.FailedToValidateError
import models.common.errors.JsonValidationError
import models.common.errors.XmlValidationError
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.NOT_FOUND
import play.api.libs.json.JsError
import play.api.libs.json.JsResult
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2_1.connectors.ValidationConnector
import v2_1.models.request.MessageType
import v2_1.models.responses._

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ValidationServiceSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val SchemaValidJson: Source[ByteString, NotUsed]      = Source.single(ByteString("{}", StandardCharsets.UTF_8))
  val SchemaInvalidJson: Source[ByteString, NotUsed]    = Source.single(ByteString("{", StandardCharsets.UTF_8))
  val SchemaValidXml: Source[ByteString, NotUsed]       = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
  val SchemaInvalidXml: Source[ByteString, NotUsed]     = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))
  val BusinessError: Source[ByteString, NotUsed]        = Source.single(ByteString(<be></be>.mkString, StandardCharsets.UTF_8))
  val UpstreamError: Source[ByteString, NotUsed]        = Source.single(ByteString("error", StandardCharsets.UTF_8))
  val InternalServiceError: Source[ByteString, NotUsed] = Source.single(ByteString("exception", StandardCharsets.UTF_8))
  val Invalid: Source[ByteString, NotUsed]              = Source.single(ByteString("invalid", StandardCharsets.UTF_8))
  val BadMessageType: Source[ByteString, NotUsed]       = Source.single(ByteString("badMessageType", StandardCharsets.UTF_8))

  private val parseError =
    UpstreamErrorResponse(Json.obj("code" -> "BAD_REQUEST", "message" -> "parse error").toString, BAD_REQUEST)

  private def badMessageType(messageType: MessageType) =
    UpstreamErrorResponse(Json.obj("code" -> "NOT_FOUND", "message" -> messageType.toString).toString, NOT_FOUND)

  private val upstreamErrorResponse =
    UpstreamErrorResponse(Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> "Internal Server Error").toString, INTERNAL_SERVER_ERROR)
  private val internalException = JsResult.Exception(JsError("arbitrary failure"))

  val fakeValidationConnector: ValidationConnector = new ValidationConnector {

    override def postXml(messageType: MessageType, stream: Source[ByteString, ?])(implicit
      hc: HeaderCarrier,
      ec: ExecutionContext
    ): Future[Option[XmlValidationErrorResponse]] =
      stream match {
        case SchemaValidXml       => Future.successful(None)
        case SchemaInvalidXml     => Future.successful(Some(XmlSchemaValidationResponse(NonEmptyList(XmlValidationError(1, 1, "nope"), Nil))))
        case BusinessError        => Future.successful(Some(BusinessValidationResponse("business error")))
        case BadMessageType       => Future.failed(badMessageType(messageType))
        case UpstreamError        => Future.failed(upstreamErrorResponse)
        case InternalServiceError => Future.failed(internalException)
        case invalidStream        => fail(s"Invalid Stream: $invalidStream")
      }

    override def postJson(messageType: MessageType, stream: Source[ByteString, ?])(implicit
      hc: HeaderCarrier,
      ec: ExecutionContext
    ): Future[Option[JsonValidationErrorResponse]] =
      stream match {
        case SchemaValidJson => Future.successful(None)
        case SchemaInvalidJson =>
          Future.successful(Some(JsonSchemaValidationResponse(NonEmptyList(JsonValidationError("IEO15C:messageSender", "MessageSender not expected"), Nil))))
        case BusinessError        => Future.successful(Some(BusinessValidationResponse("business error")))
        case Invalid              => Future.failed(parseError)
        case BadMessageType       => Future.failed(badMessageType(messageType))
        case UpstreamError        => Future.failed(upstreamErrorResponse)
        case InternalServiceError => Future.failed(internalException)
        case invalidStream        => fail(s"Invalid Stream: $invalidStream")
      }
  }

  "validating XML" - {

    "should return a right with no validation errors" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXml(MessageType.DeclarationData, SchemaValidXml)

      whenReady(result.value) {
        result => result mustBe Right(())
      }
    }

    "non-conforming XML should return a left with a SchemaFailedToValidateError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXml(MessageType.DeclarationData, SchemaInvalidXml)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList(XmlValidationError(1, 1, "nope"), Nil)))
      }
    }

    "business invalid XML should return a left with a SchemaFailedToValidateError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXml(MessageType.DeclarationData, BusinessError)

      whenReady(result.value) {
        result =>
          result mustBe Left(
            FailedToValidateError.BusinessValidationError("business error")
          )
      }
    }

    "a bad message type should return a left with a InvalidMessageTypeError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXml(MessageType.DeclarationData, BadMessageType)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.InvalidMessageTypeError(MessageType.DeclarationData.toString))
      }
    }

    "an upstream error should return a left with an UnexpectedError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXml(MessageType.DeclarationData, UpstreamError)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.UnexpectedError(Some(upstreamErrorResponse)))
      }
    }

    "an internal exception should return a left with an UnexpectedError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXml(MessageType.DeclarationData, InternalServiceError)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.UnexpectedError(Some(internalException)))
      }
    }

  }

  "validating JSON" - {

    "should return a right with no validation errors" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateJson(MessageType.DeclarationData, SchemaValidJson)

      whenReady(result.value) {
        result => result mustBe Right(())
      }
    }

    "non-conforming JSON should return a left with a SchemaFailedToValidateError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateJson(MessageType.DeclarationData, SchemaInvalidJson)

      whenReady(result.value) {
        result =>
          result mustBe Left(
            FailedToValidateError.JsonSchemaFailedToValidateError(NonEmptyList(JsonValidationError("IEO15C:messageSender", "MessageSender not expected"), Nil))
          )
      }
    }

    "business invalid JSON should return a left with a SchemaFailedToValidateError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateJson(MessageType.DeclarationData, BusinessError)

      whenReady(result.value) {
        result =>
          result mustBe Left(
            FailedToValidateError.BusinessValidationError("business error")
          )
      }
    }

    "invalid JSON should return a left with a ParsingError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateJson(MessageType.DeclarationData, Invalid)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.ParsingError("parse error"))
      }
    }

    "a bad message type should return a left with a InvalidMessageTypeError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateJson(MessageType.DeclarationData, BadMessageType)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.InvalidMessageTypeError(MessageType.DeclarationData.toString))
      }
    }

    "an upstream error should return a left with an UnexpectedError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateJson(MessageType.DeclarationData, UpstreamError)

      whenReady(result.value) {
        result =>
          result mustBe Left(FailedToValidateError.UnexpectedError(Some(upstreamErrorResponse)))
      }
    }

    "an internal exception should return a left with an UnexpectedError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateJson(MessageType.DeclarationData, InternalServiceError)

      whenReady(result.value) {
        result =>
          result mustBe Left(FailedToValidateError.UnexpectedError(Some(internalException)))
      }
    }

  }

}
