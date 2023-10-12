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

import akka.stream.scaladsl.Source
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Logger
import play.api.http.MimeTypes
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import v2.base.TestCommonGenerators
import v2.connectors.AuditingConnector
import v2.models.AuditType.DeclarationData
import v2.models._
import v2.models.request.MessageType

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuditingServiceSpec
    extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with MockitoSugar
    with ScalaCheckDrivenPropertyChecks
    with TestCommonGenerators
    with BeforeAndAfterEach {

  val mockConnector: AuditingConnector = mock[AuditingConnector]
  val sut                              = new AuditingServiceImpl(mockConnector)
  implicit val hc                      = HeaderCarrier()
  val smallMessageSize                 = 49999

  override def beforeEach(): Unit =
    reset(mockConnector)

  "For auditing message type event" - {
    "Posting any audit type event" - Seq(MimeTypes.XML, MimeTypes.JSON).foreach {
      contentType =>
        s"when contentType equals $contentType" - {
          "on success audit, return the successful future" in forAll(
            Gen.option(arbitrary[EORINumber]),
            Gen.option(arbitrary[MovementType]),
            Gen.option(arbitrary[MovementId]),
            Gen.option(arbitrary[MessageId]),
            Gen.option(arbitrary[MessageType])
          ) {
            (eoriNumber, movementType, movementId, messageId, messageType) =>
              when(
                mockConnector.postMessageType(
                  eqTo(DeclarationData),
                  eqTo(contentType),
                  eqTo[Long](smallMessageSize),
                  any(),
                  eqTo(movementId),
                  eqTo(messageId),
                  eqTo(eoriNumber),
                  eqTo(movementType),
                  eqTo(messageType)
                )(any(), any())
              )
                .thenReturn(Future.successful(()))

              whenReady(
                sut.auditMessageEvent(
                  AuditType.DeclarationData,
                  contentType,
                  smallMessageSize,
                  Source.empty,
                  movementId,
                  messageId,
                  eoriNumber,
                  movementType,
                  messageType
                )
              ) {
                _ =>
                  verify(mockConnector, times(1)).postMessageType(
                    eqTo(DeclarationData),
                    eqTo(contentType),
                    eqTo[Long](smallMessageSize),
                    any(),
                    eqTo(movementId),
                    eqTo(messageId),
                    eqTo(eoriNumber),
                    eqTo(movementType),
                    eqTo(messageType)
                  )(any(), any())
              }
          }

          "on failure audit, will log a message" in forAll(
            Gen.option(arbitrary[EORINumber]),
            Gen.option(arbitrary[MovementType]),
            Gen.option(arbitrary[MovementId]),
            Gen.option(arbitrary[MessageId]),
            Gen.option(arbitrary[MessageType])
          ) {
            (eoriNumber, movementType, movementId, messageId, messageType) =>
              val exception = new IllegalStateException("failed")
              when(
                mockConnector.postMessageType(
                  eqTo(DeclarationData),
                  eqTo(contentType),
                  any(),
                  any(),
                  eqTo(movementId),
                  eqTo(messageId),
                  eqTo(eoriNumber),
                  eqTo(movementType),
                  eqTo(messageType)
                )(any(), any())
              ).thenReturn(Future.failed(exception))

              object Harness extends AuditingServiceImpl(mockConnector) {
                val logger0 = mock[org.slf4j.Logger]
                when(logger0.isWarnEnabled()).thenReturn(true)
                override val logger: Logger = new Logger(logger0)
              }

              whenReady(
                Harness.auditMessageEvent(
                  AuditType.DeclarationData,
                  contentType,
                  0L,
                  Source.empty,
                  movementId,
                  messageId,
                  eoriNumber,
                  movementType,
                  messageType
                )
              ) {
                _ =>
                  verify(mockConnector, times(1)).postMessageType(
                    eqTo(DeclarationData),
                    eqTo(contentType),
                    any(),
                    any(),
                    eqTo(movementId),
                    eqTo(messageId),
                    eqTo(eoriNumber),
                    eqTo(movementType),
                    eqTo(messageType)
                  )(any(), any())
                  verify(Harness.logger0, times(1)).warn(eqTo("Unable to audit payload due to an exception"), eqTo(exception))
              }

          }

        }

    }
  }

  "For auditing status type event" - {
    implicit val jsValueArbitrary: Arbitrary[JsValue] = Arbitrary(Gen.const(Json.obj("code" -> "BUSINESS_VALIDATION_ERROR", "message" -> "Expected NTA.GB")))
    "Posting any audit type event on success audit, return the successful future" in forAll(
      Gen.option(arbitrary[EORINumber]),
      Gen.option(arbitrary[MovementType]),
      Gen.option(arbitrary[MovementId]),
      Gen.option(arbitrary[MessageId]),
      Gen.option(arbitrary[MessageType]),
      Gen.option(arbitrary[JsValue])
    ) {
      (eoriNumber, movementType, movementId, messageId, messageType, payload) =>
        when(
          mockConnector.postStatus(
            eqTo(DeclarationData),
            eqTo(payload),
            eqTo(movementId),
            eqTo(messageId),
            eqTo(eoriNumber),
            eqTo(movementType),
            eqTo(messageType)
          )(any(), any())
        )
          .thenReturn(Future.successful(()))

        whenReady(
          sut.auditStatusEvent(
            AuditType.DeclarationData,
            payload,
            movementId,
            messageId,
            eoriNumber,
            movementType,
            messageType
          )
        ) {
          _ =>
            verify(mockConnector, times(1)).postStatus(
              eqTo(DeclarationData),
              eqTo(payload),
              eqTo(movementId),
              eqTo(messageId),
              eqTo(eoriNumber),
              eqTo(movementType),
              eqTo(messageType)
            )(any(), any())
        }
    }

    "on failure audit, will log a message" in forAll(
      Gen.option(arbitrary[EORINumber]),
      Gen.option(arbitrary[MovementType]),
      Gen.option(arbitrary[MovementId]),
      Gen.option(arbitrary[MessageId]),
      Gen.option(arbitrary[MessageType]),
      Gen.option(arbitrary[JsValue])
    ) {
      (eoriNumber, movementType, movementId, messageId, messageType, payload) =>
        val exception = new IllegalStateException("failed")
        when(
          mockConnector.postStatus(
            eqTo(DeclarationData),
            eqTo(payload),
            eqTo(movementId),
            eqTo(messageId),
            eqTo(eoriNumber),
            eqTo(movementType),
            eqTo(messageType)
          )(any(), any())
        ).thenReturn(Future.failed(exception))

        object Harness extends AuditingServiceImpl(mockConnector) {
          val logger0 = mock[org.slf4j.Logger]
          when(logger0.isWarnEnabled()).thenReturn(true)
          override val logger: Logger = new Logger(logger0)
        }

        whenReady(
          Harness.auditStatusEvent(
            AuditType.DeclarationData,
            payload,
            movementId,
            messageId,
            eoriNumber,
            movementType,
            messageType
          )
        ) {
          _ =>
            verify(mockConnector, times(1)).postStatus(
              eqTo(DeclarationData),
              eqTo(payload),
              eqTo(movementId),
              eqTo(messageId),
              eqTo(eoriNumber),
              eqTo(movementType),
              eqTo(messageType)
            )(any(), any())
            verify(Harness.logger0, times(1)).warn(eqTo("Unable to audit payload due to an exception"), eqTo(exception))
        }

    }

  }

}
