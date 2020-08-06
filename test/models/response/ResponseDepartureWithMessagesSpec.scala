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
import utils.CallOps._
import controllers.routes
import models.domain.{DepartureWithMessages, MovementMessage}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class ResponseDepartureWithMessagesSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite {

  "ResponseDepartureWithMessages" - {
    "must have a valid public location" in {
      val departure = DepartureWithMessages(3, "loc", "messageLoc", Some("mrn"), "ref", "status", LocalDateTime.now(), LocalDateTime.now(), Nil)

      val result = ResponseDepartureWithMessages(departure)

      result.departure mustBe routes.DeparturesController.getDeparture("3").urlWithContext
    }

    "messages must have valid public locations" in {
      val departure = DepartureWithMessages(3, "loc", "messageLoc", Some("mrn"), "ref", "status", LocalDateTime.now(), LocalDateTime.now(), Seq(MovementMessage("/3", LocalDateTime.now(), "type", <test>default</test>)))

      val result = ResponseDepartureWithMessages(departure)

      result.messages.head.message mustBe routes.DepartureMessagesController.getDepartureMessage("3", "3").urlWithContext
    }
  }

}
