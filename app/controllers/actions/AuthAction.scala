/*
 * Copyright 2020 HM Revenue & Customs
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
import config.AppConfig
import play.api.mvc.Results._
import play.api.mvc._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

class AuthAction @Inject()(
                                               override val authConnector: AuthConnector,
                                               config: AppConfig,
                                               val parser: BodyParsers.Default
                                             )(implicit val executionContext: ExecutionContext)
  extends ActionBuilder[AuthRequest, AnyContent] with ActionFunction[Request, AuthRequest]
    with AuthorisedFunctions {

  private val enrolmentIdentifierKey: String = "VATRegNoTURN"

  override def invokeBlock[A](request: Request[A], block: AuthRequest[A] => Future[Result]): Future[Result] = {

    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers)

    authorised(Enrolment(config.enrolmentKey)).retrieve(Retrievals.authorisedEnrolments) {
      enrolments: Enrolments =>
        val eoriNumber: String = (for {
          enrolment  <- enrolments.enrolments.find(_.key.equals(config.enrolmentKey))
          identifier <- enrolment.getIdentifier(enrolmentIdentifierKey)
        } yield identifier.value).getOrElse(throw InsufficientEnrolments(s"Unable to retrieve enrolment for $enrolmentIdentifierKey"))

        block(AuthRequest(request, eoriNumber))
    }
  } recover {
    case _: InsufficientEnrolments =>
      Unauthorized
    case _: AuthorisationException =>
      Unauthorized
  }
}
