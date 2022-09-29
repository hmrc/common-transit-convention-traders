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

package v2.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import v2.base.StreamTestHelpers
import v2.base.TestActorSystem
import v2.models.errors.ExtractionError
import v2.models.request.MessageType

import scala.xml.NodeSeq

class MessagesXmlParsingServiceSpec
    extends AnyFreeSpec
    with TestActorSystem
    with Matchers
    with StreamTestHelpers
    with ScalaFutures
    with ScalaCheckPropertyChecks {

  val validXml: NodeSeq =
    <CC013C>
      <HolderOfTheTransitProcedure>
        <identificationNumber>GB1234</identificationNumber>
      </HolderOfTheTransitProcedure>
    </CC013C>

  val invalidMessageType: NodeSeq =
    <CC015C>
      <HolderOfTheTransitProcedure>
        <identificationNumber>GB1234</identificationNumber>
      </HolderOfTheTransitProcedure>
    </CC015C>

  "When handed an XML stream" - {
    val service = new MessagesXmlParsingServiceImpl

    "if it is valid, return an appropriate Message Data" in {
      val source = createStream(validXml)

      val result = service.extractMessageType(source)

      whenReady(result.value) {
        _ mustBe Right(MessageType.DeclarationAmendment)
      }
    }

    "if it doesn't have a valid message type, return ParseError.MessageTypeNotFound" in {
      val source = createStream(invalidMessageType)

      val result = service.extractMessageType(source)

      whenReady(result.value) {
        _ mustBe Left(ExtractionError.MessageTypeNotFound("Message Type"))
      }
    }
  }
}
