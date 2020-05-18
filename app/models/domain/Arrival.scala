package models.domain

import java.time.LocalDateTime

import play.api.libs.json.Json

object Arrival {
  implicit val format = Json.format[Arrival]
}

trait BaseArrival {
  def arrivalId: Int
  def location: String
  def messagesLocation: String
  def movementReferenceNumber: String
  def status: String
  def created: LocalDateTime
  def updated: LocalDateTime
}

case class Arrival(arrivalId: Int,
                   location: String,
                   messagesLocation: String,
                   movementReferenceNumber: String,
                   status: String,
                   created: LocalDateTime,
                   updated: LocalDateTime) extends BaseArrival {

}
