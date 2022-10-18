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

package v2.services

import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import play.api.http.Status.BAD_REQUEST
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.connectors.RouterConnector
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.errors.RouterError
import v2.models.request.MessageType

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[RouterServiceImpl])
trait RouterService {

  def send(messageType: MessageType, eoriNumber: EORINumber, movementId: MovementId, messageId: MessageId, body: Source[ByteString, _])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): EitherT[Future, RouterError, Unit]

}

class RouterServiceImpl @Inject() (routerConnector: RouterConnector) extends RouterService {

  def send(messageType: MessageType, eoriNumber: EORINumber, movementId: MovementId, messageId: MessageId, body: Source[ByteString, _])(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): EitherT[Future, RouterError, Unit] =
    EitherT(
      routerConnector
        .post(messageType, eoriNumber, movementId, messageId, body)
        .map(
          _ => Right(())
        )
        .recover {
          case UpstreamErrorResponse(_, BAD_REQUEST, _, _) =>
            Left(RouterError.UnrecognisedOffice)
          case NonFatal(e) =>
            Left(RouterError.UnexpectedError(thr = Some(e)))
        }
    )
}
