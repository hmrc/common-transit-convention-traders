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
import v2.models.errors.PersistenceError
import v2.models.responses.ArrivalResponse
import v2.models.responses.MessageSummary
import v2.models.responses.MovementResponse

import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[ArrivalsServiceImpl])
trait ArrivalsService {

  def createArrival(eori: EORINumber, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, ArrivalResponse]

  def getArrival(eori: EORINumber, arrivalId: MovementId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MovementResponse]

  def getArrivalMessageIds(eori: EORINumber, arrivalId: MovementId, receivedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Seq[MessageSummary]]

  def getArrivalsForEori(eori: EORINumber, updatedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Seq[MovementResponse]]

  def getArrivalMessage(eori: EORINumber, arrivalId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MessageSummary]

}

@Singleton
class ArrivalsServiceImpl @Inject() (persistenceConnector: PersistenceConnector) extends ArrivalsService {

  override def createArrival(eori: EORINumber, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, ArrivalResponse] =
    EitherT(
      persistenceConnector
        .postArrival(eori, source)
        .map(Right(_))
        .recover {
          case NonFatal(thr) => Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def getArrival(eori: EORINumber, arrivalId: MovementId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MovementResponse] = EitherT(
    persistenceConnector
      .getArrival(eori, arrivalId)
      .map(Right(_))
      .recover {
        case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.ArrivalNotFound(arrivalId))
        case NonFatal(thr)                             => Left(PersistenceError.UnexpectedError(Some(thr)))
      }
  )

  override def getArrivalMessageIds(eori: EORINumber, arrivalId: MovementId, receivedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Seq[MessageSummary]] =
    EitherT(
      persistenceConnector
        .getArrivalMessageIds(eori, arrivalId, receivedSince)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.ArrivalNotFound(arrivalId))
          case NonFatal(thr)                             => Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def getArrivalMessage(eori: EORINumber, arrivalId: MovementId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MessageSummary] =
    EitherT(
      persistenceConnector
        .getArrivalMessage(eori, arrivalId, messageId)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MessageNotFound(arrivalId, messageId))
          case NonFatal(thr)                             => Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def getArrivalsForEori(eori: EORINumber, updatedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Seq[MovementResponse]] = EitherT(
    persistenceConnector
      .getArrivalsForEori(eori, updatedSince)
      .map(Right(_))
      .recover {
        case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.ArrivalsNotFound(eori))
        case NonFatal(thr)                             => Left(PersistenceError.UnexpectedError(Some(thr)))
      }
  )
}
