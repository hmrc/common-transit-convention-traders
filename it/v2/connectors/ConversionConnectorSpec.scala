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

import akka.stream.scaladsl.Sink
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
import play.api.libs.json.Json
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.http
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.test.HttpClientV2Support
import utils.TestMetrics
import v2.base.TestActorSystem
import v2.models.errors.PresentationError
import v2.models.request.MessageType

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.control.NonFatal

class ConversionConnectorSpec
    extends AnyFreeSpec
    with HttpClientV2Support
    with Matchers
    with GuiceOneAppPerSuite
    with utils.GuiceWiremockSuite
    with ScalaFutures
    with IntegrationPatience
    with TestActorSystem {

  lazy val appConfig: AppConfig = app.injector.instanceOf[AppConfig]

  lazy val conversionConnector: ConversionConnectorImpl = new ConversionConnectorImpl(httpClientV2, appConfig, new TestMetrics())
  implicit lazy val ec: ExecutionContext                = app.materializer.executionContext

  "POST /messages/:messageType" - {

    val jsonSource  = Source.single(ByteString(Json.stringify(Json.obj("CC015" -> ""))))
    val xmlResponse = <CC015></CC015>.mkString
    "On successful conversion of JSON to XML, must return OK" in {
      server.stubFor(
        post(
          urlEqualTo("/transit-movements-converter/messages/IE015")
        )
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
          .willReturn(
            aResponse().withStatus(OK).withBody(xmlResponse)
          )
      )

      implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.XML))

      whenReady(conversionConnector.post(MessageType.DepartureDeclaration, jsonSource)) {
        result =>
          result
            .fold("")(
              (curStr, newStr) => curStr + newStr.utf8String
            )
            .runWith(Sink.head[String])
            .onComplete {
              case Success(value) => value mustEqual xmlResponse
              case Failure(_)     => fail("Unexpected conversion failure")
            }
      }
    }

    "On an conversion error, must return BAD_REQUEST and an appropriate error message" in {

      server.stubFor(
        post(
          urlEqualTo("/transit-movements-converter/messages/IE015")
        )
          .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.JSON))
          .willReturn(
            aResponse()
              .withStatus(BAD_REQUEST)
              .withBody(
                Json.stringify(
                  Json.toJson(
                    PresentationError.badRequestError("Conversion error")
                  )
                )
              )
          )
      )

      implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.XML))

      val future = conversionConnector.post(MessageType.DepartureDeclaration, jsonSource).map(Right(_)).recover {
        case NonFatal(e) => Left(e)
      }

      whenReady(future) {
        result =>
          val thr = result.left.get
          thr mustBe a[http.UpstreamErrorResponse]
          thr.asInstanceOf[UpstreamErrorResponse].statusCode mustBe BAD_REQUEST
          Json.parse(thr.asInstanceOf[UpstreamErrorResponse].message) mustBe Json.obj("code" -> "BAD_REQUEST", "message" -> "Conversion error")
      }

    }

  }

  override protected def portConfigKey: Seq[String] =
    Seq("microservice.services.transit-movements-converter.port")

}
