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
import javax.inject.Inject
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.ExecutionContext

class MessageConnector @Inject()(http: HttpClient, appConfig: AppConfig) {

  def post(message: String, arrivalId: Option[String])(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    arrivalId match {
      case Some(id) =>
        val url = appConfig.traderAtDestinationUrl + "/common-transit-convention-trader-at-destination/message-notification/" + id
        http.POSTString(url, message)(HttpReads.readRaw, implicitly, implicitly)
      case None =>
        val url = appConfig.traderAtDestinationUrl + "/common-transit-convention-trader-at-destination/message-notification"
        http.POSTString(url, message)(HttpReads.readRaw, implicitly, implicitly)
    }
  }
}
