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

package connectors

import com.codahale.metrics.MetricRegistry
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import com.github.tomakehurst.wiremock.client.WireMock.*
import config.AppConfig
import config.Constants
import models.SubmissionRoute
import models.common.MessageId
import models.common.MovementId
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.HeaderNames
import play.api.http.Status.ACCEPTED
import play.api.http.Status.CREATED
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.test.HttpClientV2Support
import utils.CommonGenerators
import utils.GuiceWiremockSuite
import models.common.EORINumber
import models.common.errors.ErrorCode
import models.common.errors.PresentationError
import models.common.errors.StandardError
import models.request.MessageType

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

  private val token: String = Gen.alphaNumStr.sample.get

  override val configurationOverride: Seq[(String, String)] =
    Seq(
      "internal-auth.token" -> token
    )

  implicit lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  implicit lazy val materializer: Materializer = app.materializer
  implicit lazy val ec: ExecutionContext       = app.materializer.executionContext
  lazy val routerConnector: RouterConnector    = new RouterConnector(new MetricRegistry, httpClientV2)

  def targetUrl(eoriNumber: EORINumber, messageType: MessageType, movementId: MovementId, messageId: MessageId) =
    s"/transit-movements-router/traders/${eoriNumber.value}/movements/${messageType.movementType.urlFragment}/${movementId.value}/messages/${messageId.value}"

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "POST /traders/:eori/message/:movementType/:messageId/movements/:movementId" - {

    "When CREATED is received, get the submission route as via EIS" in forAll(
      arbitrary[EORINumber],
      arbitrary[MessageType],
      arbitrary[MovementId],
      arbitrary[MessageId]
    ) {
      (eoriNumber, messageType, movementId, messageId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber, messageType, movementId, messageId))
          ).withRequestBody(equalToXml(<test></test>.mkString))
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader(Constants.XMessageTypeHeader, equalTo(messageType.code))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(aResponse().withStatus(CREATED))
        )

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))

        whenReady(
          routerConnector
            .post(messageType, eoriNumber, movementId, messageId, source)
            .map(Right(_)) // can't test unit, but we want to test for success
            .recover {
              case thr => Left(thr)
            }
        ) {
          _ mustBe Right(SubmissionRoute.ViaEIS)
        }
    }

    "When ACCEPTED is received, get the submission route as via SDES" in forAll(
      arbitrary[EORINumber],
      arbitrary[MessageType],
      arbitrary[MovementId],
      arbitrary[MessageId],
      Gen.stringOfN(15, Gen.alphaNumChar)
    ) {
      (eoriNumber, messageType, movementId, messageId, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber, messageType, movementId, messageId))
          ).withRequestBody(equalToXml(<test></test>.mkString))
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader(Constants.XMessageTypeHeader, equalTo(messageType.code))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(aResponse().withStatus(ACCEPTED))
        )

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))

        whenReady(
          routerConnector
            .post(messageType, eoriNumber, movementId, messageId, source)(
              implicitly[ExecutionContext],
              HeaderCarrier(otherHeaders = Seq((Constants.XClientIdHeader -> clientId)))
            )
            .map(Right(_)) // can't test unit, but we want to test for success
            .recover {
              case thr => Left(thr)
            }
        ) {
          _ mustBe Right(SubmissionRoute.ViaSDES)
        }
    }

    "On an upstream internal server error, get a failed Future with an UpstreamErrorResponse" in forAll(
      arbitrary[EORINumber],
      arbitrary[MessageType],
      arbitrary[MovementId],
      arbitrary[MessageId]
    ) {
      (eoriNumber, messageType, movementId, messageId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber, messageType, movementId, messageId))
          ).withRequestBody(equalToXml(<test></test>.mkString))
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader(Constants.XMessageTypeHeader, equalTo(messageType.code))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.internalServiceError()))
                )
            )
        )

        val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8))

        val future = routerConnector.post(messageType, eoriNumber, movementId, messageId, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.toOption.get mustBe a[UpstreamErrorResponse]
            val response = result.left.toOption.get.asInstanceOf[UpstreamErrorResponse]
            response.statusCode mustBe INTERNAL_SERVER_ERROR
            Json.parse(response.message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
        }
    }
  }

  override protected def portConfigKey: Seq[String] =
    Seq("microservice.services.transit-movements-router.port")
}
