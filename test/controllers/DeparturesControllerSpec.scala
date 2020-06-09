/*
 * Copyright 2020 HM Revenue & Customs
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

import connectors.DeparturesConnector
import controllers.actions.{AuthAction, FakeAuthAction}
import data.TestXml
import org.scalatest.{BeforeAndAfterEach, FreeSpec, MustMatchers, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.bind
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, when}
import play.api.http.HeaderNames
import play.api.test.{FakeHeaders, FakeRequest}
import uk.gov.hmrc.http.HttpResponse
import play.api.test.Helpers.{headers, _}
import utils.CallOps._

import scala.concurrent.Future

class DeparturesControllerSpec extends FreeSpec with MustMatchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach with TestXml {
  private val mockDepartureConnector: DeparturesConnector = mock[DeparturesConnector]

  override lazy val app = GuiceApplicationBuilder()
    .overrides(bind[AuthAction].to[FakeAuthAction])
    .overrides(bind[DeparturesConnector].toInstance(mockDepartureConnector))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockDepartureConnector)
  }

  def fakeRequestDepartures[A](method: String, headers: FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")), uri: String = routes.DeparturesController.submitDeclaration().url, body: A) =
    FakeRequest(method = method, uri = uri, headers, body = body)

  "POST /movements/depatures" - {

    "must return Accepted when successful" in {
      when(mockDepartureConnector.post(any())(any(), any(), any()))
        .thenReturn(Future.successful(HttpResponse(responseStatus = NO_CONTENT, responseJson = None, responseHeaders = Map(LOCATION -> Seq("/transit-movements-trader-at-departure/movements/depatures/123")), responseString = None) ))

      val request = fakeRequestDepartures(method = "POST", body = CC015B)
      val result = route(app, request).value

      status(result) mustBe ACCEPTED
      headers(result) must contain (LOCATION -> routes.DeparturesController.getDeparture("123").urlWithContext)
    }

    "must return InternalServerError when unsuccessful" in {}

    "must return InternalServerError when no location in downstream response header" in {}

    "must return InternalServerError when invalid Location value in downstream response header" ignore {}

    "must escape departureId in location response header" in {}

    "must exclude query string if present in downstream location header" in {}

    "must return UnsupportedMediaType when Content-Type is JSON" in {}

    "must return UnsupportedMediaType when no Content-Type specified" in {}

    "must return UnsupportedMediaType when empty XML payload is sent" in {}
  }

}
