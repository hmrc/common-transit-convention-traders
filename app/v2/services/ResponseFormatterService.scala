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

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import v2.controllers.ErrorTranslator
import v2.models.JsonPayload
import v2.models.XmlPayload
import v2.models.errors.PresentationError
import v2.models.responses.MessageSummary
import v2.utils.StreamingUtils

import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[ResponseFormatterServiceImpl])
trait ResponseFormatterService {

  def formatMessageSummary(
    messageSummary: MessageSummary,
    acceptHeaderValue: String
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, mat: Materializer): EitherT[Future, PresentationError, MessageSummary]
}

@Singleton
class ResponseFormatterServiceImpl @Inject() (conversionService: ConversionService) extends ResponseFormatterService with ErrorTranslator {

  override def formatMessageSummary(
    messageSummary: MessageSummary,
    acceptHeaderValue: String
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, mat: Materializer): EitherT[Future, PresentationError, MessageSummary] =
    (acceptHeaderValue, messageSummary) match {
      case (VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON, MessageSummary(_, _, messageType, Some(XmlPayload(body)))) =>
        for {
          jsonSource <- conversionService.xmlToJson(messageType, Source.single(ByteString(body))).asPresentation
          jsonBody   <- StreamingUtils.convertSourceToString(jsonSource).asPresentation
        } yield messageSummary.copy(body = Some(JsonPayload(jsonBody)))
      case _ =>
        EitherT.rightT(messageSummary)
    }

}
