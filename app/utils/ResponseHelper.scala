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

package utils

import models.response.JsonClientErrorResponse
import play.api.Logging
import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse

trait ResponseHelper extends Results with Status with HttpErrorFunctions with Logging {

  def handleNon2xx(response: HttpResponse): Result = {
    logger.debug(s"ResponseHelper Log\nstatus: ${response.status}\nbody: ${response.body}\nheaders: ${response.headers.map {
      x =>
        s"\n  ${x._1} : ${x._2}"
    }}")
    response.status match {
      case s if is4xx(s) =>
        if (response.body != null) Status(response.status)(Json.toJson(JsonClientErrorResponse(response.status, response.body))) else Status(response.status)
      case _ =>
        if (response.body != null) logger.error(s"Internal server error occurred : ${response.body}")
        Status(response.status)
    }
  }

  def handleNon2xx(response: UpstreamErrorResponse): Result = {
    logger.debug(s"ResponseHelper Log\nstatus: ${response.statusCode}\nheaders: ${response.headers.map {
      x =>
        s"\n  ${x._1} : ${x._2}"
    }}")
    response.statusCode match {
      case s if is4xx(s) =>
        if (response.message != null) Status(response.statusCode)(Json.toJson(JsonClientErrorResponse(response.statusCode, response.message)))
        else Status(response.statusCode)
      case _ => Status(response.statusCode)
    }
  }
}
