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

package v2.models.responses.hateoas

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2.base.TestCommonGenerators
import v2.models.EORINumber
import v2.models.LocalReferenceNumber
import v2.models.MovementReferenceNumber
import v2.models.MovementType

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class HateoasMovementIdsResponseSpec extends AnyFreeSpec with Matchers with OptionValues with TestCommonGenerators with ScalaCheckDrivenPropertyChecks {

  for (movementType <- MovementType.values)
    s"${movementType.movementType} should produce valid HateoasMovementIdsResponse responses with optional updatedSince, movementEORI, movementReferenceNumber, localReferenceNumber" in forAll(
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      Gen.option(arbitrary[MovementReferenceNumber]),
      Gen.option(arbitrary[LocalReferenceNumber])
    ) {
      (updatedSince, movementEORI, movementReferenceNumber, localReferenceNumber) =>
        val movementResponse1 = arbitraryMovementSummary.arbitrary.sample.value
        val movementResponse2 = arbitraryMovementSummary.arbitrary.sample.value

        val responses = Seq(movementResponse1, movementResponse2)

        val expected = Json.obj(
          "_links" -> Json.obj(
            "self" -> selfUrl(movementType, updatedSince, movementEORI, movementReferenceNumber, localReferenceNumber)
          ),
          movementType.urlFragment -> responses.map(
            movementResponse =>
              Json.obj(
                "_links" -> Json.obj(
                  "self"     -> Json.obj("href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse._id.value}"),
                  "messages" -> Json.obj("href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse._id.value}/messages")
                ),
                "id"                      -> movementResponse._id.value,
                "movementReferenceNumber" -> movementResponse.movementReferenceNumber.value,
                "localReferenceNumber"    -> movementResponse.localReferenceNumber.value,
                "created"                 -> movementResponse.created,
                "updated"                 -> movementResponse.updated,
                "enrollmentEORINumber"    -> movementResponse.enrollmentEORINumber,
                "movementEORINumber"      -> movementResponse.movementEORINumber
              )
          )
        )

        val actual = HateoasMovementIdsResponse(responses, movementType, updatedSince, movementEORI, movementReferenceNumber, localReferenceNumber)

        actual mustBe expected
    }

  private def selfUrl(
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber],
    localReferenceNumber: Option[LocalReferenceNumber]
  ): JsObject = {
    (updatedSince, movementEORI, movementReferenceNumber, localReferenceNumber) match {
      case (Some(updatedSince), Some(movementEORI), Some(movementReferenceNumber), Some(localReferenceNumber)) =>
        val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(updatedSince)
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?updatedSince=$time&movementEORI=${movementEORI.value}&movementReferenceNumber=${movementReferenceNumber.value}&localReferenceNumber=${localReferenceNumber.value}"
        )

      case (Some(updatedSince), Some(movementEORI), Some(movementReferenceNumber), _) =>
        val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(updatedSince)
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?updatedSince=$time&movementEORI=${movementEORI.value}&movementReferenceNumber=${movementReferenceNumber.value}"
        )

      case (Some(updatedSince), Some(movementEORI), _, Some(localReferenceNumber)) =>
        val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(updatedSince)
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?updatedSince=$time&movementEORI=${movementEORI.value}&localReferenceNumber=${localReferenceNumber.value}"
        )

      case (Some(updatedSince), _, Some(movementReferenceNumber), Some(localReferenceNumber)) =>
        val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(updatedSince)
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?updatedSince=$time&movementReferenceNumber=${movementReferenceNumber.value}&localReferenceNumber=${localReferenceNumber.value}"
        )

      case (_, Some(movementEORI), Some(movementReferenceNumber), Some(localReferenceNumber)) =>
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?movementEORI=${movementEORI.value}&movementReferenceNumber=${movementReferenceNumber.value}&localReferenceNumber=${localReferenceNumber.value}"
        )

      case (Some(updatedSince), Some(movementEORI), _, _) =>
        val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(updatedSince)
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?updatedSince=$time&movementEORI=${movementEORI.value}"
        )

      case (Some(updatedSince), _, Some(movementReferenceNumber), _) =>
        val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(updatedSince)
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?updatedSince=$time&movementReferenceNumber=${movementReferenceNumber.value}"
        )

      case (Some(updatedSince), _, _, Some(localReferenceNumber)) =>
        val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(updatedSince)
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?updatedSince=$time&localReferenceNumber=${localReferenceNumber.value}"
        )

      case (_, Some(movementEORI), Some(movementReferenceNumber), _) =>
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?movementEORI=${movementEORI.value}&movementReferenceNumber=${movementReferenceNumber.value}"
        )

      case (_, Some(movementEORI), _, Some(localReferenceNumber)) =>
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?movementEORI=${movementEORI.value}&localReferenceNumber=${localReferenceNumber.value}"
        )

      case (_, _, Some(movementReferenceNumber), Some(localReferenceNumber)) =>
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?movementReferenceNumber=${movementReferenceNumber.value}&localReferenceNumber=${localReferenceNumber.value}"
        )

      case (Some(updatedSince), _, _, _) =>
        val time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(updatedSince)
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?updatedSince=$time"
        )

      case (_, Some(movementEORI), _, _) =>
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?movementEORI=${movementEORI.value}"
        )

      case (_, _, Some(movementReferenceNumber), _) =>
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?movementReferenceNumber=${movementReferenceNumber.value}"
        )

      case (_, _, Some(localReferenceNumber), _) =>
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}?localReferenceNumber=${localReferenceNumber.value}"
        )

      case (_, _, _, _) =>
        Json.obj(
          "href" -> s"/customs/transits/movements/${movementType.urlFragment}"
        )
    }
  }
}
