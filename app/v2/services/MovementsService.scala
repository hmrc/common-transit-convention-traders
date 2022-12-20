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
import com.google.inject.Singleton
import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.connectors.PersistenceConnector
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementType
import v2.models.errors.PersistenceError
import v2.models.request.MessageType
import v2.models.responses.MessageSummary
import v2.models.responses.MovementResponse
import v2.models.responses.MovementSummary
import v2.models.responses.UpdateMovementResponse

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[MovementsServiceImpl])
trait MovementsService {

  def createMovement(eori: EORINumber, movementType: MovementType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MovementResponse]

  def getMessage(eori: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MessageSummary]

  def getMessages(eori: EORINumber, movementType: MovementType, movementId: MovementId, receivedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Seq[MessageSummary]]

  def getMovement(eori: EORINumber, movementType: MovementType, movementId: MovementId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MovementSummary]

  def getMovements(eori: EORINumber, movementType: MovementType, updatedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Seq[MovementSummary]]

  def updateMovement(movementId: MovementId, movementType: MovementType, messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, UpdateMovementResponse]

}

@Singleton
class MovementsServiceImpl @Inject() (persistenceConnector: PersistenceConnector) extends MovementsService {

  override def createMovement(eori: EORINumber, movementType: MovementType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MovementResponse] =
    EitherT(
      persistenceConnector
        .postMovement(eori, movementType, source)
        .map(Right(_))
        .recover {
          case NonFatal(thr) =>
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def getMessage(eori: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MessageSummary] =
    EitherT(
      persistenceConnector
        .getMessage(eori, movementType, movementId, messageId)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MessageNotFound(movementId, messageId))
          case NonFatal(thr)                             => Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def getMessages(eori: EORINumber, movementType: MovementType, movementId: MovementId, receivedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Seq[MessageSummary]] =
    EitherT(
      persistenceConnector
        .getMessages(eori, movementType, movementId, receivedSince)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MovementNotFound(movementId, movementType))
          case NonFatal(thr)                             => Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def getMovement(eori: EORINumber, movementType: MovementType, movementId: MovementId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MovementSummary] =
    EitherT(
      persistenceConnector
        .getMovement(eori, movementType, movementId)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MovementNotFound(movementId, movementType))
          case NonFatal(thr)                             => Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def getMovements(eori: EORINumber, movementType: MovementType, updatedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Seq[MovementSummary]] = EitherT(
    persistenceConnector
      .getMovements(eori, movementType, updatedSince)
      .map(Right(_))
      .recover {
        case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MovementsNotFound(eori, movementType))
        case NonFatal(thr)                             => Left(PersistenceError.UnexpectedError(Some(thr)))
      }
  )

  override def updateMovement(movementId: MovementId, movementType: MovementType, messageType: MessageType, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, UpdateMovementResponse] =
    EitherT(
      persistenceConnector
        .postMessage(movementId, messageType, source)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MovementNotFound(movementId, movementType))
          case NonFatal(thr)                             => Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

}
