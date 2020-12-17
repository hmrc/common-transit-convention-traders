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

import javax.inject.Inject
import org.json.XML
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.libs.json.Json

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.xml.NodeSeq

class JsonHelper @Inject()(messageTranslation: MessageTranslation) {

  def convertXmlToJson(xml: NodeSeq): JsObject =
    Try(translateMessage(xml.toString)) match {
      case Success(data) => data
      case Failure(error) =>
        Logger.error(s"Failed to convert xml to json with error: ${error.getMessage}")
        Json.obj()
    }

  private def translateMessage(xml: String): JsObject =
    messageTranslation.translate(Json.parse(XML.toJSONObject(xml).toString).as[JsObject])
}
