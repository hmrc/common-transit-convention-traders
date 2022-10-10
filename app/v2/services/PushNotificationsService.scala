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

import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import config.Constants
import play.api.mvc.Headers
import uk.gov.hmrc.http.HeaderCarrier
import v2.connectors.PushNotificationsConnector
import v2.models.BoxId
import v2.models.ClientId
import v2.models.MovementId
import v2.models.errors.PushNotificationError
import v2.models.request.PushNotificationsAssociation

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[PushNotificationsServiceImpl])
trait PushNotificationsService {

  def associate(movementId: MovementId, headers: Headers)(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext
  ): EitherT[Future, PushNotificationError, Unit]

}

class PushNotificationsServiceImpl @Inject() (pushNotificationsConnector: PushNotificationsConnector) extends PushNotificationsService {

  override def associate(movementId: MovementId, headers: Headers)(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext
  ): EitherT[Future, PushNotificationError, Unit] =
    EitherT {
      headers
        .get(Constants.XClientIdHeader)
        .map {
          clientId =>
            pushNotificationsConnector
              .postAssociation(
                movementId,
                PushNotificationsAssociation(ClientId(clientId), headers.get(Constants.XClientBoxIdHeader).map(BoxId.apply))
              )
              .map(Right(_))
              .recover {
                case NonFatal(thr) => Left(PushNotificationError.UnexpectedError(thr = Some(thr)))
              }
        }
        .getOrElse(Future.successful(Left(PushNotificationError.MissingClientId)))
    }

}
