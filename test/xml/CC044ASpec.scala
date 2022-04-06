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
import models.request.UnloadingRemarksXSD
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import services.XmlValidationService

class CC044ASpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks with TestXml {

  private val xmlValidationService = new XmlValidationService

  "validate" - {

    "must be successful when validating a valid CC044A xml" in {
      xmlValidationService.validate(CC044A.toString(), UnloadingRemarksXSD) mustBe a[Right[_, _]]
    }

    "must fail when validating an invalid CC044A xml" in {
      xmlValidationService.validate(InvalidCC044A.toString(), UnloadingRemarksXSD) mustBe a[Left[_, _]]
    }

    "must reject a CC044A containing MesSenMES3" in {
      xmlValidationService.validate(CC044AwithMesSenMES3.toString(), UnloadingRemarksXSD) mustBe a[Left[_, _]]
    }
  }
}
