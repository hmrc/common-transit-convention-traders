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

package models

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar

class GuaranteeSpec extends AnyFreeSpec with MockitoSugar with Matchers {

  "isDefaulting" - {
    "returns false when guarantee type is not a reference type" in {
      val nonReferenceTypes = Guarantee.validTypes.diff(Guarantee.referenceTypes)

      nonReferenceTypes.foreach {
        typeChar =>
          Guarantee(typeChar, "test").isDefaulting mustBe false
      }
    }

    "returns true when guarantee type is a reference type" in {
      Guarantee.referenceTypes.foreach {
        typeChar =>
          Guarantee(typeChar, "test").isDefaulting mustBe true
      }
    }
  }
}
