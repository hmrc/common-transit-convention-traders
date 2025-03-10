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

package v2_1.controllers

import controllers.common.ContentTypeRouting
import controllers.common.stream.StreamingParsers
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.scalacheck.Gen
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.HttpEntity
import play.api.http.Status
import play.api.http.Status.UNSUPPORTED_MEDIA_TYPE
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import v2_1.base.TestActorSystem

class ContentTypeRoutingSpec extends AnyFreeSpec with Matchers with TestActorSystem with ScalaCheckDrivenPropertyChecks with ScalaFutures {

  class Harness(implicit val materializer: Materializer) extends ContentTypeRouting with BaseController with StreamingParsers with Logging {
    override protected def controllerComponents: ControllerComponents = stubControllerComponents()
  }

  lazy val sut = new Harness

  "ContentTypeRouting#selectContentType" - {

    Seq("application/xml", "application/xml; charset=utf-8", "application/xml; charset=UTF-8").foreach {
      ct =>
        s"returns Some(XML) for $ct" in {
          sut.selectContentType(Some(ct)) mustBe Some(ContentTypeRouting.ContentType.XML)
        }
    }

    "returns Some(JSON) for application/json" in {
      sut.selectContentType(Some("application/json")) mustBe Some(ContentTypeRouting.ContentType.JSON)
    }

    "returns Some(ContentType.None) for a None" in {
      sut.selectContentType(None) mustBe Some(ContentTypeRouting.ContentType.None)
    }

    "returns None for anything else" in forAll(Gen.alphaStr) {
      ct =>
        sut.selectContentType(Some(ct)) mustBe None
    }

  }

  "ContentTypeRouting#contentTypeRoute" - {

    val result = Result(ResponseHeader(Status.OK), HttpEntity.NoEntity)

    val pf: PartialFunction[ContentTypeRouting.ContentType, Action[?]] = {
      case ContentTypeRouting.ContentType.XML =>
        stubControllerComponents().actionBuilder.apply {
          _ => result
        }
    }

    "for a known type, return the expected result" in {
      whenReady(
        sut
          .contentTypeRoute(pf)
          .apply(FakeRequest("GET", "/", FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), Source.empty[ByteString]))
      ) {
        _ mustBe result
      }
    }

    "for an unknown type, return the expected result" in forAll(Gen.alphaNumStr) {
      ct =>
        val actual = sut
          .contentTypeRoute(pf)
          .apply(FakeRequest("GET", "/", FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> ct)), Source.empty[ByteString]))

        status(actual) mustBe UNSUPPORTED_MEDIA_TYPE
        contentAsJson(actual) mustBe Json.obj(
          "code"    -> "UNSUPPORTED_MEDIA_TYPE",
          "message" -> s"Content-type header $ct is not supported!"
        )
    }

    "for no type, return the expected result" in {
      val actual = sut
        .contentTypeRoute(pf)
        .apply(FakeRequest("GET", "/", FakeHeaders(Seq()), Source.empty[ByteString]))

      status(actual) mustBe UNSUPPORTED_MEDIA_TYPE
      contentAsJson(actual) mustBe Json.obj(
        "code"    -> "UNSUPPORTED_MEDIA_TYPE",
        "message" -> "A content-type header is required!"
      )
    }

  }

}
