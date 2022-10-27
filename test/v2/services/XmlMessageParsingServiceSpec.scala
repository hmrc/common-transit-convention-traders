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
import uk.gov.hmrc.http.HeaderCarrier
import v2.base.StreamTestHelpers
import v2.base.TestActorSystem
import v2.models.errors.ExtractionError
import v2.models.request.MessageType
import concurrent.ExecutionContext.Implicits.global
import scala.xml.NodeSeq

class XmlMessageParsingServiceSpec
    extends AnyFreeSpec
    with TestActorSystem
    with Matchers
    with StreamTestHelpers
    with ScalaFutures
    with ScalaCheckPropertyChecks {

  val validXml: NodeSeq =
    <ncts:CC013C PhaseID="NCTS5.0" xmlns:ncts="http://ncts.dgtaxud.ec">
      <HolderOfTheTransitProcedure>
        <identificationNumber>GB1234</identificationNumber>
      </HolderOfTheTransitProcedure>
    </ncts:CC013C>

  val invalidMessageType: NodeSeq =
    <ncts:CC015C PhaseID="NCTS5.0" xmlns:ncts="http://ncts.dgtaxud.ec">
      <CC013C>
        <identificationNumber>GB1234</identificationNumber>
      </CC013C>
    </ncts:CC015C>

  val withNoMessageTypeEntry: NodeSeq =
    <ncts:HolderOfTheTransitProcedure PhaseID="NCTS5.0" xmlns:ncts="http://ncts.dgtaxud.ec">
      <identificationNumber>GB1234</identificationNumber>
    </ncts:HolderOfTheTransitProcedure>
  implicit val hc: HeaderCarrier = HeaderCarrier()

  "extractMessageType and then" - {
    "if it is valid, return an appropriate Message Type" in {
      val xmlParsingService = new XmlMessageParsingServiceImpl()
      val payload           = createStream(validXml)
      val response =
        xmlParsingService.extractMessageType(payload)

      whenReady(response.value) {
        _ mustBe Right(MessageType.DeclarationAmendment)
      }
    }

    "if it doesn't have a valid message type, return ExtractionError.MessageTypeNotFound" in {
      val xmlParsingService = new XmlMessageParsingServiceImpl()
      val payload           = createStream(invalidMessageType)
      val response =
        xmlParsingService.extractMessageType(payload)

      whenReady(response.value) {
        _ mustBe Left(ExtractionError.MessageTypeNotFound("CC015C"))
      }
    }

    "if it doesn't have a message type entry, return ExtractionError.MessageTypeNotFound" in {
      val xmlParsingService = new XmlMessageParsingServiceImpl()
      val payload           = createStream(withNoMessageTypeEntry)
      val response =
        xmlParsingService.extractMessageType(payload)

      whenReady(response.value) {
        _ mustBe Left(ExtractionError.MessageTypeNotFound("HolderOfTheTransitProcedure"))
      }
    }
  }
}