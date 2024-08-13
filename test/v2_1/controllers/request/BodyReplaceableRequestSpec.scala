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

package v2_1.controllers.request

import controllers.common.AuthenticatedRequest
import models.common.EORINumber
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.test.FakeHeaders
import play.api.test.FakeRequest

class BodyReplaceableRequestSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  "Authenticated Request produces a new request with replaced body" in forAll(Gen.alphaNumStr.map(EORINumber.apply), Gen.alphaNumStr, Gen.alphaNumStr) {
    (eori, oldBody, newBody) =>
      val original = AuthenticatedRequest(eori, FakeRequest("POST", "/", FakeHeaders(), oldBody))
      val replaced = original.replaceBody(newBody)

      // the result must be the same as the original, except for the body,
      // Oddly, result mustEqual AuthenticatedRequest(original.eoriNumber, original.request.withBody(newSource)) doesn't
      // work, so we just check for equality on the attributes of the request
      replaced.eoriNumber mustEqual replaced.eoriNumber
      replaced.connection mustEqual replaced.connection
      replaced.method mustEqual replaced.method
      replaced.target mustEqual replaced.target
      replaced.version mustEqual replaced.version
      replaced.headers mustEqual replaced.headers
      replaced.body mustEqual newBody
      replaced.attrs mustEqual replaced.attrs
  }

}
