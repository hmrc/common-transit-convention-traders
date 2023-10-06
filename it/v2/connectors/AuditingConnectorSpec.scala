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

package v2.connectors

import akka.stream.scaladsl.Source
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import config.AppConfig
import config.Constants
import io.lemonlabs.uri.Url
import org.mockito.Mockito.when
import org.scalacheck.Gen
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.HeaderNames
import play.api.http.Status.ACCEPTED
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.test.HttpClientV2Support
import utils.TestMetrics
import utils.WiremockSuite
import v2.models.AuditType
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementType
import v2.models.request.MessageType
import v2.utils.CommonGenerators

import scala.concurrent.ExecutionContext.Implicits.global

class AuditingConnectorSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with WiremockSuite
    with IntegrationPatience
    with HttpClientV2Support
    with ScalaCheckDrivenPropertyChecks
    with CommonGenerators {

  val token                  = Gen.alphaNumStr.sample.get
  implicit val mockAppConfig = mock[AppConfig]
  when(mockAppConfig.internalAuthToken).thenReturn(token)
  when(mockAppConfig.auditingUrl).thenAnswer(
    _ => Url.parse(server.baseUrl())
  ) // using thenAnswer for lazy semantics

  lazy val sut                        = new AuditingConnectorImpl(httpClientV2, new TestMetrics)
  def targetUrl(auditType: AuditType) = s"/transit-movements-auditing/audit/${auditType.name}"

  lazy val contentSize = 49999L

  "For a small message" - {
    "when sending an audit message" - Seq(MimeTypes.XML, MimeTypes.JSON).foreach {
      contentType =>
        s"when the content-type equals $contentType" - {
          "return a successful future if the audit message was accepted" in {

            // given this endpoint
            server.stubFor(
              post(
                urlEqualTo(targetUrl(AuditType.DeclarationData))
              )
                .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
                .withHeader(HeaderNames.CONTENT_TYPE, equalTo(contentType))
                .willReturn(aResponse().withStatus(ACCEPTED))
            )

            implicit val hc = HeaderCarrier()
            // when we call the audit service
            val future = sut.post(AuditType.DeclarationData, Source.empty, contentType, contentSize)

            // then the future should be ready
            whenReady(future) {
              _ =>
            }
          }

          "return a failed future if the audit message was not accepted" - Seq(BAD_REQUEST, INTERNAL_SERVER_ERROR).foreach {
            statusCode =>
              s"when a $statusCode is returned" in {

                // given this endpoint
                server.stubFor(
                  post(
                    urlEqualTo(targetUrl(AuditType.DeclarationData))
                  )
                    .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
                    .withHeader(HeaderNames.CONTENT_TYPE, equalTo(contentType))
                    .withHeader(Constants.XContentLengthHeader, equalTo(contentSize.toString))
                    .willReturn(aResponse().withStatus(statusCode))
                )

                implicit val hc = HeaderCarrier()
                // when we call the audit service
                val future = sut.post(AuditType.DeclarationData, Source.empty, contentType, contentSize)

                val result = future
                  .map(
                    _ => fail("A success was registered when it should have been a failure.")
                  )
                  .recover {
                    // backticks for stable identifier
                    case UpstreamErrorResponse(_, `statusCode`, _, _) => ()
                    case x: TestFailedException                       => x
                    case x                                            => fail(s"An unexpected exception was thrown: ${x.getClass.getSimpleName}, ${x.getMessage}")
                  }

                // then the future should be ready
                whenReady(result) {
                  _ =>
                }
              }
          }

        }
    }
  }

  "For auditing message Type or Status" - {
    "when sending an audit message" - Seq(MimeTypes.XML, MimeTypes.JSON).foreach {
      contentType =>
        s"when the content-type equals $contentType" - {
          "return a successful future if the audit message was accepted with all headers" in forAll(
            arbitrary[EORINumber],
            arbitrary[MovementId],
            arbitrary[MessageId],
            arbitrary[MessageType],
            arbitrary[MovementType]
          ) {
            (eori, movementId, messageId, messageType, movementType) =>
              server.stubFor(
                post(
                  urlEqualTo(targetUrl(AuditType.DeclarationData))
                )
                  .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
                  .withHeader(HeaderNames.CONTENT_TYPE, equalTo(contentType))
                  .withHeader(Constants.XContentLengthHeader, equalTo(contentSize.toString))
                  .withHeader("X-Audit-Meta-Movement-Id", equalTo(movementId.value))
                  .withHeader("X-Audit-Meta-Message-Id", equalTo(messageId.value))
                  .withHeader("X-Audit-Meta-EORI", equalTo(eori.value))
                  .withHeader("X-Audit-Meta-Path", equalTo("/customs/transits/movements"))
                  .withHeader("X-Audit-Meta-Message-Type", equalTo(messageType.code))
                  .withHeader("X-Audit-Meta-Movement-Type", equalTo(movementType.movementType))
                  .withHeader("X-Audit-Source", equalTo("common-transit-convention-traders"))
                  .willReturn(aResponse().withStatus(ACCEPTED))
              )

              implicit val hc = HeaderCarrier(otherHeaders = Seq("path" -> "/customs/transits/movements"))
              // when we call the audit service
              val future = sut.post(
                AuditType.DeclarationData,
                contentType,
                contentSize,
                Source.empty,
                Some(movementId),
                Some(messageId),
                Some(eori),
                Some(movementType),
                Some(messageType)
              )

              // then the future should be ready
              whenReady(future) {
                _ =>
              }
          }

          "return a successful future if the audit message was accepted with movementId, messageId & eoriNumber Optional header value" in forAll(
            arbitrary[EORINumber],
            arbitrary[MovementId],
            arbitrary[MessageId]
          ) {
            (eori, movementId, messageId) =>
              server.stubFor(
                post(
                  urlEqualTo(targetUrl(AuditType.DeclarationData))
                )
                  .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
                  .withHeader(HeaderNames.CONTENT_TYPE, equalTo(contentType))
                  .withHeader(Constants.XContentLengthHeader, equalTo(contentSize.toString))
                  .withHeader("X-Audit-Meta-Movement-Id", equalTo(movementId.value))
                  .withHeader("X-Audit-Meta-Message-Id", equalTo(messageId.value))
                  .withHeader("X-Audit-Meta-EORI", equalTo(eori.value))
                  .withHeader("X-Audit-Meta-Path", equalTo("/customs/transits/movements"))
                  .withHeader("X-Audit-Source", equalTo("common-transit-convention-traders"))
                  .willReturn(aResponse().withStatus(ACCEPTED))
              )

              implicit val hc = HeaderCarrier(otherHeaders = Seq("path" -> "/customs/transits/movements"))
              // when we call the audit service
              val future = sut.post(
                AuditType.DeclarationData,
                contentType,
                contentSize,
                Source.empty,
                Some(movementId),
                Some(messageId),
                Some(eori),
                None,
                None
              )

              // then the future should be ready
              whenReady(future) {
                _ =>
              }
          }

          "return a successful future if the audit message was accepted with movementType & messageType Optional header value" in forAll(
            arbitrary[MessageType],
            arbitrary[MovementType]
          ) {
            (messageType, movementType) =>
              server.stubFor(
                post(
                  urlEqualTo(targetUrl(AuditType.DeclarationData))
                )
                  .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
                  .withHeader(HeaderNames.CONTENT_TYPE, equalTo(contentType))
                  .withHeader(Constants.XContentLengthHeader, equalTo(contentSize.toString))
                  .withHeader("X-Audit-Meta-Message-Type", equalTo(messageType.code))
                  .withHeader("X-Audit-Meta-Movement-Type", equalTo(movementType.movementType))
                  .withHeader("X-Audit-Meta-Path", equalTo("/customs/transits/movements"))
                  .withHeader("X-Audit-Source", equalTo("common-transit-convention-traders"))
                  .willReturn(aResponse().withStatus(ACCEPTED))
              )

              implicit val hc = HeaderCarrier(otherHeaders = Seq("path" -> "/customs/transits/movements"))
              // when we call the audit service
              val future = sut.post(
                AuditType.DeclarationData,
                contentType,
                contentSize,
                Source.empty,
                None,
                None,
                None,
                Some(movementType),
                Some(messageType)
              )

              // then the future should be ready
              whenReady(future) {
                _ =>
              }
          }

          "return a successful future if the audit message was accepted without any Optional header value" in {
            server.stubFor(
              post(
                urlEqualTo(targetUrl(AuditType.DeclarationData))
              )
                .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
                .withHeader(HeaderNames.CONTENT_TYPE, equalTo(contentType))
                .withHeader(Constants.XContentLengthHeader, equalTo(contentSize.toString))
                .withHeader("X-Audit-Meta-Path", equalTo("/customs/transits/movements"))
                .withHeader("X-Audit-Source", equalTo("common-transit-convention-traders"))
                .willReturn(aResponse().withStatus(ACCEPTED))
            )

            implicit val hc = HeaderCarrier(otherHeaders = Seq("path" -> "/customs/transits/movements"))
            // when we call the audit service
            val future = sut.post(
              AuditType.DeclarationData,
              contentType,
              contentSize,
              Source.empty,
              None,
              None,
              None,
              None,
              None
            )

            // then the future should be ready
            whenReady(future) {
              _ =>
            }
          }

          "return a failed future if the audit message was not accepted" - Seq(BAD_REQUEST, INTERNAL_SERVER_ERROR).foreach {
            statusCode =>
              s"when a $statusCode is returned" in forAll(
                arbitrary[EORINumber],
                arbitrary[MovementId],
                arbitrary[MessageId],
                arbitrary[MessageType],
                arbitrary[MovementType]
              ) {
                (eori, movementId, messageId, messageType, movementType) =>
                  server.stubFor(
                    post(
                      urlEqualTo(targetUrl(AuditType.DeclarationData))
                    )
                      .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
                      .withHeader(HeaderNames.CONTENT_TYPE, equalTo(contentType))
                      .withHeader(Constants.XContentLengthHeader, equalTo(contentSize.toString))
                      .withHeader("X-Audit-Meta-Movement-Id", equalTo(movementId.value))
                      .withHeader("X-Audit-Meta-Message-Id", equalTo(messageId.value))
                      .withHeader("X-Audit-Meta-EORI", equalTo(eori.value))
                      .withHeader("X-Audit-Meta-Path", equalTo("/customs/transits/movements"))
                      .withHeader("X-Audit-Meta-Message-Type", equalTo(messageType.code))
                      .withHeader("X-Audit-Meta-Movement-Type", equalTo(movementType.movementType))
                      .withHeader("X-Audit-Source", equalTo("common-transit-convention-traders"))
                      .willReturn(aResponse().withStatus(statusCode))
                  )

                  implicit val hc = HeaderCarrier(otherHeaders = Seq("path" -> "/customs/transits/movements"))
                  // when we call the audit service
                  val future = sut.post(
                    AuditType.DeclarationData,
                    contentType,
                    contentSize,
                    Source.empty,
                    Some(movementId),
                    Some(messageId),
                    Some(eori),
                    Some(movementType),
                    Some(messageType)
                  )

                  val result = future
                    .map(
                      _ => fail("A success was registered when it should have been a failure.")
                    )
                    .recover {
                      // backticks for stable identifier
                      case UpstreamErrorResponse(_, `statusCode`, _, _) => ()
                      case x: TestFailedException                       => x
                      case x                                            => fail(s"An unexpected exception was thrown: ${x.getClass.getSimpleName}, ${x.getMessage}")
                    }

                  // then the future should be ready
                  whenReady(result) {
                    _ =>
                  }
              }
          }

        }
    }
  }

}
