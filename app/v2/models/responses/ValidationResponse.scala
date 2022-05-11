package v2.models.responses

import play.api.libs.functional.syntax.toInvariantFunctorOps
import play.api.libs.functional.syntax.unlift
import play.api.libs.json.OFormat
import play.api.libs.json.Reads
import play.api.libs.json.__

object ValidationResponse {

  implicit val validationResponseFormat: OFormat[ValidationResponse] =
    ((__ \ "validationErrors")).format(Reads.seq[String]).inmap(ValidationResponse.apply, unlift(ValidationResponse.unapply))

}
case class ValidationResponse(validationErrors: Seq[String])
