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

package v2.controllers

import cats.data.EitherT
import play.api.Logging
import play.api.libs.json.JsValue
import play.api.mvc.BaseController
import v2.models.errors.PresentationError
import v2.models.responses.UpscanResponse

import scala.concurrent.Future

trait UpscanResponseParser {
  self: BaseController with Logging =>

  def parseAndLogUpscanResponse(responseBody: JsValue): EitherT[Future, PresentationError, UpscanResponse] = {
    println("parseAndLogUpscanResponse...")
    EitherT {
      responseBody
        .validate[UpscanResponse]
        .map {
          upscanResponse =>
            logResponse(Some(upscanResponse))
            Future.successful(Right(upscanResponse))
        }
        .getOrElse {
          logResponse(None)
          Future.successful(Left(PresentationError.badRequestError("Unexpected Upscan callback response")))
        }
    }
  }

  private def logResponse(upscanResponse: Option[UpscanResponse]) =
    upscanResponse match {
      case None => logger.error("Unable to parse unexpected response from Upscan")
      case Some(UpscanResponse(_, reference, _, _, None)) =>
        logger.info(s"Received a successful response from Upscan callback for the following reference: $reference")
      case Some(UpscanResponse(_, reference, _, None, failureDetails)) =>
        logger.warn(
          s"Received a failure response from Upscan callback for the following reference: $reference. Failure reason: ${failureDetails.get.failureReason}. Failure message: ${failureDetails.get.message}"
        )
    }

}
