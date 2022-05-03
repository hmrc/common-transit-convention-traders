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

package controllers.stream

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.stream.testkit.NoMaterializer
import akka.util.ByteString
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Logging
import play.api.http.HeaderNames
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Action
import play.api.mvc.ControllerComponents
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsString
import play.api.test.Helpers.defaultAwaitTimeout
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.nio.charset.StandardCharsets
import scala.collection.immutable
import scala.concurrent.Future
import scala.xml.NodeSeq

class VersionedRoutingSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite {

  class Harness(cc: ControllerComponents)(implicit val materializer: Materializer)
      extends BackendController(cc)
      with VersionedRouting
      with StreamingParsers
      with Logging {

    def test: Action[Source[ByteString, _]] = route {
      case Some("application/vnd.hmrc.2.0+json") => actionTwo
      case _                                     => actionOne
    }

    def actionOne: Action[NodeSeq] = Action.async(parse.xml) {
      _ => Future.successful(Ok("One"))
    }

    def actionTwo: Action[NodeSeq] = Action.async(parse.xml) {
      _ => Future.successful(Ok("Two"))
    }
  }

  override lazy val app = GuiceApplicationBuilder()
    .build()
  implicit lazy val materializer: Materializer = app.materializer

  private def generateSource(string: String): Source[ByteString, NotUsed] =
    Source(ByteString.fromString(string, StandardCharsets.UTF_8).grouped(1024).to[immutable.Iterable])

  "VersionRouting" - {

    Seq(None, Some("application/vnd.hmrc.1.0+json"), Some("text/html"), Some("application/vnd.hmrc.1.0+xml"), Some("text/javascript")).foreach {
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
            val cc  = app.injector.instanceOf[ControllerComponents]
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

        val cc  = app.injector.instanceOf[ControllerComponents]
        val sut = new Harness(cc)

        val request = FakeRequest("GET", "/", departureHeaders, generateSource("<test>test</test>"))

        val result = sut.test()(request)
        contentAsString(result)(defaultAwaitTimeout, NoMaterializer) mustBe "Two"

      }
    }

  }

}
