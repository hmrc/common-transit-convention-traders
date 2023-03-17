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

package models

import models.Binders.movementTypePathBindable
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.PathBindable
import v2.models.MovementType

class BindersSpec extends AnyFreeSpec with MockitoSugar with Matchers {

  "MovementType Path Binding" - {

    val pathBindable = implicitly[PathBindable[MovementType]]

    "must bind Departure MovementType from a URL" in {

      val result = pathBindable.bind("movementType", MovementType.Departure.urlFragment)
      result.toOption.get.urlFragment mustEqual MovementType.Departure.urlFragment
    }

    "must bind Arrival MovementType from a URL" in {

      val result = pathBindable.bind("movementType", MovementType.Arrival.urlFragment)
      result.toOption.get.urlFragment mustEqual MovementType.Arrival.urlFragment
    }

    "must return error if MovementType is not valid from a URL" in {

      val result = pathBindable.bind("movementType", "invalid")
      result mustEqual Left("movementType value invalid is not valid. expecting arrivals or departures")
    }

    "must unbind Departure MovementType" in {

      val result = pathBindable.unbind("movementType", MovementType.Departure)
      result mustEqual MovementType.Departure.toString
    }

    "must unbind Arrival MovementType" in {

      val result = pathBindable.unbind("movementType", MovementType.Arrival)
      result mustEqual MovementType.Arrival.toString
    }
  }
}
