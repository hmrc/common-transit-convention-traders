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

import controllers.routes
import models.domain.Arrival
import utils.CallOps._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class ResponseArrivalSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach {

  "ResponseArrival" - {
    "must have valid public location" in {
      val arrival = Arrival(3, "loc", "messageLoc", "mrn", "status", LocalDateTime.now(), LocalDateTime.now())

      val result = ResponseArrival(arrival)

      result.arrival mustBe routes.ArrivalMovementController.getArrival("3").urlWithContext
    }

    "must have valid public messages location" in {
      val arrival = Arrival(3, "loc", "messageLoc", "mrn", "status", LocalDateTime.now(), LocalDateTime.now())

      val result = ResponseArrival(arrival)

      result.messages mustBe routes.ArrivalMessagesController.getArrivalMessages("3").urlWithContext
    }
  }

}
