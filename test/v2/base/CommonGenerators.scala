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

package v2.base

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import v2.models.AuditType
import v2.models.BoxId
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementReferenceNumber
import v2.models.MovementType
import v2.models.XmlPayload
import v2.models.request.MessageType
import v2.models.responses.BoxResponse
import v2.models.responses.MessageSummary
import v2.models.responses.MovementResponse
import v2.models.responses.MovementSummary
import v2.models.responses.UpscanFormTemplate
import v2.models.responses.UpscanInitiateResponse
import v2.models.responses.UpscanReference
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

trait CommonGenerators {

  lazy val genShortUUID: Gen[String] = Gen.long.map {
    l: Long =>
      f"${BigInt(l)}%016x"
  }

  implicit lazy val arbitraryMessageId: Arbitrary[MessageId] = Arbitrary {
    genShortUUID.map(MessageId(_))
  }

  implicit lazy val arbitraryEORINumber: Arbitrary[EORINumber] = Arbitrary {
    Gen.alphaNumStr.map(EORINumber(_))
  }

  implicit lazy val arbitraryMovementReferenceNumber: Arbitrary[MovementReferenceNumber] = Arbitrary {
    Gen.alphaNumStr.map(MovementReferenceNumber(_))
  }

  implicit lazy val arbitraryMessageType: Arbitrary[MessageType] = Arbitrary {
    Gen.oneOf(MessageType.values)
  }

  implicit lazy val arbitraryAuditType: Arbitrary[AuditType] = Arbitrary {
    Gen.oneOf(AuditType.values)
  }

  implicit lazy val arbitraryMessageSummaryXml: Arbitrary[MessageSummary] = Arbitrary {
    for {
      received    <- arbitrary[OffsetDateTime]
      messageType <- Gen.oneOf(MessageType.values)
      body        <- Gen.option(Gen.alphaNumStr.map(XmlPayload(_)))
      messageId   <- genShortUUID.map(MessageId(_))
    } yield MessageSummary(messageId, received, messageType, body)
  }

  implicit lazy val arbitraryMovementId: Arbitrary[MovementId] = Arbitrary {
    genShortUUID.map(MovementId(_))
  }

  implicit lazy val arbitraryMovementType: Arbitrary[MovementType] = Arbitrary {
    Gen.oneOf(MovementType.values)
  }

  // Restricts the date times to the range of positive long numbers to avoid overflows.
  implicit lazy val arbitraryOffsetDateTime: Arbitrary[OffsetDateTime] =
    Arbitrary {
      for {
        millis <- Gen.chooseNum(0, Long.MaxValue / 1000L)
      } yield OffsetDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneOffset.UTC)
    }

  implicit lazy val arbitraryMovementSummary: Arbitrary[MovementSummary] = Arbitrary {
    for {
      id                      <- arbitrary[MovementId]
      enrollmentEORINumber    <- arbitrary[EORINumber]
      movementEORINumber      <- arbitrary[EORINumber]
      movementReferenceNumber <- arbitrary[MovementReferenceNumber]
      created                 <- arbitraryOffsetDateTime.arbitrary
      updated                 <- arbitraryOffsetDateTime.arbitrary
    } yield MovementSummary(id, enrollmentEORINumber, movementEORINumber, Some(movementReferenceNumber), created, updated)
  }

  implicit private lazy val arbitraryFields: Arbitrary[Map[String, String]] = Arbitrary {
    for {
      fieldKeys   <- Gen.listOfN(5, Gen.alphaNumStr)
      fieldValues <- Gen.listOfN(5, Gen.alphaNumStr)
    } yield fieldKeys.zip(fieldValues).toMap
  }

  implicit private lazy val arbitraryUpscanTemplateResponse: Arbitrary[UpscanFormTemplate] = Arbitrary {
    for {
      href <- Gen.alphaNumStr
      fields = arbitraryFields.arbitrary.sample.get
    } yield UpscanFormTemplate(href, fields)
  }

  implicit lazy val arbitraryUpscanInitiateResponse: Arbitrary[UpscanInitiateResponse] = Arbitrary {
    for {
      upscanReference <- Gen.alphaNumStr.map(UpscanReference(_))
      formTemplate    <- arbitrary[UpscanFormTemplate]
    } yield UpscanInitiateResponse(upscanReference, formTemplate)
  }

  implicit lazy val arbitraryBoxResponse: Arbitrary[BoxResponse] = Arbitrary {
    for {
      boxId <- genShortUUID.map(BoxId(_))
    } yield BoxResponse(boxId)
  }

  implicit def arbitraryMovementResponse(withMessage: Boolean): Arbitrary[MovementResponse] = Arbitrary {
    for {
      movementId <- arbitrary[MovementId]
      messageId = if (withMessage) arbitrary[MessageId].sample else None
    } yield MovementResponse(movementId, messageId)
  }

}
