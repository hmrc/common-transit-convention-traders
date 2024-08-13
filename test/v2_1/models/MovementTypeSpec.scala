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

import models.common.MovementType
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.JsError
import play.api.libs.json.JsString
import play.api.libs.json.JsSuccess

class MovementTypeSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  "MovementType reads" - {
    MovementType.values.foreach {
      movementType =>
        s"should read a JsString of ${movementType.movementType} as a valid movement type" in {
          MovementType.movementTypeReads.reads(JsString(movementType.movementType)) mustBe JsSuccess(movementType)
        }
    }

    "should fail for any other string" in forAll(Gen.alphaNumStr) {
      string =>
        MovementType.movementTypeReads.reads(JsString(string)) mustBe JsError()
    }
  }

  "MovementType writes" - {
    MovementType.values.foreach {
      movementType =>
        s"should write a JsString of ${movementType.movementType} from a valid movement type" in {
          MovementType.movementTypeWrites.writes(movementType) mustBe JsString(movementType.movementType)
        }
    }
  }

}
