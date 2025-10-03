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

package services

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import cats.data.EitherT
import com.google.inject.Inject
import com.google.inject.Singleton
import connectors.PersistenceConnector
import models.Version
import models.common.EORINumber
import models.common.ItemCount
import models.common.LocalReferenceNumber
import models.common.MessageId
import models.common.MovementId
import models.common.MovementReferenceNumber
import models.common.MovementType
import models.common.PageNumber
import models.common.errors.PersistenceError
import models.request.MessageType
import models.request.MessageUpdate
import play.api.Logging
import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import models.responses.*

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@Singleton
class PersistenceService @Inject() (persistenceConnector: PersistenceConnector) extends Logging {

  def createMovement(eori: EORINumber, movementType: MovementType, source: Option[Source[ByteString, ?]], version: Version)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MovementResponse] =
    EitherT(
      persistenceConnector
        .postMovement(eori, movementType, source, version)
        .map {
          movementResponse => Right(movementResponse)
        }
        .recover {
          case NonFatal(thr) =>
            logger.error(s"Unable to create movement : ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  def getMessage(eori: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId, version: Version)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MessageSummary] =
    EitherT(
      persistenceConnector
        .getMessage(eori, movementType, movementId, messageId, version)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MessageNotFound(movementId, messageId))
          case NonFatal(thr)                             =>
            logger.error(s"Unable to get message : ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  def getMessages(
    eori: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    receivedSince: Option[OffsetDateTime],
    page: Option[PageNumber],
    count: ItemCount,
    receivedUntil: Option[OffsetDateTime],
    version: Version
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, PaginationMessageSummary] =
    EitherT(
      persistenceConnector
        .getMessages(eori, movementType, movementId, receivedSince, page, count, receivedUntil, version)
        .map {
          summary => messages(page, summary)
        }
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MovementNotFound(movementId, movementType))
          case NonFatal(thr)                             =>
            logger.error(s"Unable to get messages : ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  def getMovement(eori: EORINumber, movementType: MovementType, movementId: MovementId, version: Version)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MovementSummary] =
    EitherT(
      persistenceConnector
        .getMovement(eori, movementType, movementId, version)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MovementNotFound(movementId, movementType))
          case NonFatal(thr)                             =>
            logger.error(s"Unable to get movement : ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  def getMovements(
    eori: EORINumber,
    movementType: MovementType,
    updatedSince: Option[OffsetDateTime],
    movementEORI: Option[EORINumber],
    movementReferenceNumber: Option[MovementReferenceNumber],
    page: Option[PageNumber],
    count: ItemCount,
    receivedUntil: Option[OffsetDateTime],
    localReferenceNumber: Option[LocalReferenceNumber],
    version: Version
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, PaginationMovementSummary] = EitherT {
    persistenceConnector
      .getMovements(eori, movementType, updatedSince, movementEORI, movementReferenceNumber, page, count, receivedUntil, localReferenceNumber, version)
      .map {
        summary => movements(page, summary)
      }
      .recover {
        case NonFatal(thr) =>
          logger.error(s"Unable to get movements : ${thr.getMessage}", thr)
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

  def addMessage(movementId: MovementId, movementType: MovementType, messageType: Option[MessageType], source: Option[Source[ByteString, ?]], version: Version)(
    implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, UpdateMovementResponse] =
    EitherT(
      persistenceConnector
        .postMessage(movementId, messageType, source, version)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MovementNotFound(movementId, movementType))
          case NonFatal(thr)                             =>
            logger.error(s"Unable to add message : ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  def updateMessage(
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    body: MessageUpdate,
    version: Version
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Unit] =
    EitherT(
      persistenceConnector
        .patchMessage(eoriNumber, movementType, movementId, messageId, body, version)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MessageNotFound(movementId, messageId))
          case NonFatal(thr)                             =>
            logger.error(s"Unable to update message : ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  def updateMessageBody(
    messageType: MessageType,
    eoriNumber: EORINumber,
    movementType: MovementType,
    movementId: MovementId,
    messageId: MessageId,
    source: Source[ByteString, ?],
    version: Version
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Unit] =
    EitherT(
      persistenceConnector
        .updateMessageBody(messageType, eoriNumber, movementType, movementId, messageId, source, version)
        .map(Right(_))
        .recover {
          case NonFatal(thr) =>
            logger.error(s"Unable to update message body : ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  def getMessageBody(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId, version: Version)(implicit
    hc: HeaderCarrier,
    mat: Materializer,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Source[ByteString, ?]] =
    EitherT(
      persistenceConnector
        .getMessageBody(eoriNumber, movementType, movementId, messageId, version)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MessageNotFound(movementId, messageId))
          case NonFatal(thr)                             =>
            logger.error(s"Unable to get message body : ${thr.getMessage}", thr)
            Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )
}
