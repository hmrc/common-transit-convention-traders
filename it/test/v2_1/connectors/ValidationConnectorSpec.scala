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

package v2_1.connectors

import cats.data.NonEmptyList
import com.codahale.metrics.MetricRegistry
import com.fasterxml.jackson.core.JsonParseException
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import config.AppConfig
import connectors.ValidationConnector
import models.common.errors.JsonValidationError
import models.common.errors.PresentationError
import models.common.errors.XmlValidationError
import models.request.MessageType
import models.responses.BusinessValidationResponse
import models.responses.JsonSchemaValidationResponse
import models.responses.XmlSchemaValidationResponse
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.NO_CONTENT
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.http
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.test.HttpClientV2Support

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class ValidationConnectorSpec
    extends AnyFreeSpec
    with HttpClientV2Support
    with Matchers
    with GuiceOneAppPerSuite
    with utils.GuiceWiremockSuite
    with ScalaFutures
    with IntegrationPatience {

  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  lazy val validationConnector: ValidationConnector = new ValidationConnector(httpClientV2, appConfig, new MetricRegistry)
  implicit lazy val ec: ExecutionContext            = app.materializer.executionContext

  "POST /message/:messageType/validation" - {

    "When validating XML" - {

      "On successful validation of schema valid XML, must return NO_CONTENT" in {

        server.stubFor(
          post(
            urlEqualTo("/transit-movements-validator/messages/IE015/validation")
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .willReturn(
              aResponse().withStatus(NO_CONTENT)
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8)) // TODO: IE015C

        whenReady(validationConnector.postXml(MessageType.DeclarationData, source)) {
          result =>
            result mustBe None
        }
      }

      "On successful validation of schema invalid XML, must return OK and a validation error" in {

        server.stubFor(
          post(
            urlEqualTo("/transit-movements-validator/messages/IE015/validation")
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.stringify(
                    Json.toJson(
                      XmlSchemaValidationResponse(NonEmptyList(XmlValidationError(1, 1, "nope"), Nil))
                    )
                  )
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8)) // TODO: IE015C

        whenReady(validationConnector.postXml(MessageType.DeclarationData, source)) {
          result =>
            result mustBe Some(XmlSchemaValidationResponse(NonEmptyList(XmlValidationError(1, 1, "nope"), Nil)))
        }
      }

      "On successful validation of business invalid XML, must return OK and a validation error" in {

        server.stubFor(
          post(
            urlEqualTo("/transit-movements-validator/messages/IE015/validation")
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  """{
                    |    "code": "BUSINESS_VALIDATION_ERROR",
                    |    "message": "business error"
                    |}
                    |""".stripMargin
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8)) // TODO: IE015C

        whenReady(validationConnector.postXml(MessageType.DeclarationData, source)) {
          result =>
            result mustBe Some(BusinessValidationResponse("business error"))
        }
      }

      "On an invalid message type, must return BAD_REQUEST and an appropriate error message" in {

        server.stubFor(
          post(
            urlEqualTo("/transit-movements-validator/messages/IE015/validation")
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(
                  Json.stringify(
                    Json.toJson(
                      PresentationError.badRequestError("Invalid message type") // The message doesn't matter here
                    )
                  )
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8)) // TODO: IE015C

        val future = validationConnector.postXml(MessageType.DeclarationData, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            val thr = result.swap.getOrElse(fail("No throwable"))
            thr mustBe a[http.UpstreamErrorResponse]
            thr.asInstanceOf[UpstreamErrorResponse].statusCode mustBe BAD_REQUEST
            Json.parse(thr.asInstanceOf[UpstreamErrorResponse].message) mustBe Json.obj(
              "code"    -> "BAD_REQUEST",
              "message" -> "Invalid message type"
            )
        }

      }

      "On an incorrect Json fragment, must return a JsResult.Exception" in {

        server.stubFor(
          post(
            urlEqualTo("/transit-movements-validator/messages/IE015/validation")
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

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8)) // TODO: IE015C

        val future = validationConnector.postXml(MessageType.DeclarationData, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.swap.getOrElse(fail("No throwable")) mustBe a[JsonParseException]
        }
      }
    }

    "When validating JSON" - {

      "On successful validation of schema valid JSON, must return NO_CONTENT" in {

        server.stubFor(
          post(
            urlEqualTo("/transit-movements-validator/messages/IE015/validation")
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
            .willReturn(
              aResponse().withStatus(NO_CONTENT)
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString("{}", StandardCharsets.UTF_8)) // TODO: IE015C

        whenReady(validationConnector.postJson(MessageType.DeclarationData, source)) {
          result =>
            result mustBe None
        }
      }

      "On successful validation of schema invalid JSON, must return OK and a validation error" in {

        server.stubFor(
          post(
            urlEqualTo("/transit-movements-validator/messages/IE015/validation")
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.stringify(
                    Json.toJson(
                      JsonSchemaValidationResponse(NonEmptyList(JsonValidationError("path", "error"), Nil))
                    )
                  )
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString("{", StandardCharsets.UTF_8)) // TODO: IE015C

        whenReady(validationConnector.postJson(MessageType.DeclarationData, source)) {
          result =>
            result mustBe Some(JsonSchemaValidationResponse(NonEmptyList(JsonValidationError("path", "error"), Nil)))
        }
      }

      "On successful validation of business invalid XML, must return OK and a validation error" in {

        server.stubFor(
          post(
            urlEqualTo("/transit-movements-validator/messages/IE015/validation")
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  """{
                    |    "code": "BUSINESS_VALIDATION_ERROR",
                    |    "message": "business error"
                    |}
                    |""".stripMargin
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8)) // TODO: IE015C

        whenReady(validationConnector.postXml(MessageType.DeclarationData, source)) {
          result =>
            result mustBe Some(BusinessValidationResponse("business error"))
        }
      }

      "On an invalid message type, must return BAD_REQUEST and an appropriate error message" in {

        server.stubFor(
          post(
            urlEqualTo("/transit-movements-validator/messages/IE015/validation")
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
            .willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(
                  Json.stringify(
                    Json.toJson(
                      PresentationError.badRequestError("Invalid message type") // The message doesn't matter here
                    )
                  )
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString("{}", StandardCharsets.UTF_8)) // TODO: IE015C

        val future = validationConnector.postJson(MessageType.DeclarationData, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            val thr = result.swap.getOrElse(fail("No throwable"))
            thr mustBe a[http.UpstreamErrorResponse]
            thr.asInstanceOf[UpstreamErrorResponse].statusCode mustBe BAD_REQUEST
            Json.parse(thr.asInstanceOf[UpstreamErrorResponse].message) mustBe Json.obj(
              "code"    -> "BAD_REQUEST",
              "message" -> "Invalid message type"
            )
        }

      }

      "On an incorrect Json fragment, must return a JsResult.Exception" in {

        server.stubFor(
          post(
            urlEqualTo("/transit-movements-validator/messages/IE015/validation")
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  "{ hello"
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString("{}", StandardCharsets.UTF_8)) // TODO: IE015C

        val future = validationConnector.postJson(MessageType.DeclarationData, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.swap.getOrElse(fail("No throwable")) mustBe a[JsonParseException]
        }
      }

    }

  }

  override protected def portConfigKey: Seq[String] =
    Seq("microservice.services.transit-movements-validator.port")
}
