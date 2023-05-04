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

package v2.services

import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import play.api.http.Status.BAD_REQUEST
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.connectors.RouterConnector
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.SubmissionRoute
import v2.models.errors.InvalidOfficeError
import v2.models.errors.RouterError
import v2.models.request.MessageType

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

class RouterServiceImpl @Inject() (routerConnector: RouterConnector) extends RouterService {

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
          case NonFatal(e) =>
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
}
