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

package v2.models.responses.hateoas

import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import v2.base.CommonGenerators
import v2.models.DepartureId
import v2.models.MessageId

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

class HateoasDepartureMessageIdsResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks with OptionValues with CommonGenerators {

  Seq(arbitrary[OffsetDateTime].sample, None).foreach {
    dateTime =>
      val set = dateTime
        .map(
          _ => "set"
        )
        .getOrElse("not set")

      s"with a valid message response and receivedSince $set, create a valid HateoasDepartureMessageResponse" in {
        val messageIds = (for {
          m1 <- arbitrary[MessageId]
          m2 <- arbitrary[MessageId]
          m3 <- arbitrary[MessageId]
        } yield Seq(m1, m2, m3)).sample.value
        val departureId = arbitrary[DepartureId].sample.value
        val actual      = HateoasDepartureMessageIdsResponse(departureId, messageIds, dateTime)
        val expected = Json.obj(
          "_links" -> Json.obj(
            "self"      -> selfUrl(departureId, dateTime),
            "departure" -> Json.obj("href" -> s"/customs/transits/movements/departures/${departureId.value}")
          ),
          "departure" -> Json.obj(
            "id" -> s"/customs/transits/movements/departures/${departureId.value}"
          ),
          "messages" -> messageIds.map(
            x =>
              Json.obj(
                "id" -> s"/customs/transits/movements/departures/${departureId.value}/messages/${x.value}"
              )
          )
        )

        actual mustBe expected
      }
  }

  private def selfUrl(departureId: DepartureId, dateTime: Option[OffsetDateTime]): JsObject = dateTime match {
    case Some(x) =>
      Json.obj(
        "href" -> s"/customs/transits/movements/departures/${departureId.value}/messages?receivedSince=${x.truncatedTo(ChronoUnit.SECONDS)}"
      )
    case None => Json.obj("href" -> s"/customs/transits/movements/departures/${departureId.value}/messages")
  }

}
