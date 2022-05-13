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
import play.api.http.Status.OK
import play.api.libs.json.JsArray
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.http
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import utils.TestMetrics
import v2.models.errors.BaseError
import v2.models.request.MessageType
import v2.models.responses.ValidationResponse

import java.nio.charset.StandardCharsets
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class ValidationConnectorSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with utils.WiremockSuite with ScalaFutures with IntegrationPatience {

  lazy val wsclient: WSClient   = app.injector.instanceOf[WSClient]
  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  lazy val validationConnector: ValidationConnectorImpl = new ValidationConnectorImpl(wsclient, appConfig, new TestMetrics())
  implicit lazy val ec: ExecutionContext                = app.materializer.executionContext

  "POST /message/:messageType/validate" - {

    "On successful validation of schema valid XML, must return OK and empty validation error" in {

      server.stubFor(
        post(
          urlEqualTo("/transit-movements-validator/message/IE015C/validate") // /transit-movements-validator/message/IE015C/validate
        )
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json.stringify(
                  Json.toJson(
                    ValidationResponse(Seq())
                  )
                )
              )
          )
      )

      implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

      val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8)) // TODO: IE015C

      val result = validationConnector.validate(MessageType.DepartureDeclaration, source)
      Await.result(result, 5.seconds) mustBe Json.obj("validationErrors" -> JsArray())
    }

    "On successful validation of schema invalid XML, must return OK and a validation error" in {

      server.stubFor(
        post(
          urlEqualTo("/transit-movements-validator/message/IE015C/validate") // /transit-movements-validator/message/IE015C/validate
        )
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json.stringify(
                  Json.toJson(
                    ValidationResponse(Seq("nope"))
                  )
                )
              )
          )
      )

      implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

      val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8)) // TODO: IE015C

      val result = validationConnector.validate(MessageType.DepartureDeclaration, source)
      Await.result(result, 5.seconds) mustBe Json.obj("validationErrors" -> Seq("nope")) // TODO
    }

    "On invalid XML, must return BAD_REQUEST and an appropriate error message" in {

      server.stubFor(
        post(
          urlEqualTo("/transit-movements-validator/message/IE015C/validate") // /transit-movements-validator/message/IE015C/validate
        )
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withBody(
                Json.stringify(
                  Json.toJson(
                    BaseError.badRequestError("Invalid XML") // TODO
                  )
                )
              )
          )
      )

      implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

      val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8)) // TODO: IE015C

      val result: Future[JsValue] = Await.ready(validationConnector.validate(MessageType.DepartureDeclaration, source), 5.seconds)

      val thr = result.eitherValue.get.left.get
      thr mustBe a[http.UpstreamErrorResponse]
      thr.asInstanceOf[UpstreamErrorResponse].statusCode mustBe BAD_REQUEST
      Json.parse(thr.asInstanceOf[UpstreamErrorResponse].message) mustBe Json.obj("code" -> "BAD_REQUEST", "message" -> "Invalid XML")
    }

  }

  override protected def portConfigKey: Seq[String] =
    Seq("microservice.services.transit-movements-validator.port")
}
