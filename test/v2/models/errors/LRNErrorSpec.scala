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

package v2.models.errors

import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll
import play.api.libs.json.Json

class LRNErrorSpec extends AnyFreeSpec with Matchers with MockitoSugar {

  "LRNError" - {
    "must read from json and write to json" in forAll(Gen.stringOfN(22, Gen.alphaNumChar)) {
      string =>
        val json = Json.parse(s"""
             |{
             |  "message": "$string",
             |  "code": "$string",
             |  "lrn": "$string"
             |}
             |""".stripMargin)

        json.as[LRNError] mustBe LRNError(string, string, string)
        Json.toJson(LRNError(string, string, string)) mustBe json
    }
  }
}
