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

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import play.api.mvc.PathBindable
import play.api.mvc.QueryStringBindable
import v2.models.EORINumber
import v2.models.MovementType

object Binders {

  implicit val offsetDateTimeQueryStringBindable: QueryStringBindable[OffsetDateTime] = {
    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    new QueryStringBindable.Parsing[OffsetDateTime](
      OffsetDateTime.parse(_),
      dt => formatter.format(dt),
      (param, _) => s"Cannot parse parameter $param as a valid ISO 8601 timestamp, e.g. 2015-09-08T01:55:28+00:00"
    )
  }

  // needed for the reverse routing
  implicit val optionOffsetDateTimeQueryStringBindable: QueryStringBindable[Option[OffsetDateTime]] =
    QueryStringBindable.bindableOption[OffsetDateTime]

  implicit val optionEORINumberQueryStringBindable: QueryStringBindable[Option[EORINumber]] =
    QueryStringBindable.bindableOption[EORINumber]

  implicit def movementTypePathBindable: PathBindable[MovementType] = new PathBindable[MovementType] {

    override def bind(key: String, value: String): Either[String, MovementType] =
      value match {
        case MovementType.Arrival.urlFragment   => Right(MovementType.Arrival)
        case MovementType.Departure.urlFragment => Right(MovementType.Departure)
        case _                                  => Left(s"$key value $value is not valid. expecting arrivals or departures")
      }

    override def unbind(key: String, value: MovementType): String =
      value.toString
  }
}
