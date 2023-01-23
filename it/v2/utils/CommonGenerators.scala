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

package v2.utils

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Arbitrary
import org.scalacheck.Gen
import v2.models._
import v2.models.request.MessageType
import v2.models.request.PushNotificationsAssociation
import v2.models.responses.BoxResponse
import v2.models.responses.MessageSummary
import v2.models.responses.MovementSummary
import v2.models.responses.UpscanInitiateResponse

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
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
    genShortUUID.map(MovementId(_))
  }

  implicit lazy val arbitraryMessageType: Arbitrary[MessageType] =
    Arbitrary(Gen.oneOf(MessageType.values))

  // Restricts the date times to the range of positive long numbers to avoid overflows.
  implicit lazy val arbitraryOffsetDateTime: Arbitrary[OffsetDateTime] =
    Arbitrary {
      for {
        millis <- Gen.chooseNum(0, Long.MaxValue / 1000L)
      } yield OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
    }

  implicit lazy val arbitraryPushNotificationsAssociation: Arbitrary[PushNotificationsAssociation] = Arbitrary {
    for {
      clientId     <- Gen.alphaNumStr.map(ClientId.apply)
      movementType <- Gen.oneOf(MovementType.values)
      boxId        <- Gen.option(Gen.uuid.map(_.toString).map(BoxId.apply))
    } yield PushNotificationsAssociation(clientId, movementType, boxId)
  }

  implicit lazy val arbitraryMovementReferenceNumber: Arbitrary[MovementReferenceNumber] =
    Arbitrary {
      for {
        year <- Gen
          .choose(0, 99)
          .map(
            y => f"$y%02d"
          )
        country <- Gen.pick(2, 'A' to 'Z')
        serial  <- Gen.pick(13, ('A' to 'Z') ++ ('0' to '9'))
      } yield MovementReferenceNumber(year ++ country.mkString ++ serial.mkString)
    }

  implicit lazy val arbitraryMovementSummary: Arbitrary[MovementSummary] = Arbitrary {
    for {
      id                      <- arbitrary[MovementId]
      enrollmentEORINumber    <- arbitrary[EORINumber]
      movementEORINumber      <- arbitrary[EORINumber]
      movementReferenceNumber <- arbitrary[Option[MovementReferenceNumber]]
      created                 <- arbitrary[OffsetDateTime]
      updated                 <- arbitrary[OffsetDateTime]
    } yield MovementSummary(id, enrollmentEORINumber, movementEORINumber, movementReferenceNumber, created, updated)
  }

  implicit lazy val arbitraryMessageSummary: Arbitrary[MessageSummary] =
    Arbitrary {
      for {
        id             <- arbitrary[MessageId]
        offsetDateTime <- arbitrary[OffsetDateTime]
        messageType    <- arbitrary[MessageType]
      } yield MessageSummary(id, offsetDateTime, messageType, None)
    }

  implicit lazy val arbitraryBoxId: Arbitrary[BoxId] = Arbitrary {
    Gen.delay(BoxId(UUID.randomUUID.toString))
  }

  implicit lazy val arbitraryBoxResponse: Arbitrary[BoxResponse] =
    Arbitrary {
      for {
        boxId <- arbitrary[BoxId]
      } yield BoxResponse(boxId)
    }
}
