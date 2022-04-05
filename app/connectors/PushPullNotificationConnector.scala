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

package connectors

import com.google.inject.Inject
import config.AppConfig
import config.Constants
import models.Box
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class PushPullNotificationConnector @Inject() (config: AppConfig, http: HttpClient) {

  def getBox(clientId: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Either[UpstreamErrorResponse, Box]] = {
    val url = s"${config.pushPullUrl}/box"
    val queryParams = Seq(
      "boxName"  -> Constants.BoxName,
      "clientId" -> clientId
    )

    http.GET[Either[UpstreamErrorResponse, Box]](url, queryParams)
  }
}
