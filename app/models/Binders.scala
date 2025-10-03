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

import models.common.EORINumber

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import play.api.mvc.QueryStringBindable

object Binders {

  // The QueryStringBindable will pick up the exception, hence we throw one here (against normal Scala norms)
  private def ensurePositiveYear(offsetDateTime: OffsetDateTime): OffsetDateTime =
    if (offsetDateTime.getYear < 1) throw new IllegalArgumentException("negative year")
    else offsetDateTime

  implicit val offsetDateTimeQueryStringBindable: QueryStringBindable[OffsetDateTime] = {
    val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    new QueryStringBindable.Parsing[OffsetDateTime](
      str => ensurePositiveYear(OffsetDateTime.parse(str)),
      dt => formatter.format(dt),
      (param, exception) =>
        exception match {
          case x: IllegalArgumentException if x.getMessage == "negative year" => "Year cannot be negative"
          case _                                                              =>
            s"Cannot parse parameter $param as a valid ISO 8601 timestamp, e.g. 2015-09-08T01:55:28+00:00"
        }
    )
  }

  // needed for the reverse routing
  implicit val optionOffsetDateTimeQueryStringBindable: QueryStringBindable[Option[OffsetDateTime]] =
    QueryStringBindable.bindableOption[OffsetDateTime]

  implicit val optionEORINumberQueryStringBindable: QueryStringBindable[Option[EORINumber]] =
    QueryStringBindable.bindableOption[EORINumber]

}
