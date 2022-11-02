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

package v2.utils

import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import v2.models.BoxId
import v2.models.ClientId
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementType
import v2.models.request.PushNotificationsAssociation

import scala.math.abs

trait CommonGenerators {

  lazy val genShortUUID: Gen[String] = Gen.long.map {
    l: Long =>
      f"${BigInt(abs(l))}%016x"
  }

  implicit lazy val arbitraryMessageId: Arbitrary[MessageId] = Arbitrary {
    genShortUUID.map(MessageId(_))
  }

  implicit lazy val arbitraryEORINumber: Arbitrary[EORINumber] = Arbitrary {
    Gen.alphaNumStr.map(EORINumber(_))
  }

  implicit lazy val arbitraryMovementId: Arbitrary[MovementId] = Arbitrary {
    Gen.alphaNumStr.map(MovementId(_))
  }

  implicit lazy val arbitraryPushNotificationsAssociation: Arbitrary[PushNotificationsAssociation] = Arbitrary {
    for {
      clientId     <- Gen.alphaNumStr.map(ClientId.apply)
      movementType <- Gen.oneOf(MovementType.values)
      boxId        <- Gen.option(Gen.uuid.map(_.toString).map(BoxId.apply))
    } yield PushNotificationsAssociation(clientId, movementType, boxId)
  }
}
