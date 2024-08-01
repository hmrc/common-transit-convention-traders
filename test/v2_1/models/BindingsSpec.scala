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

package v2_1.models

import cats.implicits.catsSyntaxEitherId
import models.common.MovementType
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.mvc.PathBindable

class BindingsSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  val testBinding: PathBindable[String] = Bindings.hexBinding(identity[String], identity[String])

  val validHexString: Gen[String] = Gen.listOfN(16, Gen.hexChar).map(_.mkString.toLowerCase)

  val tooShortHexString: Gen[String] = Gen
    .chooseNum(1, 15)
    .flatMap(
      x => Gen.listOfN(x, Gen.hexChar)
    )
    .map(_.mkString.toLowerCase)

  val tooLongHexString: Gen[String] = Gen
    .chooseNum(17, 30)
    .flatMap(
      x => Gen.listOfN(x, Gen.hexChar)
    )
    .map(_.mkString.toLowerCase)
  // Last character ensures it's not a hex string
  val notAHexString: Gen[String] = Gen.listOfN(15, Gen.alphaNumChar).map(_.mkString.toLowerCase).map(_ + "x")

  "hex binding" - {

    "bind" - {
      "a 16 character lowercase hex string must be accepted" in {
        val value = validHexString.sample.getOrElse("1234567890123456")
        testBinding.bind("test", value) mustBe Right(value)
      }

      "an invalid hex string must not be accepted" in Seq(tooShortHexString, tooLongHexString, notAHexString).foreach {
        gen =>
          val value = gen.sample.get
          testBinding.bind("test", value) mustBe Left(s"test: Value $value is not a 16 character hexadecimal string")
      }
    }

    "unbind" - {
      "should correctly unbind the value" in {
        val value = validHexString.sample.getOrElse("1234567890123456")
        testBinding.unbind("test", value) mustBe value
      }
    }

  }

  "MovementType Path Binding" - {

    val pathBindable = Bindings.movementTypePathBindable

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
      result mustEqual "movementType value invalid is not valid. expecting arrivals or departures".asLeft
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
