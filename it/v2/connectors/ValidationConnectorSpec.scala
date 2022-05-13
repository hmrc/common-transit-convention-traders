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
import org.scalatest.BeforeAndAfterEach
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.http.Status.OK
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsArray
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.http.HeaderCarrier
import utils.TestMetrics
import v2.models.request.MessageType
import v2.models.responses.ValidationResponse

import java.nio.charset.StandardCharsets
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

class ValidationConnectorSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with utils.WiremockSuite with BeforeAndAfterEach {

  override lazy val app: Application = GuiceApplicationBuilder().build()
  val wsclient: WSClient             = app.injector.instanceOf[WSClient]
  val appConfig: AppConfig           = app.injector.instanceOf[AppConfig]

  val validationConnector: ValidationConnectorImpl = new ValidationConnectorImpl(wsclient, appConfig, new TestMetrics())

  implicit val ec: ExecutionContext = app.materializer.executionContext

  "POST /message/:messageType/validate" - {

    "On successful validation of schema valid XML, must return OK and empty validation error" in {
      server.stubFor(
        post(
          urlEqualTo("/transit-movements-validator/message/IE015C/validate") // /transit-movements-validator/message/IE015C/validate
        )
         // .withHeader(HeaderNames.ACCEPT, equalTo(ContentTypes.JSON))
         // .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
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

      val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8))

      val result = validationConnector.validate(MessageType.DepartureDeclaration, source)
      Await.result(result, 5.seconds) mustBe Json.obj("validationErrors" -> JsArray())
    }

  }

  override protected def portConfigKey: Seq[String] =
    Seq("microservice.services.transit-movements-validator.port")
}
