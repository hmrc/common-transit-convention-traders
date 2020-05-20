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

import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import config.AppConfig
import connectors.util.CustomHttpReader
import javax.inject.Inject
import models.domain.{Arrival, ArrivalWithMessages, Arrivals}
import play.api.mvc.{Headers, RequestHeader}
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import utils.Utils

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

class ArrivalConnector @Inject()(http: HttpClient, appConfig: AppConfig) extends BaseConnector {

  def post(message: String)(implicit requestHeader: RequestHeader, hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    val url = appConfig.traderAtDestinationUrl + arrivalRoute

    http.POSTString(url, message)(CustomHttpReader, enforceAuthHeaderCarrier(requestHeaders), ec)
  }

  def put(message: String, arrivalId: String)(implicit requestHeader: RequestHeader, headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    val url = appConfig.traderAtDestinationUrl + arrivalRoute + Utils.urlEncode(arrivalId)

    http.PUTString(url, message)(CustomHttpReader, enforceAuthHeaderCarrier(requestHeaders), ec)
  }

  def get(arrivalId: String)(implicit requestHeader: RequestHeader, hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, Arrival]] = {
    val url = appConfig.traderAtDestinationUrl + arrivalRoute + Utils.urlEncode(arrivalId)

    http.GET[HttpResponse](url, queryParams = Seq(), responseHeaders)(CustomHttpReader, enforceAuthHeaderCarrier(responseHeaders), ec).map { response =>
      extractIfSuccessful[Arrival](response)
    }
  }

  def getForEori()(implicit requestHeader: RequestHeader, hc: HeaderCarrier, ec: ExecutionContext): Future[Either[HttpResponse, Arrivals]] = {
    val url = appConfig.traderAtDestinationUrl + arrivalRoute

    http.GET[HttpResponse](url, queryParams = Seq(), responseHeaders)(CustomHttpReader, enforceAuthHeaderCarrier(responseHeaders), ec).map { response =>
      extractIfSuccessful[Arrivals](response)
    }
  }
}
