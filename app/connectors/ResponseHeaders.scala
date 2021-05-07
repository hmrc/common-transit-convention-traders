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

import uk.gov.hmrc.http.HttpReads
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpResponse

final case class ResponseHeaders[A](headers: Map[String, Seq[String]], responseData: A) {

  def header(key: String): Option[String] =
    headers.get(key).flatMap(_.headOption)
}

object ResponseHeaders {

  implicit def responseHeadersHttpReads[A: HttpReads]: HttpReads[ResponseHeaders[A]] =
    HttpReads[HttpResponse].flatMap {
      response =>
        HttpReads[A].map(
          a => ResponseHeaders(response.headers, a)
        )
    }
}
