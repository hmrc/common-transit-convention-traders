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

package controllers

import models.common.errors.PresentationError

import javax.inject.Inject
import play.api.Configuration
import play.api.http.Status.*
import play.api.libs.json.Json
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results.*
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse
import uk.gov.hmrc.play.bootstrap.backend.http.JsonErrorHandler
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CustomJsonErrorHandler @Inject() (
  auditConnector: AuditConnector,
  httpAuditEvent: HttpAuditEvent,
  configuration: Configuration
)(implicit ec: ExecutionContext)
    extends JsonErrorHandler(auditConnector, httpAuditEvent, configuration)(ec) {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    super.onClientError(request, statusCode, message).map {
      result =>
        statusCode match {
          case BAD_REQUEST if message.contains("16 character hexadecimal") =>
            Status(BAD_REQUEST)(Json.toJson(PresentationError.bindingBadRequestError(message)))
          case BAD_REQUEST => BadRequest(Json.toJson(ErrorResponse(BAD_REQUEST, message)))
          case _           => result
        }
    }

  override def onServerError(request: RequestHeader, ex: Throwable): Future[Result] =
    super.onServerError(request, ex)
}
