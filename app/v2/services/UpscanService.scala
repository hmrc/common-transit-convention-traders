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
import uk.gov.hmrc.http.HeaderCarrier
import v2.connectors.UpscanConnector
import v2.connectors.V2BaseConnector
import v2.models.MovementId
import v2.models.errors.UpscanInitiateError
import v2.models.request.UpscanInitiate
import v2.models.responses.UpscanInitiateResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[UpscanServiceImpl])
trait UpscanService {

  def upscanInitiate(movementId: MovementId)(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext
  ): EitherT[Future, UpscanInitiateError, UpscanInitiateResponse]

}

class UpscanServiceImpl @Inject() (
  upscanConnector: UpscanConnector,
  appConfig: AppConfig
) extends UpscanService
    with V2BaseConnector {

  override def upscanInitiate(movementId: MovementId)(implicit
    headerCarrier: HeaderCarrier,
    executionContext: ExecutionContext
  ): EitherT[Future, UpscanInitiateError, UpscanInitiateResponse] =
    EitherT {
      upscanConnector
        .upscanInitiate(movementId)
        .map(Right(_))
        .recover {
          case NonFatal(thr) => Left(UpscanInitiateError.UnexpectedError(thr = Some(thr)))
        }
    }
}
