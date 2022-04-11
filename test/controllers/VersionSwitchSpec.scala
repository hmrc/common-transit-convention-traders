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

package controllers

import akka.stream.testkit.NoMaterializer
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.Status.UNSUPPORTED_MEDIA_TYPE
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{Action, ControllerComponents}
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status}
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.Future
import scala.xml.NodeSeq

class VersionSwitchSpec extends AnyFreeSpec
  with Matchers
  with GuiceOneAppPerSuite {

  class Harness(cc: ControllerComponents) extends BackendController(cc) with VersionSwitch with Logging {
    def test: Action[NodeSeq] = versionSwitch(actionOne, actionTwo)
    def actionOne: Action[NodeSeq] = Action.async(parse.xml) { _ => Future.successful(Ok("One")) }
    def actionTwo: Action[NodeSeq] = Action.async(parse.xml) { _ => Future.successful(Ok("Two")) }
  }

  override lazy val app = GuiceApplicationBuilder()
    .build()

  "VersionSwitch" - {

    Seq(None, Some("application/vnd.hmrc.1.0+json")).foreach {
      acceptHeaderValue =>
        val acceptHeader = acceptHeaderValue.map(header => Seq(HeaderNames.ACCEPT -> header)).getOrElse(Seq.empty)
        val departureHeaders =  FakeHeaders(acceptHeader ++ Seq(HeaderNames.CONTENT_TYPE -> "application/xml"))
        val withString = acceptHeaderValue
          .getOrElse("nothing")
        s"with accept header set to $withString" - {

          "must call correct action" in {
            val cc = app.injector.instanceOf[ControllerComponents]
            val sut = new Harness(cc)

            val request = FakeRequest("GET","/", departureHeaders, <test>test</test>)

            val result = sut.test()(request)
            contentAsString(result)(defaultAwaitTimeout, NoMaterializer) mustBe "One"
          }
        }
    }

    "with accept header set to application/vnd.hmrc.2.0+json (version two)" - {

      val departureHeaders =  FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "application/xml"))

      "must call correct action" in {

        val cc = app.injector.instanceOf[ControllerComponents]
        val sut = new Harness(cc)

        val request = FakeRequest("GET","/", departureHeaders, <test>test</test>)

        val result = sut.test()(request)
        contentAsString(result)(defaultAwaitTimeout, NoMaterializer) mustBe "Two"

      }
    }

    "with accept header set to something invalid" - {

      val departureHeaders =  FakeHeaders(Seq(HeaderNames.ACCEPT -> "invalid", HeaderNames.CONTENT_TYPE -> "application/xml"))

      "must return unsupported media type when successful" in {
        val cc = app.injector.instanceOf[ControllerComponents]
        val sut = new Harness(cc)

        val request = FakeRequest("GET","/", departureHeaders, <test>test</test>)

        val result = sut.test()(request)
        status(result)(defaultAwaitTimeout) mustBe UNSUPPORTED_MEDIA_TYPE
      }
    }




  }

}
