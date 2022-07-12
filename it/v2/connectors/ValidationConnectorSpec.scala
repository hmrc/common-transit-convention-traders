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
import cats.data.NonEmptyList
import com.fasterxml.jackson.core.JsonParseException
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import config.AppConfig
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
import uk.gov.hmrc.play.http.test.ResponseMatchers
import utils.TestMetrics
import v2.models.errors.PresentationError
import v2.models.errors.ValidationError
import v2.models.request.MessageType
import v2.models.responses.ValidationResponse

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

  lazy val validationConnector: ValidationConnectorImpl = new ValidationConnectorImpl(httpClientV2, appConfig, new TestMetrics())
  implicit lazy val ec: ExecutionContext                = app.materializer.executionContext

  "POST /message/:messageType/validation" - {

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

      whenReady(validationConnector.validate(MessageType.DepartureDeclaration, source)) {
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
                    ValidationResponse(NonEmptyList(ValidationError(1, 1, "nope"), Nil))
                  )
                )
              )
          )
      )

      implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

      val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8)) // TODO: IE015C

      whenReady(validationConnector.validate(MessageType.DepartureDeclaration, source)) {
        result =>
          result mustBe Some(ValidationResponse(NonEmptyList(ValidationError(1, 1, "nope"), Nil)))
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

      val future = validationConnector.validate(MessageType.DepartureDeclaration, source).map(Right(_)).recover {
        case NonFatal(e) => Left(e)
      }

      whenReady(future) {
        result =>
          val thr = result.left.get
          thr mustBe a[http.UpstreamErrorResponse]
          thr.asInstanceOf[UpstreamErrorResponse].statusCode mustBe BAD_REQUEST
          Json.parse(thr.asInstanceOf[UpstreamErrorResponse].message) mustBe Json.obj("code" -> "BAD_REQUEST", "message" -> "Invalid message type")
      }

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

    val future = validationConnector.validate(MessageType.DepartureDeclaration, source).map(Right(_)).recover {
      case NonFatal(e) => Left(e)
    }

    whenReady(future) {
      result =>
        result.left.get mustBe a[JsonParseException]
    }
  }

  override protected def portConfigKey: Seq[String] =
    Seq("microservice.services.transit-movements-validator.port")
}
