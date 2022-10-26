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
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import uk.gov.hmrc.http.HeaderCarrier
import v2.models.errors.ConversionError
import v2.models.errors.PresentationError
import v2.models.request.MessageType
import v2.services.ConversionService

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

sealed trait MessageFormat extends ErrorTranslator {

  def convertSourceToString(
    source: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): EitherT[Future, PresentationError, String] = EitherT {
    source
      .reduce(_ ++ _)
      .map(_.utf8String)
      .runWith(Sink.head[String])
      .map(Right[PresentationError, String])
      .recover {
        case NonFatal(ex) => Left[PresentationError, String](PresentationError.internalServiceError(cause = Some(ex)))
      }
  }
}

object XmlMessage extends MessageFormat {

  def convertToJson(messageType: MessageType, body: Option[String], conversionService: ConversionService)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    materializer: Materializer
  ): EitherT[Future, PresentationError, Option[String]] = {
    if (body.isEmpty) EitherT.rightT(None)
    for {
      jsonSource <- conversionService.xmlToJson(messageType, Source.single(ByteString(body.get))).asPresentation
      jsonBody   <- convertSourceToString(jsonSource)
    } yield jsonBody
  }

}
