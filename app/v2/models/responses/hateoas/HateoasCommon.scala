package v2.models.responses.hateoas

import play.api.libs.json.Json

object HateoasHref {
  implicit lazy val hateoasHrefWrites = Json.writes[HateoasHref]
}
case class HateoasHref(href: String)

object HateoasEmbedded {
  implicit lazy val hateoasEmbedded = Json.writes[HateoasEmbedded]
}
case class HateoasEmbedded(notifcations: Option[HateoasRequest])

case class HateoasRequest(requestId: String)

abstract sealed class HateoasLinks {
  def self: HateoasHref
}

case class HateoasDepartureLinks(self: HateoasHref, departure: HateoasHref) extends HateoasLinks