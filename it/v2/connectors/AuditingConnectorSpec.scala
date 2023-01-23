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
import io.lemonlabs.uri.Url
import org.mockito.Mockito.when
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
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

import scala.concurrent.ExecutionContext.Implicits.global

class AuditingConnectorSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with WiremockSuite
    with IntegrationPatience
    with HttpClientV2Support {

  val mockAppConfig = mock[AppConfig]
  when(mockAppConfig.auditingUrl).thenAnswer(
    _ => Url.parse(server.baseUrl())
  ) // using thenAnswer for lazy semantics

  lazy val sut                        = new AuditingConnectorImpl(httpClientV2, mockAppConfig, new TestMetrics)
  def targetUrl(auditType: AuditType) = s"/transit-movements-auditing/audit/${auditType.name}"

  "when sending an audit message" - Seq(MimeTypes.XML, MimeTypes.JSON).foreach {
    contentType =>
      s"when the content-type equals $contentType" - {
        "return a successful future if the audit message was accepted" in {

          // given this endpoint
          server.stubFor(
            post(
              urlEqualTo(targetUrl(AuditType.DeclarationData))
            )
              .withHeader(HeaderNames.CONTENT_TYPE, equalTo(contentType))
              .willReturn(aResponse().withStatus(ACCEPTED))
          )

          implicit val hc = HeaderCarrier()
          // when we call the audit service
          val future = sut.post(AuditType.DeclarationData, Source.empty, contentType)

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
                  .withHeader(HeaderNames.CONTENT_TYPE, equalTo(contentType))
                  .willReturn(aResponse().withStatus(statusCode))
              )

              implicit val hc = HeaderCarrier()
              // when we call the audit service
              val future = sut.post(AuditType.DeclarationData, Source.empty, contentType)

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
