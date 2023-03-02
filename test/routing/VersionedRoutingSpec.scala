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

package routing

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.HttpVerbs
import play.api.http.MimeTypes
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.NOT_ACCEPTABLE
import play.api.http.Status.UNSUPPORTED_MEDIA_TYPE
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.ControllerComponents
import play.api.test.DefaultAwaitTimeout
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.contentAsString
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import v2.base.TestActorSystem
import v2.controllers.stream.StreamingParsers

import java.nio.charset.StandardCharsets
import scala.concurrent.Future
import scala.xml.NodeSeq

class VersionedRoutingSpec
    extends AnyFreeSpec
    with Matchers
    with TestActorSystem
    with ScalaCheckDrivenPropertyChecks
    with OptionValues
    with DefaultAwaitTimeout {

  class Harness(cc: ControllerComponents)(implicit val materializer: Materializer)
      extends BackendController(cc)
      with VersionedRouting
      with StreamingParsers
      with Logging {

    def testWithContent: Action[Source[ByteString, _]] = route {
      case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) => contentActionTwo
      case Some(x) if x != MimeTypes.TEXT                           => contentActionOne
    }

    def contentActionOne: Action[NodeSeq] = Action.async(parse.xml) {
      _ => Future.successful(Ok("One"))
    }

    def contentActionTwo: Action[NodeSeq] = Action.async(parse.xml) {
      _ => Future.successful(Ok("Two"))
    }

    def testWithoutContent: Action[Source[ByteString, _]] = route {
      case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_PATTERN()) => actionTwo
      case Some(x) if x != MimeTypes.TEXT                           => actionOne
    }

    def actionOne: Action[AnyContent] = Action {
      request =>
        request.headers.get(CONTENT_TYPE) match {
          case Some(x) => UnsupportedMediaType(s"Content type was set: $x")
          case None    => Ok("One")
        }
    }

    def actionTwo: Action[AnyContent] = Action {
      request =>
        request.headers.get(CONTENT_TYPE) match {
          case Some(x) => UnsupportedMediaType(s"Content type was set: $x")
          case None    => Ok("Two")
        }
    }
  }

  private def generateSource(string: String): Source[ByteString, NotUsed] =
    Source.fromIterator(
      () => ByteString.fromString(string, StandardCharsets.UTF_8).grouped(1024)
    )

  "VersionRouting" - {

    val methodsWithBody = Seq(
      HttpVerbs.POST,
      HttpVerbs.PUT,
      HttpVerbs.PATCH
    )

    val methodsWithoutBody = Seq(
      HttpVerbs.GET,
      HttpVerbs.DELETE,
      HttpVerbs.HEAD,
      HttpVerbs.OPTIONS
    )

    val headers =
      Seq(Some("application/vnd.hmrc.1.0+json"), Some("text/html"), Some("application/vnd.hmrc.1.0+xml"), Some("text/javascript"))

    headers.foreach {
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

          "must call correct action with a body" - methodsWithBody.foreach {
            method =>
              s"for method $method" in {
                val cc  = stubControllerComponents()
                val sut = new Harness(cc)

                // I know this shouldn't work for get, but it should drain it and work
                val request = FakeRequest(method, "/", departureHeaders, generateSource("<test>test</test>"))

                val result = sut.testWithContent()(request)
                contentAsString(result) mustBe "One"
              }
          }

          "must call correct action without a body" - methodsWithoutBody.foreach {
            method =>
              Seq(Some(MimeTypes.XML), Some(MimeTypes.JSON), None).foreach {
                contentType =>
                  val headersWithoutBody = FakeHeaders(
                    acceptHeader ++ contentType
                      .map(
                        x => Seq(HeaderNames.CONTENT_TYPE -> x)
                      )
                      .getOrElse(Seq.empty)
                  )
                  s"for method $method with content type $contentType" in {
                    val cc      = stubControllerComponents()
                    val sut     = new Harness(cc)
                    val request = FakeRequest(method, "/", headersWithoutBody, AnyContentAsEmpty)

                    val result = sut.testWithoutContent()(request)
                    contentAsString(result) mustBe "One"
                  }
              }
          }

          "must cause failures if we send an HTTP method that doesn't support request bodies" - methodsWithoutBody.foreach {
            method =>
              s"for method $method" in {
                val cc  = stubControllerComponents()
                val sut = new Harness(cc)

                val request = FakeRequest(method, "/", departureHeaders, generateSource("<test>test</test>"))

                val result = sut.testWithContent()(request)
                status(result) mustBe UNSUPPORTED_MEDIA_TYPE
              }
          }
        }
    }

    "with accept header set to application/vnd.hmrc.2.0+json (version two)" - {

      val departureHeaders = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "application/xml"))

      "must call correct action without body" in {

        val cc  = stubControllerComponents()
        val sut = new Harness(cc)

        val request = FakeRequest(HttpVerbs.GET, "/", departureHeaders, AnyContentAsEmpty)

        val result = sut.testWithoutContent()(request)
        contentAsString(result) mustBe "Two"

      }

      "must call correct action with body" in {

        val cc  = stubControllerComponents()
        val sut = new Harness(cc)

        val request = FakeRequest(HttpVerbs.POST, "/", departureHeaders, generateSource("<test>test</test>"))

        val result = sut.testWithContent()(request)
        contentAsString(result) mustBe "Two"

      }
    }

    "with invalid accept header" - {

      "when not set" in {
        val departureHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml"))

        val cc  = stubControllerComponents()
        val sut = new Harness(cc)

        val request = FakeRequest("GET", "/", departureHeaders, generateSource("<test>test</test>"))

        val result = sut.testWithContent()(request)
        status(result) mustBe NOT_ACCEPTABLE
        Json.parse(contentAsString(result)) mustBe Json.obj(
          "code"    -> "NOT_ACCEPTABLE",
          "message" -> "The Accept header is missing or invalid."
        )

      }

      "when set to text/plain" in {
        val departureHeaders = FakeHeaders(Seq(HeaderNames.ACCEPT -> MimeTypes.TEXT, HeaderNames.CONTENT_TYPE -> "application/xml"))

        val cc  = stubControllerComponents()
        val sut = new Harness(cc)

        val request = FakeRequest(HttpVerbs.POST, "/", departureHeaders, generateSource("<test>test</test>"))

        val result = sut.testWithContent()(request)
        status(result) mustBe NOT_ACCEPTABLE
        Json.parse(contentAsString(result)) mustBe Json.obj(
          "code"    -> "NOT_ACCEPTABLE",
          "message" -> "The Accept header is missing or invalid."
        )

      }
    }

  }

  "with accept header set to version two" - {
    val headers =
      Seq(
        Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON),
        Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML),
        Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN)
      )

    headers.foreach {
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

          "must call correct action without body" in {

            val cc  = stubControllerComponents()
            val sut = new Harness(cc)

            val request = FakeRequest(HttpVerbs.GET, "/", departureHeaders, AnyContentAsEmpty)

            val result = sut.testWithoutContent()(request)
            contentAsString(result) mustBe "Two"

          }

          "must call correct action with body" in {

            val cc  = stubControllerComponents()
            val sut = new Harness(cc)

            val request = FakeRequest(HttpVerbs.POST, "/", departureHeaders, generateSource("<test>test</test>"))

            val result = sut.testWithContent()(request)
            contentAsString(result) mustBe "Two"

          }
        }
    }
  }

  "Binding Failure Error Action" - {
    "when a failure is requested, return an appropriate BAD_REQUEST" in {

      val sut = new Harness(stubControllerComponents())

      val request = FakeRequest(HttpVerbs.POST, "/", FakeHeaders(), generateSource("<test>test</test>"))
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
