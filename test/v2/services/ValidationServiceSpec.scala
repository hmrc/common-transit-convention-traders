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
import cats.data.NonEmptyList
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
import v2.connectors.ValidationConnector
import v2.models.errors.FailedToValidateError
import v2.models.errors.JsonValidationError
import v2.models.errors.XmlValidationError
import v2.models.request.MessageType
import v2.models.responses.JsonValidationResponse
import v2.models.responses.XmlValidationResponse

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ValidationServiceSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val SchemaValidJson: Source[ByteString, NotUsed]         = Source.single(ByteString("{}", StandardCharsets.UTF_8))
  val SchemaInvalidJson: Source[ByteString, NotUsed]       = Source.single(ByteString("{", StandardCharsets.UTF_8))
  val SchemaValidXml: Source[ByteString, NotUsed]          = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
  val SchemaInvalidXml: Source[ByteString, NotUsed]        = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))
  val UpstreamError: Source[ByteString, NotUsed]           = Source.single(ByteString("error", StandardCharsets.UTF_8))
  val InternalServiceError: Source[ByteString, NotUsed]    = Source.single(ByteString("exception", StandardCharsets.UTF_8))
  val Invalid: Source[ByteString, NotUsed]                 = Source.single(ByteString("invalid", StandardCharsets.UTF_8))
  val BusinessValidationError: Source[ByteString, NotUsed] = Source.single(ByteString("invalid business rule", StandardCharsets.UTF_8))
  val BadMessageType: Source[ByteString, NotUsed]          = Source.single(ByteString("badMessageType", StandardCharsets.UTF_8))

  val uriBadMessageType      = "badMessage"
  val uriUpstreamError       = "upstreamError"
  val uriInternalServerError = "internalServerError"

  private val parseError =
    UpstreamErrorResponse(Json.obj("code" -> "BAD_REQUEST", "message" -> "parse error").toString, BAD_REQUEST)

  private val businessValidationError =
    UpstreamErrorResponse(Json.obj("code" -> "BUSINESS_VALIDATION_ERROR", "message" -> "business validation error").toString, BAD_REQUEST)

  private def badMessageType(messageType: MessageType) =
    UpstreamErrorResponse(Json.obj("code" -> "NOT_FOUND", "message" -> messageType.toString).toString, NOT_FOUND)

  private val upstreamErrorResponse =
    UpstreamErrorResponse(Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> "Internal Server Error").toString, INTERNAL_SERVER_ERROR)
  private val internalException = new JsResult.Exception(JsError("arbitrary failure"))

  val fakeValidationConnector: ValidationConnector = new ValidationConnector {

    class InvalidMessageTypeError(message: String)  extends Throwable(message)
    class BusinessValidationError(message: String)  extends Throwable(message)
    class UnexpectedError(cause: Option[Throwable]) extends Throwable(cause.orNull)
    class ParsingError(message: String)             extends Throwable(message)

    override def postXml(messageType: MessageType, stream: Source[ByteString, _])(implicit
      hc: HeaderCarrier,
      ec: ExecutionContext
    ): Future[Either[FailedToValidateError, Option[XmlValidationResponse]]] =
      stream match {
        case SchemaValidXml => Future.successful(Right(None))
        case SchemaInvalidXml =>
          Future.successful(
            Left(
              FailedToValidateError.XmlSchemaFailedToValidateError(
                NonEmptyList(XmlValidationError(1, 1, "nope"), Nil)
              )
            )
          )
        case BadMessageType =>
          Future.successful(Left(FailedToValidateError.InvalidMessageTypeError(messageType.toString)))
        case BusinessValidationError =>
          Future.successful(Left(FailedToValidateError.BusinessValidationError("business validation error")))
        case UpstreamError =>
          Future.failed(upstreamErrorResponse)
        case InternalServiceError =>
          Future.failed(internalException)
      }

    override def postJson(messageType: MessageType, stream: Source[ByteString, _])(implicit
      hc: HeaderCarrier,
      ec: ExecutionContext
    ): Future[Either[FailedToValidateError, Option[JsonValidationResponse]]] =
      stream match {
        case SchemaValidJson =>
          Future.successful(Right(None))
        case SchemaInvalidJson =>
          Future.successful(
            Left(
              FailedToValidateError.JsonSchemaFailedToValidateError(
                NonEmptyList(JsonValidationError("IEO15C:messageSender", "MessageSender not expected"), Nil)
              )
            )
          )
        case Invalid =>
          Future.successful(Left(FailedToValidateError.ParsingError("parse error")))
        case BusinessValidationError =>
          Future.successful(Left(FailedToValidateError.BusinessValidationError("business validation error")))
        case BadMessageType =>
          Future.successful(Left(FailedToValidateError.InvalidMessageTypeError(messageType.toString)))
        case UpstreamError =>
          Future.successful(Left(FailedToValidateError.UnexpectedError(Some(upstreamErrorResponse))))
        case InternalServiceError =>
          Future.successful(Left(FailedToValidateError.UnexpectedError(Some(internalException))))
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

    "a bad message type should return a left with a InvalidMessageTypeError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXml(MessageType.DeclarationData, BadMessageType)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.InvalidMessageTypeError(MessageType.DeclarationData.toString))
      }
    }

    "a bad business validation rule should return a left with a BusinessValidationError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXml(MessageType.DeclarationData, BusinessValidationError)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.BusinessValidationError("business validation error"))
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

    "invalid JSON should return a left with a ParsingError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateJson(MessageType.DeclarationData, Invalid)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.ParsingError("parse error"))
      }
    }

    "a bad business validation rule should return a left with a BusinessValidationError" in {
      val sut    = new ValidationServiceImpl(fakeValidationConnector)
      val result = sut.validateXml(MessageType.DeclarationData, BusinessValidationError)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.BusinessValidationError("business validation error"))
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
