/*
 * Copyright 2022 HM Revenue & Customs
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

package xml

import data.TestXml
import models.request.DeclarationCancellationRequestXSD
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import services.XmlValidationService

class CC014ASpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks with TestXml {

  private val xmlValidationService = new XmlValidationService

  "validate" - {

    "must be successful when validating a valid CC014A xml" in {
      xmlValidationService.validate(CC014A, DeclarationCancellationRequestXSD) mustBe a[Right[_, _]]
    }

    "must fail when validating an invalid CC014A xml" in {
      xmlValidationService.validate(InvalidCC014A, DeclarationCancellationRequestXSD) mustBe a[Left[_, _]]
    }

    "must reject a CC014A containing MesSenMES3" in {
      xmlValidationService.validate(CC014AwithMesSenMES3, DeclarationCancellationRequestXSD) mustBe a[Left[_, _]]
    }
  }
}
