/*
 * Copyright 2021 HM Revenue & Customs
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

package connectors

import connectors.util.CustomHttpReader
import connectors.util.CustomHttpReader.INTERNAL_SERVER_ERROR
import models.ChannelType.api
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.json.Reads
import play.api.mvc.RequestHeader
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions, HttpResponse}

class BaseConnector extends HttpErrorFunctions {

  protected val channelHeader: (String, String) = ("channel", api.toString)
  protected val requestHeaders: Seq[(String, String)] =
    Seq((HeaderNames.CONTENT_TYPE, MimeTypes.XML), channelHeader)

  protected val responseHeaders: Seq[(String, String)] =
    Seq((HeaderNames.CONTENT_TYPE, MimeTypes.JSON), channelHeader)

  protected val arrivalRoute = "/transit-movements-trader-at-destination/movements/arrivals/"

  protected val departureRoute = "/transits-movements-trader-at-departure/movements/departures/"

  protected def extractIfSuccessful[T](response: HttpResponse)(implicit reads: Reads[T]): Either[HttpResponse, T] =
    if(is2xx(response.status)) {
      response.json.asOpt[T] match {
        case Some(instance) => Right(instance)
        case _ => Left(CustomHttpReader.recode(INTERNAL_SERVER_ERROR, response))
      }
    } else Left(response)

  protected def enforceAuthHeaderCarrier(extraHeaders: Seq[(String, String)])(implicit requestHeader: RequestHeader, headerCarrier: HeaderCarrier): HeaderCarrier = {
    val newHeaderCarrier = headerCarrier
      .copy(authorization = Some(Authorization(requestHeader.headers.get(HeaderNames.AUTHORIZATION).getOrElse(""))))
      .withExtraHeaders(extraHeaders: _*)
    newHeaderCarrier
  }
}
