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

package controllers.actions

import com.google.inject.Inject
import config.Constants._
import models.errors.ForbiddenError
import models.errors.TransitMovementError
import models.formats.HttpFormats
import play.api.Logging
import play.api.mvc.Results._
import play.api.mvc._
import play.api.libs.json.Json
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AuthNewEnrolmentOnlyAction @Inject() (override val authConnector: AuthConnector, val parser: BodyParsers.Default)(implicit
  val executionContext: ExecutionContext
) extends ActionBuilder[AuthRequest, AnyContent]
    with ActionFunction[Request, AuthRequest]
    with AuthorisedFunctions
    with Logging
    with HttpFormats {

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
              block(AuthRequest(request, eoriNumber))
          }
          .getOrElse {
            Future.failed(InsufficientEnrolments(s"Unable to retrieve enrolment for $NewEnrolmentKey"))
          }
    }
  } recover {
    case e: InsufficientEnrolments =>
      logger.warn("Failed to authorise due to insufficient enrolments", e)
      Forbidden(Json.toJson[TransitMovementError](ForbiddenError("Current user doesn't have a valid EORI enrolment.")))
    case e: AuthorisationException =>
      logger.warn(s"Failed to authorise", e)
      Unauthorized
  }
}
