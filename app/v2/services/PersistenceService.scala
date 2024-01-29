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

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import play.api.Logging
import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.connectors.PersistenceConnector
import v2.models._
import v2.models.errors.PersistenceError
import v2.models.request.MessageType
import v2.models.request.MessageUpdate
import v2.models.responses._

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[PersistenceServiceImpl])
trait PersistenceService {

  def createMovement(eori: EORINumber, movementType: MovementType, source: Option[Source[ByteString, _]])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MovementResponse]

  def getMessage(eori: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MessageSummary]

  def getMessages(
    eori: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    receivedSince: Option[OffsetDateTime],
    page: Option[PageNumber],
    count: ItemCount,
    receivedUntil: Option[OffsetDateTime]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, PaginationMessageSummary]

  def getMovement(eori: EORINumber, movementType: MovementType, movementId: MovementId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MovementSummary]

  def getMovements(
    eori: EORINumber,
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber],
    page: Option[PageNumber],
    count: ItemCount,
    receivedUntil: Option[OffsetDateTime],
    localReferenceNumber: Option[LocalReferenceNumber]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, PaginationMovementSummary]

  def addMessage(movementId: MovementId, movementType: MovementType, messageType: Option[MessageType], source: Option[Source[ByteString, _]])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, UpdateMovementResponse]

  def updateMessage(
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    body: MessageUpdate
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Unit]

  def updateMessageBody(
    messageType: MessageType,
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    source: Source[ByteString, _]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Unit]

  def getMessageBody(
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId
  )(implicit
    hc: HeaderCarrier,
    mat: Materializer,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Source[ByteString, _]]
}

@Singleton
class PersistenceServiceImpl @Inject() (persistenceConnector: PersistenceConnector) extends PersistenceService with Logging {

  override def createMovement(eori: EORINumber, movementType: MovementType, source: Option[Source[ByteString, _]])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MovementResponse] =
    EitherT(
      persistenceConnector
        .postMovement(eori, movementType, source)
        .map {
          movementResponse => Right(movementResponse)
        }
        .recover {
          case NonFatal(thr) =>
            logger.error(s"Unable to create movement due to an exception ${thr.getMessage}", thr)
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
          case NonFatal(thr) =>
            logger.error(s"Unable to get message due to an exception ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def getMessages(
    eori: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    receivedSince: Option[OffsetDateTime],
    page: Option[PageNumber],
    count: ItemCount,
    receivedUntil: Option[OffsetDateTime]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, PaginationMessageSummary] =
    EitherT(
      persistenceConnector
        .getMessages(eori, movementType, movementId, receivedSince, page, count, receivedUntil)
        .map {
          summary => messages(page, summary)
        }
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MovementNotFound(movementId, movementType))
          case NonFatal(thr) =>
            logger.error(s"Unable to get messages due to an exception ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
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
          case NonFatal(thr) =>
            logger.error(s"Unable to get movement due to an exception ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def getMovements(
    eori: EORINumber,
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber],
    page: Option[PageNumber],
    count: ItemCount,
    receivedUntil: Option[OffsetDateTime],
    localReferenceNumber: Option[LocalReferenceNumber]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, PaginationMovementSummary] = EitherT {
    persistenceConnector
      .getMovements(eori, movementType, updatedSince, movementEORI, movementReferenceNumber, page, count, receivedUntil, localReferenceNumber)
      .map {
        summary => movements(page, summary)
      }
      .recover {
        case NonFatal(thr) =>
          logger.error(s"Unable to get movements due to an exception ${thr.getMessage}", thr)
          Left(PersistenceError.UnexpectedError(Some(thr)))
      }
  }

  private def isEmptyPage(page: Option[PageNumber], numberOfItemsForPage: Int): Boolean =
    (page, numberOfItemsForPage) match {
      case (Some(PageNumber(1)), _)                                     => false
      case (Some(_), numberOfItemsForPage) if numberOfItemsForPage == 0 => true
      case (_, _)                                                       => false
    }

  private def movements(page: Option[PageNumber], summary: PaginationMovementSummary) =
    if (isEmptyPage(page, summary.movementSummary.length))
      Left(PersistenceError.PageNotFound)
    else
      Right(summary)

  private def messages(page: Option[PageNumber], summary: PaginationMessageSummary) =
    if (isEmptyPage(page, summary.messageSummary.length))
      Left(PersistenceError.PageNotFound)
    else
      Right(summary)

  override def addMessage(movementId: MovementId, movementType: MovementType, messageType: Option[MessageType], source: Option[Source[ByteString, _]])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, UpdateMovementResponse] =
    EitherT(
      persistenceConnector
        .postMessage(movementId, messageType, source)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MovementNotFound(movementId, movementType))
          case NonFatal(thr) =>
            logger.error(s"Unable to add message due to an exception ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def updateMessage(
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    body: MessageUpdate
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Unit] =
    EitherT(
      persistenceConnector
        .patchMessage(eoriNumber, movementType, movementId, messageId, body)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MessageNotFound(movementId, messageId))
          case NonFatal(thr) =>
            logger.error(s"Unable to update message due to an exception ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def updateMessageBody(
    messageType: MessageType,
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    source: Source[ByteString, _]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Unit] =
    EitherT(
      persistenceConnector
        .updateMessageBody(messageType, eoriNumber, movementType, movementId, messageId, source)
        .map(Right(_))
        .recover {
          case NonFatal(thr) =>
            logger.error(s"Unable to update message body due to an exception ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def getMessageBody(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    mat: Materializer,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Source[ByteString, _]] =
    EitherT(
      persistenceConnector
        .getMessageBody(eoriNumber, movementType, movementId, messageId)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MessageNotFound(movementId, messageId))
          case NonFatal(thr) =>
            logger.error(s"Unable to get message body due to an exception ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

}
