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

package v2.models

import cats.data.NonEmptyList
import models.common.errors.JsonValidationError
import models.common.errors.XmlValidationError
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import v2.models.responses._

class ValidationResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  "XMLValidationErrorResponse#reads" - {

    "for a business error, will return a BusinessResponse" in forAll(Gen.alphaNumStr) {
      message =>
        val response =
          s"""{
            |    "code": "BUSINESS_VALIDATION_ERROR",
            |    "message": "$message"
            |}
            |""".stripMargin

        Json.parse(response).as[XmlValidationErrorResponse] mustBe BusinessValidationResponse(message)
    }

    "for a schema error, will return a XMLSchemaValidationResponse" in forAll(Gen.alphaNumStr) {
      message =>
        val response =
          s"""{
             |    "validationErrors": [
             |      {
             |         "lineNumber": 1,
             |         "columnNumber": 2,
             |         "message": "$message"
             |      }
             |    ]
             |}
             |""".stripMargin

        Json.parse(response).as[XmlValidationErrorResponse] mustBe XmlSchemaValidationResponse(
          NonEmptyList(
            XmlValidationError(1, 2, message),
            Nil
          )
        )
    }

  }

  "JsonValidationErrorResponse#reads" - {

    "for a business error, will return a BusinessResponse" in forAll(Gen.alphaNumStr) {
      message =>
        val response =
          s"""{
             |    "code": "BUSINESS_VALIDATION_ERROR",
             |    "message": "$message"
             |}
             |""".stripMargin

        Json.parse(response).as[JsonValidationErrorResponse] mustBe BusinessValidationResponse(message)
    }

    "for a schema error, will return a JsonSchemaValidationResponse" in forAll(Gen.alphaNumStr) {
      message =>
        val response =
          s"""{
             |    "validationErrors": [
             |      {
             |         "schemaPath": "$$.abc",
             |         "message": "$message"
             |      }
             |    ]
             |}
             |""".stripMargin

        Json.parse(response).as[JsonSchemaValidationResponse] mustBe JsonSchemaValidationResponse(
          NonEmptyList(
            JsonValidationError("$.abc", message),
            Nil
          )
        )
    }

  }

}
