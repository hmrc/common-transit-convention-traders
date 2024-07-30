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

package v2_1.services

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import models.common.EORINumber
import models.common.LocalReferenceNumber
import models.common.MessageId
import models.common.MovementId
import models.common.errors.InvalidOfficeError
import models.common.errors.LRNError
import models.common.errors.RouterError
import play.api.Logging
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.CONFLICT
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2_1.connectors.RouterConnector
import v2_1.models.SubmissionRoute
import v2_1.models.request.MessageType

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

@ImplementedBy(classOf[RouterServiceImpl])
trait RouterService {

  def send(messageType: MessageType, eoriNumber: EORINumber, movementId: MovementId, messageId: MessageId, body: Source[ByteString, _])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): EitherT[Future, RouterError, SubmissionRoute]

}

class RouterServiceImpl @Inject() (routerConnector: RouterConnector) extends RouterService with Logging {

  def send(messageType: MessageType, eoriNumber: EORINumber, movementId: MovementId, messageId: MessageId, body: Source[ByteString, _])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): EitherT[Future, RouterError, SubmissionRoute] =
    EitherT(
      routerConnector
        .post(messageType, eoriNumber, movementId, messageId, body)
        .map(
          result => Right(result)
        )
        .recover {
          case UpstreamErrorResponse(message, BAD_REQUEST, _, _) => Left(determineError(message))
          case UpstreamErrorResponse(message, CONFLICT, _, _)    => Left(onConflict(message))
          case NonFatal(e) =>
            logger.error(s"Unable to send to EIS : ${e.getMessage}", e)
            Left(RouterError.UnexpectedError(thr = Some(e)))
        }
    )

  private def determineError(message: String): RouterError =
    Try(Json.parse(message))
      .map(_.validate[InvalidOfficeError])
      .map {
        case JsSuccess(value: InvalidOfficeError, _) => RouterError.UnrecognisedOffice(value.office, value.field)
        case _                                       => RouterError.UnexpectedError()
      }
      .getOrElse(RouterError.UnexpectedError()) // we didn't get Json, but the exception here would then be a
  // red herring, so don't pass it on

  private def onConflict(message: String): RouterError =
    Json
      .parse(message)
      .validate[LRNError]
      .map(
        lrnError => RouterError.DuplicateLRN(LocalReferenceNumber(lrnError.lrn))
      )
      .recoverTotal {
        case JsError(_) => RouterError.UnexpectedError()
      }

}
