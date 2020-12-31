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

package models.response

import java.time.LocalDateTime
import models.domain.MovementMessage
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json

class HateaosArrivalResponseMessageSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach {

  "HateaosArrivalResponseMessage" - {
    "must have valid message structure" in {
      val message = MovementMessage("/customs/transits/movements/arrivals/1/messages/1",
        LocalDateTime.of(2020, 10, 10, 10, 10, 10),
        "type",
        <test>default</test>)

      val expectedJson = Json.parse(
        """
          |{
          |  "_links": {
          |    "self": {
          |      "href": "/customs/transits/movements/arrivals/1/messages/1"
          |    },
          |    "arrival": {
          |      "href": "/customs/transits/movements/arrivals/1"
          |    }
          |  },
          |  "arrivalId": "1",
          |  "messageId": "1",
          |  "received": "2020-10-10T10:10:10",
          |  "messageType": "type",
          |  "body": "<test>default</test>"
          |}""".stripMargin)

      val result = HateaosArrivalResponseMessage("1", "1", message)

      expectedJson mustEqual Json.toJson(result)
    }
  }
}
