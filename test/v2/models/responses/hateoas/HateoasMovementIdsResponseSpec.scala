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
import v2.models.responses.PaginationMovementSummary
import v2.models.EORINumber
import v2.models.ItemCount
import v2.models.LocalReferenceNumber
import v2.models.MovementReferenceNumber
import v2.models.MovementType
import v2.models.PageNumber
import v2.models.TotalCount

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class HateoasMovementIdsResponseSpec extends AnyFreeSpec with Matchers with OptionValues with TestCommonGenerators with ScalaCheckDrivenPropertyChecks {

  for (movementType <- MovementType.values)
    s"${movementType.movementType} should produce valid HateoasMovementIdsResponse responses with optional updatedSince, movementEORI, movementReferenceNumber, page, count, receivedUntil and localReferenceNumber" in forAll(
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      Gen.option(arbitrary[MovementReferenceNumber]),
      Gen.option(arbitrary[PageNumber]),
      Gen.option(arbitrary[ItemCount]),
      Gen.option(arbitrary[LocalReferenceNumber])
    ) {
      (updatedSince, movementEORI, movementReferenceNumber, page, count, localReferenceNumber) =>
        val receivedUntil     = updatedSince
        val movementResponse1 = arbitraryMovementSummary.arbitrary.sample.value
        val movementResponse2 = arbitraryMovementSummary.arbitrary.sample.value
        val responses         = Seq(movementResponse1, movementResponse2)
        val response          = PaginationMovementSummary(TotalCount(responses.length), responses)

        val expected = Json.obj(
          "_links" -> Json.obj(
            "self" -> selfUrl(movementType, updatedSince, movementEORI, movementReferenceNumber, page, count, receivedUntil, localReferenceNumber)
          ),
          "totalCount" -> response.totalCount,
          movementType.urlFragment -> response.movementSummary.map(
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

        val actual = HateoasMovementIdsResponse(
          response,
          movementType,
          updatedSince,
          movementEORI,
          movementReferenceNumber,
          page,
          count,
          receivedUntil,
          localReferenceNumber
        )

        actual mustBe expected
    }

  private def selfUrl(
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber],
    page: Option[PageNumber],
    count: Option[ItemCount],
    receivedUntil: Option[OffsetDateTime],
    localReferenceNumber: Option[LocalReferenceNumber]
  ): JsObject = {

    val updated  = updatedSince.fold("")("updatedSince=" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(_)).trim
    val eori     = movementEORI.fold("")("movementEORI=" + _.value).trim
    val mrn      = movementReferenceNumber.fold("")("movementReferenceNumber=" + _.value).trim
    val pageNum  = page.fold("")("page=" + _.value).trim
    val countNum = count.fold("")("count=" + _.value).trim
    val received = receivedUntil.fold("")("receivedUntil=" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(_)).trim
    val lrn      = localReferenceNumber.fold("")("localReferenceNumber=" + _.value).trim

    val queryString = Seq(updated, eori, mrn, pageNum, countNum, received, lrn)
      .map(
        param => if (param.length > 0) "&" + param else ""
      )
      .mkString
      .replaceFirst("&", "?")

    val url = s"/customs/transits/movements/${movementType.urlFragment}$queryString"

    Json.obj("href" -> url)
  }

}
