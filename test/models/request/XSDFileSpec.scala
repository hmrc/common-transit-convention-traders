/*
 * Copyright 2020 HM Revenue & Customs
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

package models.request

import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.{FreeSpec, MustMatchers}

class XSDFileSpec extends FreeSpec with MustMatchers with MockitoSugar {
  "XSDFile" - {
    "supported message types must contain UnloadingRemarksXSD" in {
      XSDFile.supportedMessageTypes must contain ("CC044A" -> UnloadingRemarksXSD)
    }

    "supported message types must not contain DepartureDeclarationXSD" in {
      XSDFile.supportedMessageTypes mustNot contain ("CC015B" -> DepartureDeclarationXSD)
    }

    "supported message types must not contain ArrivalNotificationXSD" in {
      XSDFile.supportedMessageTypes mustNot contain ("CC007A" -> ArrivalNotificationXSD)
    }
  }
}
