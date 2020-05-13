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

import connectors.util.CustomHttpReader
import connectors.util.CustomHttpReader.INTERNAL_SERVER_ERROR
import play.api.libs.json.Reads
import play.mvc.Http.{HeaderNames, MimeTypes}
import uk.gov.hmrc.http.{HeaderCarrier, HttpErrorFunctions, HttpResponse}

trait BaseConnector extends HttpErrorFunctions{
  protected def requestHeaders()(implicit hc: HeaderCarrier): Seq[(String, String)] =
    hc.headers ++ Seq((HeaderNames.CONTENT_TYPE, MimeTypes.XML))

  protected def responseHeaders()(implicit hc: HeaderCarrier): Seq[(String, String)] =
    hc.headers ++ Seq((HeaderNames.CONTENT_TYPE, MimeTypes.JSON))

  protected def extractIfSuccessful[T](response: HttpResponse)(implicit reads: Reads[T]): Either[HttpResponse, T] =
    if(is2xx(response.status)) {
      response.json.asOpt[T] match {
        case Some(instance) => Right(instance)
        case _ => Left(CustomHttpReader.recode(INTERNAL_SERVER_ERROR, response))
      }
    } else Left(response)

}