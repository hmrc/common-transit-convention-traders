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
import v2.models.DepartureId
import v2.models.errors.PersistenceError
import v2.models.responses.DeclarationResponse
import v2.models.responses.DepartureResponse
import v2.models.responses.MessageResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[DeparturesServiceImpl])
trait DeparturesService {

  def saveDeclaration(eori: EORINumber, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, DeclarationResponse]

  def getMessage(eori: EORINumber, departureId: DepartureId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MessageResponse]

  def getMessageIds(eori: EORINumber, departureId: DepartureId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Seq[MessageId]]

  def getDeparture(eori: EORINumber, departureId: DepartureId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, DepartureResponse]

}

@Singleton
class DeparturesServiceImpl @Inject() (persistenceConnector: PersistenceConnector) extends DeparturesService {

  override def saveDeclaration(eori: EORINumber, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, DeclarationResponse] =
    EitherT(
      persistenceConnector
        .post(eori, source)
        .map(Right(_))
        .recover {
          case NonFatal(thr) => Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def getMessage(eori: EORINumber, departureId: DepartureId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, MessageResponse] =
    EitherT(
      persistenceConnector
        .getDepartureMessage(eori, departureId, messageId)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.MessageNotFound(departureId, messageId))
          case NonFatal(thr)                             => Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def getMessageIds(eori: EORINumber, departureId: DepartureId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, Seq[MessageId]] =
    EitherT(
      persistenceConnector
        .getDepartureMessageIds(eori, departureId)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.DepartureNotFound(departureId))
          case NonFatal(thr)                             => Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

  override def getDeparture(eori: EORINumber, departureId: DepartureId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, PersistenceError, DepartureResponse] =
    EitherT(
      persistenceConnector
        .getDeparture(eori, departureId)
        .map(Right(_))
        .recover {
          case UpstreamErrorResponse(_, NOT_FOUND, _, _) => Left(PersistenceError.DepartureNotFound(departureId))
          case NonFatal(thr)                             => Left(PersistenceError.UnexpectedError(Some(thr)))
        }
    )

}
