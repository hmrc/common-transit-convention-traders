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

import models.domain.{Arrival, ArrivalWithMessages, MovementMessage}
import org.scalatest.{BeforeAndAfterEach, FreeSpec, MustMatchers, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

class ResponseArrivalWithMessagesSpec extends FreeSpec with MustMatchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar with BeforeAndAfterEach {

  "ResponseArrivalWithMessages" - {
    "must have valid public location" in {
      val arrival = ArrivalWithMessages(3, "loc", "messageLoc", "mrn", "status", LocalDateTime.now(), LocalDateTime.now(), Nil)

      val result = ResponseArrivalWithMessages(arrival)

      result.arrival mustBe "/movements/arrivals/3"
    }

    "messages must have valid public locations" in {
      val arrival = ArrivalWithMessages(3, "loc", "messageLoc", "mrn", "status", LocalDateTime.now(), LocalDateTime.now(), Seq(MovementMessage("/3", LocalDateTime.now(), "type", <test>default</test>)))

      val result = ResponseArrivalWithMessages(arrival)

      result.messages.head.message mustBe "/movements/arrivals/3/messages/3"
    }
  }

}