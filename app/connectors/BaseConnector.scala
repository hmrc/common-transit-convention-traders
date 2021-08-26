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

import config.Constants._
import connectors.util.CustomHttpReader
import io.lemonlabs.uri.UrlPath
import models.ChannelType.api
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.http.Status._
import play.api.libs.json.Reads
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.http.HttpResponse

import java.time.format.DateTimeFormatter

class BaseConnector extends HttpErrorFunctions {

  /** Adds an empty Authorization header if none is present in the request.
    * If one is present already, no extra header is added because it will be propagated via HeaderCarrier.
    *
    * @param hc the HeaderCarrier for the current request
    * @return the explicit headers to use in a downstream request if appropriate
    */
  protected def enforceAuthHeader(implicit hc: HeaderCarrier): Seq[(String, String)] =
    hc.authorization
      .map(
        _ => Seq.empty
      )
      .getOrElse(Seq(HeaderNames.AUTHORIZATION -> ""))

  /** The headers to use for a downstream POST or PUT request.
    *
    * For the CTC Traders API this means an Accept header that requests JSON,
    * a Content Type header for an XML body, and the Channel header indicating
    * that this request was generated via the API rather than via a frontend
    * microservice.
    *
    * If an API Platform X-Client-Id header is present this will be propagated as well.
    *
    * If no Authorization header is present an empty Authorization header is added.
    *
    * @param hc the [[uk.gov.hmrc.http.HeaderCarrier]] for this request
    * @return the explicit headers to use in a downstream request
    */
  protected def postPutXmlHeaders(implicit hc: HeaderCarrier): Seq[(String, String)] =
    enforceAuthHeader ++ hc.headers(Seq(ClientIdHeader)) ++ Seq(
      HeaderNames.ACCEPT       -> ContentTypes.JSON,
      HeaderNames.CONTENT_TYPE -> ContentTypes.XML,
      ChannelHeader            -> api.name
    )

  /** The headers to use for a downstream GET request.
    *
    * For the CTC Traders API this means an Accept header that requests JSON
    * and the Channel header indicating that this request was generated via
    * the API rather than via a frontend microservice.
    *
    * If an API Platform X-Client-Id header is present this will be propagated as well.
    *
    * If no Authorization header is present an empty Authorization header is added.
    *
    * @param hc the [[uk.gov.hmrc.http.HeaderCarrier]] for this request
    * @return the explicit headers to use in a downstream request
    */
  protected def getJsonHeaders(implicit hc: HeaderCarrier): Seq[(String, String)] =
    enforceAuthHeader ++ hc.headers(Seq(ClientIdHeader)) ++ Seq(
      HeaderNames.ACCEPT -> ContentTypes.JSON,
      ChannelHeader      -> api.name
    )

  protected val arrivalRoute = UrlPath.parse("/transit-movements-trader-at-destination/movements/arrivals")

  protected val departureRoute = UrlPath.parse("/transits-movements-trader-at-departure/movements/departures")

  protected val queryDateFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

  protected def extractIfSuccessful[T](response: HttpResponse)(implicit reads: Reads[T]): Either[HttpResponse, T] =
    if (is2xx(response.status)) {
      response.json.asOpt[T] match {
        case Some(instance) => Right(instance)
        case _              => Left(CustomHttpReader.recode(INTERNAL_SERVER_ERROR, response))
      }
    } else Left(response)
}
