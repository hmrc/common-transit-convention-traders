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

package models.request

import models.request.MessageType.ArrivalNotification
import models.request.MessageType.DeclarationAmendment
import models.request.MessageType.DeclarationData
import models.request.MessageType.DeclarationInvalidationRequest
import models.request.MessageType.PresentationNotificationForThePreLodgedDeclaration
import models.request.MessageType.UnloadingRemarks
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class MessageTypeSpec extends AnyFreeSpec with Matchers with MockitoSugar with OptionValues with ScalaCheckDrivenPropertyChecks {
  "MessageType must contain" - {
    "UnloadingRemarks" in {
      MessageType.values must contain(UnloadingRemarks)
      UnloadingRemarks.code mustEqual "IE044"
      UnloadingRemarks.rootNode mustEqual "CC044C"
      MessageType.updateMessageTypesSentByArrivalTrader must contain(UnloadingRemarks)
    }

    "ArrivalNotification" in {
      MessageType.values must contain(ArrivalNotification)
      ArrivalNotification.code mustEqual "IE007"
      ArrivalNotification.rootNode mustEqual "CC007C"
      MessageType.messageTypesSentByArrivalTrader must contain(ArrivalNotification)
    }

    "DeclarationAmendment" in {
      MessageType.values must contain(DeclarationAmendment)
      DeclarationAmendment.code mustEqual "IE013"
      DeclarationAmendment.rootNode mustEqual "CC013C"
      MessageType.updateMessageTypesSentByDepartureTrader must contain(DeclarationAmendment)
    }

    "DeclarationInvalidation" in {
      MessageType.values must contain(DeclarationInvalidationRequest)
      DeclarationInvalidationRequest.code mustEqual "IE014"
      DeclarationInvalidationRequest.rootNode mustEqual "CC014C"
      MessageType.updateMessageTypesSentByDepartureTrader must contain(DeclarationInvalidationRequest)
    }

    "DeclarationData" in {
      MessageType.values must contain(DeclarationData)
      DeclarationData.code mustEqual "IE015"
      DeclarationData.rootNode mustEqual "CC015C"
      MessageType.messageTypesSentByDepartureTrader must contain(DeclarationData)
    }

    "PresentationNotificationForPreLodgedDec" in {
      MessageType.values must contain(PresentationNotificationForThePreLodgedDeclaration)
      PresentationNotificationForThePreLodgedDeclaration.code mustEqual "IE170"
      PresentationNotificationForThePreLodgedDeclaration.rootNode mustEqual "CC170C"
      MessageType.updateMessageTypesSentByDepartureTrader must contain(PresentationNotificationForThePreLodgedDeclaration)
    }
  }

  "find" - {
    "must return None when junk is provided" in forAll(Gen.stringOfN(6, Gen.alphaNumChar)) {
      code =>
        MessageType.findByCode(code) must not be defined
    }

    "must return the correct message type when a correct code is provided" in forAll(Gen.oneOf(MessageType.values)) {
      messageType =>
        MessageType.findByCode(messageType.code) mustBe Some(messageType)
    }
  }
}
