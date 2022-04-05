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

package connectors.util

import play.api.Logging
import play.api.http.Status
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.HttpResponse

object CustomHttpReader extends HttpReads[HttpResponse] with HttpErrorFunctions with Status with Logging {

  override def read(method: String, url: String, response: HttpResponse): HttpResponse = {
    logger.debug(s"CustomHttpReader Log\nstatus: ${response.status}\nbody: ${response.body}\nheaders: ${response.headers.map {
      x =>
        s"\n  ${x._1} : ${x._2}"
    }}")
    response.status match {
      case LOCKED | BAD_GATEWAY => recode(INTERNAL_SERVER_ERROR, response)
      case _                    => response
    }
  }

  def recode(newCode: Int, response: HttpResponse) = HttpResponse(newCode, response.body, response.headers)
}
