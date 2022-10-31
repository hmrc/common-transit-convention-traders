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

package v2.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import play.api.libs.json.JsObject
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import v2.models.MessageId
import v2.models.MovementId
import v2.models.errors.PresentationError
import v2.models.responses.MessageSummary
import v2.models.responses.hateoas.HateoasDepartureMessageResponse
import v2.services.ConversionService
import v2.utils.StreamingUtils

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

object ResponseFormatter extends ErrorTranslator {

  def formatMessageSummaryResponse(
    conversionService: ConversionService,
    movementId: MovementId,
    messageId: MessageId,
    messageSummary: MessageSummary,
    acceptHeaderValue: String
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, mat: Materializer): EitherT[Future, PresentationError, JsObject] =
    (acceptHeaderValue, messageSummary) match {
      case (VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON, MessageSummary(_, _, messageType, Some(body))) =>
        for {
          jsonSource <- conversionService.xmlToJson(messageType, Source.single(ByteString(body))).asPresentation
          jsonBody   <- StreamingUtils.convertSourceToString(jsonSource).asPresentation
        } yield HateoasDepartureMessageResponse(movementId, messageId, messageSummary.copy(body = Some(jsonBody)))
      case _ =>
        EitherT.rightT(HateoasDepartureMessageResponse(movementId, messageId, messageSummary))
    }

}
