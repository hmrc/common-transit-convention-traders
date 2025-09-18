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

import base.TestCommonGenerators
import connectors.AuditingConnector
import models.*
import models.AuditType.DeclarationData
import models.common.EORINumber
import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import models.request.MessageType
import org.apache.pekko.stream.scaladsl.Source
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import models.Version.V2_1
import models.Version.V3_0
import org.scalatest.OptionValues
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.slf4j
import play.api.Logger
import play.api.http.MimeTypes
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuditingServiceSpec
    extends AnyFreeSpec
    with Matchers
    with ScalaFutures
    with OptionValues
    with MockitoSugar
    with ScalaCheckDrivenPropertyChecks
    with TestCommonGenerators
    with BeforeAndAfterEach {

  val mockConnector: AuditingConnector = mock[AuditingConnector]
  val sut: AuditingService             = new AuditingService(mockConnector)
  implicit val hc: HeaderCarrier       = HeaderCarrier()
  val smallMessageSize                 = 49999
  val version: Version                 = Gen.oneOf(V2_1, V3_0).sample.value

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
                  eqTo[Long](smallMessageSize.toLong),
                  any(),
                  eqTo(movementId),
                  eqTo(messageId),
                  eqTo(eoriNumber),
                  eqTo(movementType),
                  eqTo(messageType),
                  eqTo(version)
                )(any(), any())
              )
                .thenReturn(Future.successful(()))

              whenReady(
                sut.auditMessageEvent(
                  AuditType.DeclarationData,
                  contentType,
                  smallMessageSize.toLong,
                  Source.empty,
                  movementId,
                  messageId,
                  eoriNumber,
                  movementType,
                  messageType,
                  version
                )
              ) {
                _ =>
                  verify(mockConnector, times(1)).postMessageType(
                    eqTo(DeclarationData),
                    eqTo(contentType),
                    eqTo[Long](smallMessageSize.toLong),
                    any(),
                    eqTo(movementId),
                    eqTo(messageId),
                    eqTo(eoriNumber),
                    eqTo(movementType),
                    eqTo(messageType),
                    eqTo(version)
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
                  eqTo(messageType),
                  eqTo(version)
                )(any(), any())
              ).thenReturn(Future.failed(exception))

              object Harness extends AuditingService(mockConnector) {
                val logger0: slf4j.Logger = mock[org.slf4j.Logger]
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
                  messageType,
                  version
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
                    eqTo(messageType),
                    eqTo(version)
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
            eqTo(messageType),
            eqTo(version)
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
            messageType,
            version
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
              eqTo(messageType),
              eqTo(version)
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
            eqTo(messageType),
            eqTo(version)
          )(any(), any())
        ).thenReturn(Future.failed(exception))

        object Harness extends AuditingService(mockConnector) {
          val logger0: slf4j.Logger = mock[org.slf4j.Logger]
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
            messageType,
            version
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
              eqTo(messageType),
              eqTo(version)
            )(any(), any())
            verify(Harness.logger0, times(1)).warn(eqTo("Unable to audit payload due to an exception"), eqTo(exception))
        }
    }
  }
}
