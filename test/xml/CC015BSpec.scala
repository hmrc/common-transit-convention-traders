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

package xml

import config.AppConfig
import data.TestXml
import models.request.DepartureDeclarationXSD
import org.mockito.Mockito.when
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import services.XmlValidationService

class CC015BSpec extends AnyFreeSpec with Matchers with ScalaCheckPropertyChecks with TestXml with MockitoSugar {

  private val mockAppConfig        = mock[AppConfig]
  private val xmlValidationService = new XmlValidationService(mockAppConfig)
  when(mockAppConfig.blockUnknownNamespaces).thenReturn(true)

  "validate" - {

    "must be successful when validating a valid CC015B xml" in {
      xmlValidationService.validate(CC015B, DepartureDeclarationXSD) mustBe a[Right[_, _]]
    }

    "must fail when validating an invalid CC015B xml" in {
      xmlValidationService.validate(InvalidCC015B, DepartureDeclarationXSD) mustBe a[Left[_, _]]
    }

    "must reject a CC015B containing MesSenMES3" in {
      xmlValidationService.validate(CC015BwithMesSenMES3, DepartureDeclarationXSD) mustBe a[Left[_, _]]
    }
  }
}
