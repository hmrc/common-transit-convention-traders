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

package services

import com.google.inject.ImplementedBy
import com.google.inject.Inject
import config.AppConfig
import play.api.Logging
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.auth.core.EnrolmentIdentifier
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[EnrolmentLoggingServiceImpl])
trait EnrolmentLoggingService {

  def logEnrolments(clientId: Option[String])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]

}

class EnrolmentLoggingServiceImpl @Inject() (authConnector: AuthConnector, appConfig: AppConfig) extends EnrolmentLoggingService with Logging {

  override def logEnrolments(clientId: Option[String])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    if (appConfig.logInsufficientEnrolments) {
      authConnector
        .authorise(EmptyPredicate, Retrievals.allEnrolments)
        .map(_.enrolments.map(createLogString))
        .map(createMessage(clientId))
        .map(logger.warn(_))
    } else Future.successful(())

  def createLogString(enrolment: Enrolment): String =
    s"Enrolment Key: ${enrolment.key}, Activated: ${enrolment.isActivated}, Identifiers: [ ${createIdentifierString(enrolment.identifiers)} ]"

  // See CLI-169 and CTDA-1925 for the approval for this change.
  def createIdentifierString(identifiers: Seq[EnrolmentIdentifier]): String =
    identifiers
      .map {
        identifier =>
          s"${identifier.key}: ${redact(identifier.value)}"
      }
      .mkString(", ")

  def createMessage(clientId: Option[String])(logMessage: Set[String])(implicit hc: HeaderCarrier): String = {
    val message: Seq[String] = Seq(
      "Insufficient enrolments were received for the following request:",
      s"Client ID: ${clientId.getOrElse("Not provided")}",
      s"Gateway User ID: ${redact(hc.gaUserId)}"
    ) ++ logMessage

    message.mkString("\n")
  }

  def redact(value: Option[String]): String =
    value.map(redact).getOrElse("Not provided")

  def redact(value: String): String =
    if (value.length > 3) s"***${value.takeRight(3)}"
    else "***"

}
