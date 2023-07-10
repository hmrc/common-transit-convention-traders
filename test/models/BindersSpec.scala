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

package models

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import v2.base.TestCommonGenerators

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class BindersSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks with TestCommonGenerators {

  "offsetDateTimeQueryStringBindable" - {

    "parses a standard ISO 8601 timestamp" in forAll(arbitrary[OffsetDateTime].map(_.truncatedTo(ChronoUnit.SECONDS))) {
      dateTime =>
        val formattedDateTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime)
        val result            = Binders.offsetDateTimeQueryStringBindable.bind("something", Map("something" -> Seq(formattedDateTime)))
        result mustBe Some(Right(dateTime))
    }

    "parses a standard ISO 8601 timestamp but rejects a negative year" in forAll(
      arbitrary[OffsetDateTime].map(
        x => x.withYear(-x.getYear)
      )
    ) {
      dateTime =>
        val formattedDateTime = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime)
        val result            = Binders.offsetDateTimeQueryStringBindable.bind("something", Map("something" -> Seq(formattedDateTime)))
        result mustBe Some(Left("Year cannot be negative"))
    }

    "does not parse something that is not a timestamp" in forAll(Gen.alphaNumStr.filterNot(_.isEmpty)) {
      str =>
        // ScalaCheck seems to allow for empty strings, even with the filter above. To work around this, we catch the empty
        // string and make it not empty -- the reason being that an empty query string value will basically not get parsed
        // and return None, rather than Some(Left).
        val testString = if (str.isEmpty) "test" else str
        val result     = Binders.offsetDateTimeQueryStringBindable.bind("something", Map("something" -> Seq(testString)))
        result mustBe Some(Left(s"Cannot parse parameter something as a valid ISO 8601 timestamp, e.g. 2015-09-08T01:55:28+00:00"))
    }

  }

}
