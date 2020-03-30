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

import uk.gov.hmrc.http.HttpResponse
import config.AppConfig
import javax.inject.Inject
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

class ArrivalConnector @Inject()(http: HttpClient, appConfig: AppConfig) {

  val arrivalRoute = "/transit-movements-trader-at-destination/movements/arrivals/"

  def put(arrivalId: String, message: NodeSeq)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    val url = appConfig.traderAtDestinationUrl + arrivalRoute + arrivalId
    http.PUTString(url, message.toString)(HttpReads.readRaw, implicitly, implicitly)
  }

}
