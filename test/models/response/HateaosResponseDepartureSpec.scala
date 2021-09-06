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

import models.domain.Departure
import models.domain.DepartureId
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json

class HateoasResponseDepartureSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with BeforeAndAfterEach {

  "HateoasResponseDeparture" - {
    "must generate correct message structure" in {
      val departure = Departure(
        DepartureId(3),
        "loc",
        "messageLoc",
        Some("mrn"),
        LocalDateTime.of(2020, 10, 10, 10, 10, 10),
        LocalDateTime.of(2020, 12, 12, 12, 12, 12)
      )

      val result = HateoasResponseDeparture(departure)

      val expectedJson = Json.parse("""
          |{
          |  "id": "3",
          |  "created": "2020-10-10T10:10:10",
          |  "updated": "2020-12-12T12:12:12",
          |  "movementReferenceNumber": "mrn",
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/departures/3"
          |    },
          |    "messages": {
          |      "href": "/customs/transits/movements/departures/3/messages"
          |    }
          |  }
          |}
          |""".stripMargin)

      expectedJson mustEqual Json.toJson(result)
    }
  }
}
