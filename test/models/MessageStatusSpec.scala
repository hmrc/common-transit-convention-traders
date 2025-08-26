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

package models

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.JsString
import play.api.libs.json.Json

class MessageStatusSpec extends AnyFlatSpec with Matchers {

  "MessageStatus" should "serialise correctly" in {
    Json.toJson[MessageStatus](MessageStatus.Processing) should be(JsString("Processing"))
  }

  "MessageStatus" should "deserialize correctly" in {
    JsString("Processing").validate[MessageStatus].get should be(MessageStatus.Processing)
  }
}
