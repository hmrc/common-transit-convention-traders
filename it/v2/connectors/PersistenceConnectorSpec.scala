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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.fasterxml.jackson.core.JsonParseException
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import config.AppConfig
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.OK
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
import v2.models.MovementId
import v2.models.errors.ErrorCode
import v2.models.errors.PresentationError
import v2.models.errors.StandardError
import v2.models.responses.DeclarationResponse

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class PersistenceConnectorSpec
    extends AnyFreeSpec
    with HttpClientV2Support
    with Matchers
    with GuiceOneAppPerSuite
    with GuiceWiremockSuite
    with ScalaFutures
    with IntegrationPatience
    with ScalaCheckDrivenPropertyChecks {

  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  lazy val persistenceConnector: PersistenceConnectorImpl = new PersistenceConnectorImpl(httpClientV2, appConfig, new TestMetrics())
  implicit lazy val ec: ExecutionContext                  = app.materializer.executionContext

  "POST /traders/:eori/message/departures" - {

    lazy val shortUuidGen: Arbitrary[String] = Arbitrary(Gen.long.map {
      l: Long =>
        f"${BigInt(l)}%016x"
    })

    lazy val okResultGen =
      for {
        movementId <- shortUuidGen.arbitrary.map(MovementId.apply)
        messageId  <- shortUuidGen.arbitrary.map(MessageId.apply)
      } yield DeclarationResponse(movementId, messageId)

    lazy val eoriNumberGen = Gen.alphaNumStr.map(EORINumber.apply)

    def targetUrl(eoriNumber: EORINumber) = s"/transit-movements/traders/${eoriNumber.value}/movements/departures/"

    "On successful creation of an element, must return OK" in forAll(eoriNumberGen, okResultGen) {
      (eoriNumber, okResult) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .willReturn(
              aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(okResult)))
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))

        whenReady(persistenceConnector.post(eoriNumber, source)) {
          result =>
            result mustBe okResult
        }
    }

    "On an upstream internal server error, get a UpstreamErrorResponse" in forAll(eoriNumberGen) {
      eoriNumber =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
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

        val future = persistenceConnector.post(eoriNumber, source).map(Right(_)).recover {
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

    "On an upstream bad request, get an UpstreamErrorResponse" in forAll(eoriNumberGen) {
      eoriNumber =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.badRequestError("Bad request")))
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8))

        val future = persistenceConnector.post(eoriNumber, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.get mustBe a[UpstreamErrorResponse]
            val response = result.left.get.asInstanceOf[UpstreamErrorResponse]
            response.statusCode mustBe BAD_REQUEST
            Json.parse(response.message).validate[StandardError] mustBe JsSuccess(StandardError("Bad request", ErrorCode.BadRequest))
        }
    }

    "On an incorrect Json fragment, must return a JsResult.Exception" in forAll(eoriNumberGen) {
      eoriNumber =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  "{ hello"
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))

        val future = persistenceConnector.post(eoriNumber, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.get mustBe a[JsonParseException]
        }
    }
  }

  override protected def portConfigKey: Seq[String] =
    Seq("microservice.services.transit-movements.port")
}
