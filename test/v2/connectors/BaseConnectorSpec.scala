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

package v2.connectors

import io.lemonlabs.uri.UrlPath
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import v2.models.request.MessageType

class BaseConnectorSpec extends AnyFreeSpec with Matchers with MockitoSugar {

  object Harness extends V2BaseConnector {

    def validationRouteTest(messageType: MessageType): UrlPath = validationRoute(messageType)
  }

  "the validation URL for a cc015c message type on localhost should be as expected" in {
    val urlPath = Harness.validationRouteTest(MessageType.DepartureDeclaration)

    urlPath.toString() mustBe "/transit-movements-validator/message/IE015C/validate"
  }

}
