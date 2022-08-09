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

package v2.connectors

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import config.AppConfig
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.http.Status.ACCEPTED
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.test.HttpClientV2Support
import utils.TestMetrics
import utils.GuiceWiremockSuite
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.DepartureId
import v2.models.errors.ErrorCode
import v2.models.errors.PresentationError
import v2.models.errors.StandardError
import v2.models.request.MessageType
import v2.utils.CommonGenerators

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class RouterConnectorSpec
    extends AnyFreeSpec
    with HttpClientV2Support
    with Matchers
    with GuiceOneAppPerSuite
    with GuiceWiremockSuite
    with ScalaFutures
    with IntegrationPatience
    with ScalaCheckDrivenPropertyChecks
    with CommonGenerators {

  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  implicit lazy val materializer: Materializer = app.materializer
  implicit lazy val ec: ExecutionContext       = app.materializer.executionContext
  lazy val routerConnector: RouterConnector    = new RouterConnectorImpl(new TestMetrics(), appConfig, httpClientV2)

  "POST /traders/:eori/message/:movementType/:messageId/movements/:movementId" - {

    lazy val messageTypeGen = Gen.oneOf(Seq(MessageType.DepartureDeclaration))

    def targetUrl(eoriNumber: EORINumber, messageType: MessageType, movementId: DepartureId, messageId: MessageId) =
      s"/transit-movements-router/traders/${eoriNumber.value}/movements/${messageType.movementType}/${movementId.value}/messages/${messageId.value}/"

    "When ACCEPTED is received, must returned a successful future" in forAll(
      arbitrary[EORINumber],
      messageTypeGen,
      arbitrary[DepartureId],
      arbitrary[MessageId]
    ) {
      (eoriNumber, messageType, movementId, messageId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber, messageType, movementId, messageId))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .willReturn(aResponse().withStatus(ACCEPTED))
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))

        whenReady(
          routerConnector
            .post(messageType, eoriNumber, movementId, messageId, source)
            .map(Right(_)) // can't test unit, but we want to test for success
            .recover {
              case thr => Left(thr)
            }
        ) {
          _ mustBe Right(())
        }
    }

    "On an upstream internal server error, get a failed Future with an UpstreamErrorResponse" in forAll(
      arbitrary[EORINumber],
      messageTypeGen,
      arbitrary[DepartureId],
      arbitrary[MessageId]
    ) {
      (eoriNumber, messageType, movementId, messageId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber, messageType, movementId, messageId))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.internalServiceError()))
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8))

        val future = routerConnector.post(messageType, eoriNumber, movementId, messageId, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.get mustBe a[UpstreamErrorResponse]
            val response = result.left.get.asInstanceOf[UpstreamErrorResponse]
            response.statusCode mustBe INTERNAL_SERVER_ERROR
            Json.parse(response.message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
        }
    }
  }

  override protected def portConfigKey: Seq[String] =
    Seq("microservice.services.transit-movements-router.port")
}
