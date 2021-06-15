/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.libs.ws.WSClient

import java.util.UUID

class CustomJsonErrorHandlerSpec extends AnyFreeSpec with Matchers with GuiceOneServerPerSuite with utils.WiremockSuite with ScalaFutures with IntegrationPatience {
  override protected def portConfigKey: Seq[String] = Seq.empty

  private val httpClient = app.injector.instanceOf[WSClient]
  private val requestId  = UUID.randomUUID().toString

  "CustomJsonErrorHandler" - {
    "return error message when a bad request is sent" in {
      val response = httpClient
        .url(s"http://localhost:$port/movements/departures?updatedSince=15-06-2021T14:55:55.000Z")
        .withHttpHeaders("channel" -> "api", "X-Request-ID" -> requestId)
        .get()
        .futureValue

      response.status shouldBe BAD_REQUEST

      response.json shouldBe Json.obj(
        "statusCode" -> BAD_REQUEST,
        "message" -> "Cannot parse parameter updatedSince as a valid ISO 8601 timestamp, e.g. 2015-09-08T01:55:28+00:00"
      )
    }
  }
}
