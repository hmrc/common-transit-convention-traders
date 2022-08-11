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

package routing

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.stream.testkit.NoMaterializer
import akka.util.ByteString
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.UNSUPPORTED_MEDIA_TYPE
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.contentAsString
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import v2.base.TestActorSystem
import v2.controllers.stream.StreamingParsers

import java.nio.charset.StandardCharsets
import scala.collection.immutable
import scala.concurrent.Future
import scala.xml.NodeSeq

class VersionedRoutingSpec extends AnyFreeSpec with Matchers with TestActorSystem {

  class Harness(cc: ControllerComponents)(implicit val materializer: Materializer)
      extends BackendController(cc)
      with VersionedRouting
      with StreamingParsers
      with Logging {

    def test: Action[Source[ByteString, _]] = route {
      case Some("application/vnd.hmrc.2.0+json") => actionTwo
      case Some(x) if x != MimeTypes.TEXT        => actionOne
    }

    def actionOne: Action[NodeSeq] = Action.async(parse.xml) {
      _ => Future.successful(Ok("One"))
    }

    def actionTwo: Action[NodeSeq] = Action.async(parse.xml) {
      _ => Future.successful(Ok("Two"))
    }
  }

  private def generateSource(string: String): Source[ByteString, NotUsed] =
    Source(ByteString.fromString(string, StandardCharsets.UTF_8).grouped(1024).to[immutable.Iterable])

  "VersionRouting" - {

    Seq(Some("application/vnd.hmrc.1.0+json"), Some("text/html"), Some("application/vnd.hmrc.1.0+xml"), Some("text/javascript")).foreach {
      acceptHeaderValue =>
        val acceptHeader = acceptHeaderValue
          .map(
            header => Seq(HeaderNames.ACCEPT -> header)
          )
          .getOrElse(Seq.empty)
        val departureHeaders = FakeHeaders(acceptHeader ++ Seq(HeaderNames.CONTENT_TYPE -> "application/xml"))
        val withString = acceptHeaderValue
          .getOrElse("nothing")
        s"with accept header set to $withString" - {

          "must call correct action" in {
            val cc  = stubControllerComponents()
            val sut = new Harness(cc)

            val request = FakeRequest("GET", "/", departureHeaders, generateSource("<test>test</test>"))

            val result = sut.test()(request)
            contentAsString(result)(defaultAwaitTimeout, NoMaterializer) mustBe "One"
          }
        }
    }

    "with accept header set to application/vnd.hmrc.2.0+json (version two)" - {

      val departureHeaders = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "application/xml"))

      "must call correct action" in {

        val cc  = stubControllerComponents()
        val sut = new Harness(cc)

        val request = FakeRequest("GET", "/", departureHeaders, generateSource("<test>test</test>"))

        val result = sut.test()(request)
        contentAsString(result)(defaultAwaitTimeout, NoMaterializer) mustBe "Two"

      }
    }

    "with invalid accept header" - {

      "when not set" in {
        val departureHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml"))

        val cc  = stubControllerComponents()
        val sut = new Harness(cc)

        val request = FakeRequest("GET", "/", departureHeaders, generateSource("<test>test</test>"))

        val result = sut.test()(request)
        status(result) mustBe UNSUPPORTED_MEDIA_TYPE
        Json.parse(contentAsString(result)) mustBe Json.obj(
          "code"    -> "UNSUPPORTED_MEDIA_TYPE",
          "message" -> "An accept header is required!"
        )

      }

      "when set to text/plain" in {
        val departureHeaders = FakeHeaders(Seq(HeaderNames.ACCEPT -> MimeTypes.TEXT, HeaderNames.CONTENT_TYPE -> "application/xml"))

        val cc  = stubControllerComponents()
        val sut = new Harness(cc)

        val request = FakeRequest("GET", "/", departureHeaders, generateSource("<test>test</test>"))

        val result = sut.test()(request)
        status(result) mustBe UNSUPPORTED_MEDIA_TYPE
        Json.parse(contentAsString(result)) mustBe Json.obj(
          "code"    -> "UNSUPPORTED_MEDIA_TYPE",
          "message" -> "Accept header text/plain is not supported!"
        )

      }
    }

  }

  "Binding Failure Error Action" - {
    "when a failure is requested, return an appropriate BAD_REQUEST" in {

      val sut = new Harness(stubControllerComponents())

      val request = FakeRequest("GET", "/", FakeHeaders(), generateSource("<test>test</test>"))
      val action  = sut.bindingFailureAction("failed")
      val result  = action(request)

      status(result) mustBe BAD_REQUEST
      contentAsJson(result) mustBe Json.obj(
        "code"       -> "BAD_REQUEST",
        "statusCode" -> 400,
        "message"    -> "failed"
      )
    }
  }

}
