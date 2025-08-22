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

package services

import cats.data.EitherT
import cats.data.NonEmptyList
import connectors.ValidationConnector
import models.common.errors.FailedToValidateError
import models.common.errors.JsonValidationError
import models.common.errors.XmlValidationError
import models.request.MessageType
import models.responses.*
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
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

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ValidationServiceSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar {

  "validating XML" - {

    "should return a right with no validation errors" in new Setup {
      when(mockValidationConnector.postXml(any, any)(any, any)).thenReturn(Future.successful(None))
      val sut: ValidationService                               = new ValidationService(mockValidationConnector)
      val result: EitherT[Future, FailedToValidateError, Unit] = sut.validateXml(MessageType.DeclarationData, SchemaValidXml)

      whenReady(result.value) {
        result => result mustBe Right(())
      }
    }

    "non-conforming XML should return a left with a SchemaFailedToValidateError" in new Setup {
      when(mockValidationConnector.postXml(any, any)(any, any))
        .thenReturn(Future.successful(Some(XmlSchemaValidationResponse(NonEmptyList(XmlValidationError(1, 1, "nope"), Nil)))))
      val sut: ValidationService                               = new ValidationService(mockValidationConnector)
      val result: EitherT[Future, FailedToValidateError, Unit] = sut.validateXml(MessageType.DeclarationData, SchemaInvalidXml)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList(XmlValidationError(1, 1, "nope"), Nil)))
      }
    }

    "business invalid XML should return a left with a SchemaFailedToValidateError" in new Setup {
      when(mockValidationConnector.postXml(any, any)(any, any)).thenReturn(Future.successful(Some(BusinessValidationResponse("business error"))))
      val sut: ValidationService                               = new ValidationService(mockValidationConnector)
      val result: EitherT[Future, FailedToValidateError, Unit] = sut.validateXml(MessageType.DeclarationData, BusinessError)

      whenReady(result.value) {
        result =>
          result mustBe Left(
            FailedToValidateError.BusinessValidationError("business error")
          )
      }
    }

    "a bad message type should return a left with a InvalidMessageTypeError" in new Setup {
      when(mockValidationConnector.postXml(any, any)(any, any)).thenReturn(Future.failed(badMessageType(MessageType.DeclarationData)))
      val sut: ValidationService                               = new ValidationService(mockValidationConnector)
      val result: EitherT[Future, FailedToValidateError, Unit] = sut.validateXml(MessageType.DeclarationData, BadMessageType)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.InvalidMessageTypeError(MessageType.DeclarationData.toString))
      }
    }

    "an upstream error should return a left with an UnexpectedError" in new Setup {
      when(mockValidationConnector.postXml(any, any)(any, any)).thenReturn(Future.failed(upstreamErrorResponse))
      val sut: ValidationService                               = new ValidationService(mockValidationConnector)
      val result: EitherT[Future, FailedToValidateError, Unit] = sut.validateXml(MessageType.DeclarationData, UpstreamError)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.UnexpectedError(Some(upstreamErrorResponse)))
      }
    }

    "an internal exception should return a left with an UnexpectedError" in new Setup {
      when(mockValidationConnector.postXml(any, any)(any, any)).thenReturn(Future.failed(internalException))
      val sut: ValidationService                               = new ValidationService(mockValidationConnector)
      val result: EitherT[Future, FailedToValidateError, Unit] = sut.validateXml(MessageType.DeclarationData, InternalServiceError)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.UnexpectedError(Some(internalException)))
      }
    }

  }

  "validating JSON" - {

    "should return a right with no validation errors" in new Setup {
      when(mockValidationConnector.postJson(any, any)(any, any)).thenReturn(Future.successful(None))
      val sut: ValidationService                               = new ValidationService(mockValidationConnector)
      val result: EitherT[Future, FailedToValidateError, Unit] = sut.validateJson(MessageType.DeclarationData, SchemaValidJson)

      whenReady(result.value) {
        result => result mustBe Right(())
      }
    }

    "non-conforming JSON should return a left with a SchemaFailedToValidateError" in new Setup {
      when(mockValidationConnector.postJson(any, any)(any, any)).thenReturn(
        Future.successful(Some(JsonSchemaValidationResponse(NonEmptyList(JsonValidationError("IEO15C:messageSender", "MessageSender not expected"), Nil))))
      )
      val sut: ValidationService                               = new ValidationService(mockValidationConnector)
      val result: EitherT[Future, FailedToValidateError, Unit] = sut.validateJson(MessageType.DeclarationData, SchemaInvalidJson)

      whenReady(result.value) {
        result =>
          result mustBe Left(
            FailedToValidateError.JsonSchemaFailedToValidateError(NonEmptyList(JsonValidationError("IEO15C:messageSender", "MessageSender not expected"), Nil))
          )
      }
    }

    "business invalid JSON should return a left with a SchemaFailedToValidateError" in new Setup {
      when(mockValidationConnector.postJson(any, any)(any, any)).thenReturn(Future.successful(Some(BusinessValidationResponse("business error"))))
      val sut: ValidationService                               = new ValidationService(mockValidationConnector)
      val result: EitherT[Future, FailedToValidateError, Unit] = sut.validateJson(MessageType.DeclarationData, BusinessError)

      whenReady(result.value) {
        result =>
          result mustBe Left(
            FailedToValidateError.BusinessValidationError("business error")
          )
      }
    }

    "invalid JSON should return a left with a ParsingError" in new Setup {
      when(mockValidationConnector.postJson(any, any)(any, any)).thenReturn(Future.failed(parseError))
      val sut: ValidationService                               = new ValidationService(mockValidationConnector)
      val result: EitherT[Future, FailedToValidateError, Unit] = sut.validateJson(MessageType.DeclarationData, Invalid)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.ParsingError("parse error"))
      }
    }

    "a bad message type should return a left with a InvalidMessageTypeError" in new Setup {
      when(mockValidationConnector.postJson(any, any)(any, any)).thenReturn(Future.failed(badMessageType(MessageType.DeclarationData)))
      val sut: ValidationService                               = new ValidationService(mockValidationConnector)
      val result: EitherT[Future, FailedToValidateError, Unit] = sut.validateJson(MessageType.DeclarationData, BadMessageType)

      whenReady(result.value) {
        result => result mustBe Left(FailedToValidateError.InvalidMessageTypeError(MessageType.DeclarationData.toString))
      }
    }

    "an upstream error should return a left with an UnexpectedError" in new Setup {
      when(mockValidationConnector.postJson(any, any)(any, any)).thenReturn(Future.failed(upstreamErrorResponse))
      val sut: ValidationService                               = new ValidationService(mockValidationConnector)
      val result: EitherT[Future, FailedToValidateError, Unit] = sut.validateJson(MessageType.DeclarationData, UpstreamError)

      whenReady(result.value) {
        result =>
          result mustBe Left(FailedToValidateError.UnexpectedError(Some(upstreamErrorResponse)))
      }
    }

    "an internal exception should return a left with an UnexpectedError" in new Setup {
      when(mockValidationConnector.postJson(any, any)(any, any)).thenReturn(Future.failed(internalException))
      val sut: ValidationService                               = new ValidationService(mockValidationConnector)
      val result: EitherT[Future, FailedToValidateError, Unit] = sut.validateJson(MessageType.DeclarationData, InternalServiceError)

      whenReady(result.value) {
        result =>
          result mustBe Left(FailedToValidateError.UnexpectedError(Some(internalException)))
      }
    }
  }

  trait Setup {
    val SchemaValidJson: Source[ByteString, NotUsed]      = Source.single(ByteString("{}", StandardCharsets.UTF_8))
    val SchemaInvalidJson: Source[ByteString, NotUsed]    = Source.single(ByteString("{", StandardCharsets.UTF_8))
    val SchemaValidXml: Source[ByteString, NotUsed]       = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
    val SchemaInvalidXml: Source[ByteString, NotUsed]     = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))
    val BusinessError: Source[ByteString, NotUsed]        = Source.single(ByteString(<be></be>.mkString, StandardCharsets.UTF_8))
    val UpstreamError: Source[ByteString, NotUsed]        = Source.single(ByteString("error", StandardCharsets.UTF_8))
    val InternalServiceError: Source[ByteString, NotUsed] = Source.single(ByteString("exception", StandardCharsets.UTF_8))
    val Invalid: Source[ByteString, NotUsed]              = Source.single(ByteString("invalid", StandardCharsets.UTF_8))
    val BadMessageType: Source[ByteString, NotUsed]       = Source.single(ByteString("badMessageType", StandardCharsets.UTF_8))

    implicit val hc: HeaderCarrier = HeaderCarrier()

    val parseError =
      UpstreamErrorResponse(Json.obj("code" -> "BAD_REQUEST", "message" -> "parse error").toString, BAD_REQUEST)

    def badMessageType(messageType: MessageType) =
      UpstreamErrorResponse(Json.obj("code" -> "NOT_FOUND", "message" -> messageType.toString).toString, NOT_FOUND)

    val upstreamErrorResponse =
      UpstreamErrorResponse(Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> "Internal Server Error").toString, INTERNAL_SERVER_ERROR)

    val internalException = JsResult.Exception(JsError("arbitrary failure"))

    val mockValidationConnector: ValidationConnector = mock[ValidationConnector]
  }
}
