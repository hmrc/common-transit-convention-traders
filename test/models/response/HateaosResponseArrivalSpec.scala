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
import models.domain.Arrival
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json

class HateoasResponseArrivalSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach {

  "HateoasResponseArrival" - {
    "must generate correct message structure" in {
      val arrival = Arrival(3,
        "loc",
        "messageLoc",
        "mrn",
        "status",
        LocalDateTime.of(2020, 10, 10, 10, 10, 10),
        LocalDateTime.of(2020, 12, 12, 12, 12, 12)
      )

      val expectedJson = Json.parse(
        """
          |{
          |  "id": "3",
          |  "created": "2020-10-10T10:10:10",
          |  "updated": "2020-12-12T12:12:12",
          |  "movementReferenceNumber": "mrn",
          |  "status": "status",
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/arrivals/3"
          |    },
          |    "messages": {
          |      "href": "/customs/transits/movements/arrivals/3/messages"
          |    }
          |  }
          |}""".stripMargin)

      val result = HateoasResponseArrival(arrival)

      expectedJson mustEqual Json.toJson(result)
    }
  }
}
