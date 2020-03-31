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

package controllers

import java.net.URI

import play.api.libs.json.JsError
import play.api.libs.json.JsResult.Exception

import scala.util.{Failure, Success, Try}

case class LocationHeader(arrivalId: String)

object LocationHeader {
  def parse(location: String): LocationHeader = {
    Try(new URI(location).getPath.split("/").last) match {
      case Success(value) =>  new LocationHeader(arrivalId = value)
      case Failure(_) => throw Exception(JsError(s"Unable to extract arrivalId from locationHeader: $location"))
    }
  }
}
