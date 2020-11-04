package models.domain

import play.api.libs.json.Json

object Departures {
  implicit val format = Json.format[Departures]
}

case class Departures(departures: Seq[Departure])
