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

package utils

import java.net.{URI, URLEncoder}

import scala.util.Try

object Utils {
  val acceptHeaderPattern = "^application/vnd[.]{1}hmrc[.]{1}(.*?)[+]{1}(.*)$".r

  def lastFragment(location: String): String =
    URI.create(location).getPath.split("/").last

  def urlEncode(str: String): String =
    URLEncoder.encode(str, "UTF-8")
}
