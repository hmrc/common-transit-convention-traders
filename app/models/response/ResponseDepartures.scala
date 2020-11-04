package models.response

import play.api.libs.json.Json

object ResponseDepartures {
  implicit val format = Json.format[ResponseDepartures]
}

case class ResponseDepartures(departures: Seq[ResponseDeparture])
