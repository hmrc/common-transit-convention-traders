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

package utils

import models.BoxId
import models.MessageStatus
import models.ObjectStoreURI
import models.TotalCount
import models.common.*
import models.request.MessageType
import models.request.MessageUpdate
import models.request.PushNotificationsAssociation
import models.responses.BoxResponse
import models.responses.MessageSummary
import models.responses.MovementResponse
import models.responses.MovementSummary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Arbitrary
import org.scalacheck.Gen

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import scala.math.abs

trait CommonGenerators {

  lazy val genShortUUID: Gen[String] = Gen.long.map {
    l =>
      f"${BigInt(abs(l))}%016x"
  }

  implicit lazy val arbitraryMessageId: Arbitrary[MessageId] = Arbitrary {
    genShortUUID.map(MessageId(_))
  }

  implicit lazy val arbitraryEORINumber: Arbitrary[EORINumber] = Arbitrary {
    Gen.alphaNumStr.map(
      alphaNum => if (alphaNum.trim.size == 0) EORINumber("abc123") else EORINumber(alphaNum) // guard against the empty string
    )
  }

  implicit lazy val arbitraryMovementId: Arbitrary[MovementId] = Arbitrary {
    genShortUUID.map(MovementId(_))
  }

  implicit lazy val arbitraryPageNumber: Arbitrary[PageNumber] = Arbitrary {
    Gen.long.map(
      l => PageNumber(Math.abs(l % Int.MaxValue - 1)) // page number is always >= 0
    )
  }

  implicit lazy val arbitraryItemCount: Arbitrary[ItemCount] = Arbitrary {
    Gen.long.map(
      l => ItemCount(Math.abs(l % (Int.MaxValue - 1))) // item count is always >= 0
    )
  }

  implicit lazy val arbitraryTotalCount: Arbitrary[TotalCount] = Arbitrary {
    Gen.long.map(
      l => TotalCount(Math.abs(l % (Int.MaxValue - 1))) // total count is always >= 0
    )
  }

  implicit lazy val arbitraryMessageType: Arbitrary[MessageType] =
    Arbitrary(Gen.oneOf(MessageType.values.toIndexedSeq))

  // Restricts the date times to the range of positive long numbers to avoid overflows.
  implicit lazy val arbitraryOffsetDateTime: Arbitrary[OffsetDateTime] =
    Arbitrary {
      for {
        millis <- Gen.chooseNum(0L, Long.MaxValue / 1000L)
      } yield OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
    }

  implicit lazy val arbitraryPushNotificationsAssociation: Arbitrary[PushNotificationsAssociation] = Arbitrary {
    for {
      clientId     <- Gen.alphaNumStr.map(ClientId.apply)
      movementType <- Gen.oneOf(MovementType.values)
      boxId        <- Gen.option(Gen.uuid.map(_.toString).map(BoxId.apply))
      eori         <- arbitrary[EORINumber]
    } yield PushNotificationsAssociation(clientId, movementType, boxId, eori)
  }

  def alphaNum(maxLen: Int, minLen: Int = 1) = for {
    len <- Gen.choose(minLen, maxLen)
    str <- Gen.stringOfN(len, Gen.alphaNumChar)
  } yield str

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

  implicit lazy val arbitraryLocalReferenceNumber: Arbitrary[LocalReferenceNumber] = Arbitrary {
    for {
      ref <- alphaNum(22)
    } yield LocalReferenceNumber(ref)
  }

  implicit lazy val arbitraryMovementSummary: Arbitrary[MovementSummary] = Arbitrary {
    for {
      id                      <- arbitrary[MovementId]
      enrollmentEORINumber    <- arbitrary[EORINumber]
      movementEORINumber      <- arbitrary[EORINumber]
      movementReferenceNumber <- arbitrary[Option[MovementReferenceNumber]]
      localReferenceNumber    <- arbitrary[Option[LocalReferenceNumber]]
      created                 <- arbitrary[OffsetDateTime]
      updated                 <- arbitrary[OffsetDateTime]
    } yield MovementSummary(id, enrollmentEORINumber, Some(movementEORINumber), movementReferenceNumber, localReferenceNumber, created, updated)
  }

  implicit lazy val arbitraryObjectStoreURI: Arbitrary[ObjectStoreURI] = Arbitrary {
    Gen.alphaNumStr.map(ObjectStoreURI(_))
  }

  implicit lazy val arbitraryMessageSummary: Arbitrary[MessageSummary] =
    Arbitrary {
      for {
        id             <- arbitrary[MessageId]
        offsetDateTime <- arbitrary[OffsetDateTime]
        messageType    <- arbitrary[MessageType]
        status         <- Gen.oneOf(MessageStatus.values.toIndexedSeq)
        objectStoreURI <- Gen.option(arbitrary[ObjectStoreURI])
      } yield MessageSummary(id, offsetDateTime, Some(messageType), None, Some(status), objectStoreURI)
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

  implicit def arbitraryMovementResponse(): Arbitrary[MovementResponse] = Arbitrary {
    for {
      movementId <- arbitrary[MovementId]
      messageId  <- arbitrary[MessageId]
    } yield MovementResponse(movementId, messageId)
  }

  implicit val arbitraryMessageUpdate: Arbitrary[MessageUpdate] = Arbitrary {
    for {
      status      <- Gen.oneOf(MessageStatus.values.toIndexedSeq)
      uri         <- Gen.alphaNumStr
      messageType <- Gen.oneOf(MessageType.values.toIndexedSeq)
    } yield MessageUpdate(status, Some(ObjectStoreURI(uri)), Some(messageType))
  }

  implicit lazy val arbitraryMovementType: Arbitrary[MovementType] = Arbitrary {
    Gen.oneOf(MovementType.values)
  }

  implicit lazy val arbitraryClientId: Arbitrary[ClientId] = Arbitrary {
    Gen.stringOfN(24, Gen.alphaNumChar).map(ClientId.apply)
  }

}
