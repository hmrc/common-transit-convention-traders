/*
 * Copyright 2020 HM Revenue & Customs
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

import config.AppConfig
import connectors.util.CustomHttpReader
import connectors.util.CustomHttpReader.INTERNAL_SERVER_ERROR
import javax.inject.Inject
import models.domain.{Arrival, MovementMessage}
import play.api.libs.json.Reads
import play.api.mvc.{Headers, RequestHeader}
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import utils.Utils

import scala.concurrent.{ExecutionContext, Future}

class MessageConnector @Inject()(http: HttpClient, appConfig: AppConfig) extends BaseConnector {

  val rootUrl = appConfig.traderAtDestinationUrl + "/transit-movements-trader-at-destination/movements"

  def get(arrivalId: String, messageId: String)(implicit requestHeader: RequestHeader, hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, MovementMessage]] = {
    val url = rootUrl + s"/arrivals/${Utils.urlEncode(arrivalId)}/messages/${Utils.urlEncode(messageId)}"

    http.GET[HttpResponse](url, queryParams = Seq(), responseHeaders)(CustomHttpReader, enforceAuthHeaderCarrier(responseHeaders), ec).map { response =>
      extractIfSuccessful[MovementMessage](response)
    }
  }

  def post(message: String, arrivalId: String)(implicit requestHeader: RequestHeader, hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    val url = rootUrl + s"/arrivals/${Utils.urlEncode(arrivalId)}/messages"

    http.POSTString(url, message, requestHeaders)(CustomHttpReader, enforceAuthHeaderCarrier(requestHeaders), ec)
  }

  def getArrivalMessages(arrivalId: String)(implicit requestHeader: RequestHeader, hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, Arrival]] = {
    val url = rootUrl + s"/arrivals/${Utils.urlEncode(arrivalId)}/messages"

    http.GET[HttpResponse](url, queryParams = Seq(), responseHeaders)(CustomHttpReader, enforceAuthHeaderCarrier(responseHeaders), ec).map { response =>
      extractIfSuccessful[Arrival](response)
    }
  }
}
