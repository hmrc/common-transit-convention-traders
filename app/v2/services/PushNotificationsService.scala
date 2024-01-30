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

import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import config.AppConfig
import config.Constants
import play.api.Logging
import play.api.http.Status.NOT_FOUND
import play.api.libs.json.JsValue
import play.api.mvc.Headers
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.connectors.PushNotificationsConnector
import v2.models.BoxId
import v2.models.ClientId
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementType
import v2.models.errors.PushNotificationError
import v2.models.request.PushNotificationsAssociation
import v2.models.responses.BoxResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[PushNotificationsServiceImpl])
trait PushNotificationsService {

  def associate(movementId: MovementId, movementType: MovementType, headers: Headers, enrollmentEORINumber: EORINumber)(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext
  ): EitherT[Future, PushNotificationError, BoxResponse]

  def update(movementId: MovementId)(implicit headerCarrier: HeaderCarrier, executionContext: ExecutionContext): EitherT[Future, PushNotificationError, Unit]

  def postPpnsNotification(movementId: MovementId, messageId: MessageId, body: JsValue)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PushNotificationError, Unit]

}

class PushNotificationsServiceImpl @Inject() (
  pushNotificationsConnector: PushNotificationsConnector,
  appConfig: AppConfig
) extends PushNotificationsService
    with Logging {

  override def associate(movementId: MovementId, movementType: MovementType, headers: Headers, enrollmentEORINumber: EORINumber)(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext
  ): EitherT[Future, PushNotificationError, BoxResponse] =
    if (!appConfig.pushNotificationsEnabled) EitherT.leftT(PushNotificationError.PushNotificationDisabled)
    else
      EitherT {
        headers
          .get(Constants.XClientIdHeader)
          .map {
            clientId =>
              pushNotificationsConnector
                .postAssociation(
                  movementId,
                  PushNotificationsAssociation(
                    ClientId(clientId),
                    movementType,
                    headers.get(Constants.XCallbackBoxIdHeader).map(BoxId.apply),
                    enrollmentEORINumber
                  )
                )
                .map(Right(_))
                .recover {
                  case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PushNotificationError.BoxNotFound)
                  case NonFatal(thr) =>
                    logger.error(s"Unable to associate notification : ${thr.getMessage}", thr)
                    Left(PushNotificationError.UnexpectedError(thr = Some(thr)))
                }
          }
          .getOrElse(Future.successful(Left(PushNotificationError.MissingClientId)))
      }

  override def update(
    movementId: MovementId
  )(implicit headerCarrier: HeaderCarrier, executionContext: ExecutionContext): EitherT[Future, PushNotificationError, Unit] =
    if (!appConfig.pushNotificationsEnabled) EitherT.rightT(())
    else
      EitherT {
        pushNotificationsConnector
          .patchAssociation(movementId)
          .map(Right(_))
          .recover {
            case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PushNotificationError.AssociationNotFound)
            case NonFatal(thr) =>
              logger.error(s"Unable to update notification : ${thr.getMessage}", thr)
              Left(PushNotificationError.UnexpectedError(thr = Some(thr)))
          }
      }

  override def postPpnsNotification(movementId: MovementId, messageId: MessageId, body: JsValue)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PushNotificationError, Unit] =
    if (!appConfig.pushNotificationsEnabled) EitherT.rightT(())
    else
      EitherT {
        pushNotificationsConnector
          .postPpnsSubmissionNotification(movementId, messageId, body)
          .map(Right(_))
          .recover {
            case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PushNotificationError.BoxNotFound)
            case NonFatal(thr) =>
              logger.error(s"Unable to post notification : ${thr.getMessage}", thr)
              Left(PushNotificationError.UnexpectedError(thr = Some(thr)))
          }
      }

}
