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

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.github.tomakehurst.wiremock.client.WireMock._
import config.AppConfig
import config.Constants
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
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.test.HttpClientV2Support
import utils.GuiceWiremockSuite
import utils.TestMetrics
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.ObjectStoreURI
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

  def targetUrl(eoriNumber: EORINumber, messageType: MessageType, movementId: MovementId, messageId: MessageId) =
    s"/transit-movements-router/traders/${eoriNumber.value}/movements/${messageType.movementType.urlFragment}/${movementId.value}/messages/${messageId.value}"

  implicit val hc = HeaderCarrier()

  "POST /traders/:eori/message/:movementType/:messageId/movements/:movementId" - {

    "When ACCEPTED is received, must return a successful future" in forAll(
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
          _ mustBe Right(())
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

  "POST /traders/:eori/message/:movementType/:messageId/movements/:movementId for large message route" - {

    "When ACCEPTED is received, must returned a successful future" in forAll(
      arbitrary[EORINumber],
      arbitrary[MessageType],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[ObjectStoreURI]
    ) {
      (eoriNumber, messageType, movementId, messageId, objectStoreURI) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber, messageType, movementId, messageId))
          )
            .withHeader(Constants.XObjectStoreUriHeader, equalTo(objectStoreURI.value))
            .withHeader(Constants.XMessageTypeHeader, equalTo(messageType.code))
            .willReturn(aResponse().withStatus(ACCEPTED))
        )

        whenReady(
          routerConnector
            .postLargeMessage(messageType, eoriNumber, movementId, messageId, objectStoreURI)
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
      arbitrary[MessageType],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[ObjectStoreURI]
    ) {
      (eoriNumber, messageType, movementId, messageId, objectStoreURI) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber, messageType, movementId, messageId))
          )
            .withHeader(Constants.XObjectStoreUriHeader, equalTo(objectStoreURI.value))
            .withHeader(Constants.XMessageTypeHeader, equalTo(messageType.code))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.internalServiceError()))
                )
            )
        )

        val future = routerConnector.postLargeMessage(messageType, eoriNumber, movementId, messageId, objectStoreURI).map(Right(_)).recover {
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
