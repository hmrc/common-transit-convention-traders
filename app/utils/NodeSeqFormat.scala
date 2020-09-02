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

import play.api.libs.json._

import scala.xml.{NodeSeq, XML}

trait NodeSeqFormat {
  implicit val writesNodeSeq: Writes[NodeSeq] = new Writes[NodeSeq] {
    override def writes(o: NodeSeq): JsValue = JsString(o.mkString)
  }

  implicit val readsNodeSeq: Reads[NodeSeq] = new Reads[NodeSeq] {
    override def reads(json: JsValue): JsResult[NodeSeq] = json match {
      case JsString(value) => JsSuccess(XML.loadString(value))
      case _               => JsError("Value cannot be parsed as XML")
    }
  }
}

object NodeSeqFormat extends NodeSeqFormat