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

package connectors.util

import play.api.Logger
import play.api.http.Status
import uk.gov.hmrc.http.{HttpErrorFunctions, HttpReads, HttpResponse}

object CustomHttpReader extends HttpReads[HttpResponse] with HttpErrorFunctions with Status {
  override def read(method: String, url: String, response: HttpResponse): HttpResponse = {
    Logger.debug(s"CustomHttpReader Log\nstatus: ${response.status}\nbody: ${response.body}\nheaders: ${response.allHeaders.map {
      x =>
        s"\n  ${x._1} : ${x._2}"
    }}")
    response.status match {
      case LOCKED | BAD_GATEWAY => recode(INTERNAL_SERVER_ERROR, response)
      case _ => response
    }
  }

  def recode(newCode: Int, response: HttpResponse) = HttpResponse(newCode, Some(response.json), response.allHeaders, Some(response.body))
}
