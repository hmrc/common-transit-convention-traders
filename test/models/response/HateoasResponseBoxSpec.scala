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

package models.response

import java.time.LocalDateTime

import models.{Box, BoxId}
import models.domain.{Departure, DepartureId}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json

class HateoasResponseBoxSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with BeforeAndAfterEach {

  "HateoasResponseBox" - {
    "must generate correct message structure" in {
      val box = Box(BoxId("testId"), "testName")

      val result = HateoasResponseBox(box)

      val expectedJson = Json.parse("""
          |{
          |  "boxId": "testId",
          |  "boxName": "testName",
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/push-pull-notifications/box"
          |    }
          |  }
          |}
          |""".stripMargin)

      expectedJson mustEqual Json.toJson(result)
    }
  }
}
