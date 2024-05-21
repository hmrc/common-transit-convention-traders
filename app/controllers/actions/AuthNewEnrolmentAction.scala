/*
 * Copyright 2024 HM Revenue & Customs
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

package controllers.actions

import com.google.inject.Inject
import config.Constants.{NewEnrolmentIdKey, NewEnrolmentKey}
import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import v2.models.errors.PresentationError

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class AuthNewEnrolmentAction @Inject() (
  override val authConnector: AuthConnector,
  val parser: BodyParsers.Default
)(implicit val executionContext: ExecutionContext)
    extends ActionBuilder[AuthRequest, AnyContent]
    with ActionFunction[Request, AuthRequest]
    with AuthorisedFunctions
    with Logging {

  def getEnrolmentIdentifier(enrolments: Enrolments, enrolmentKey: String, enrolmentIdKey: String): Option[String] =
    for {
      enrolment  <- enrolments.getEnrolment(enrolmentKey)
      identifier <- enrolment.getIdentifier(enrolmentIdKey)
    } yield identifier.value

  override def invokeBlock[A](request: Request[A], block: AuthRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)

    authorised(Enrolment(NewEnrolmentKey)).retrieve(Retrievals.authorisedEnrolments) {
      enrolments: Enrolments =>
        val newEnrolmentId = getEnrolmentIdentifier(
          enrolments,
          NewEnrolmentKey,
          NewEnrolmentIdKey
        )

        newEnrolmentId
          .map {
            eoriNumber =>
              block(AuthRequest(request, eoriNumber, true))
          }
          .getOrElse {
            Future.failed(InsufficientEnrolments(s"Unable to retrieve enrolment for $NewEnrolmentKey"))
          }
    }
  } recover {
    case e: InsufficientEnrolments =>
      logger.warn("Failed to authorise due to insufficient enrolments", e)
      Forbidden(
        Json.toJson(
          PresentationError.forbiddenError(
            "Legacy enrolment is no longer supported. Please upgrade to the new ECC enrolment to submit declarations. Guidance for new ECC enrolment: https://www.gov.uk/guidance/how-to-subscribe-to-the-new-computerised-transit-system"
          )
        )
      )
    case e: AuthorisationException =>
      logger.warn(s"Failed to authorise", e)
      Unauthorized(Json.toJson(PresentationError.unauthorized(s"Failed to authorise user: ${e.reason}")))
    case NonFatal(thr) =>
      logger.error(s"Error returned from auth service: ${thr.getMessage}", thr)
      InternalServerError(Json.toJson(PresentationError.internalServiceError(cause = Some(thr))))
  }
}
