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

package services

import base.StreamTestHelpers
import base.TestActorSystem
import models.common.errors.ExtractionError
import models.request.MessageType
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.NodeSeq

class XmlMessageParsingServiceSpec
    extends AnyFreeSpec
    with TestActorSystem
    with Matchers
    with StreamTestHelpers
    with ScalaFutures
    with ScalaCheckPropertyChecks {

  val validDepartureXml: NodeSeq =
    <ncts:CC013C PhaseID="NCTS5.0" xmlns:ncts="http://ncts.dgtaxud.ec">
      <HolderOfTheTransitProcedure>
        <identificationNumber>GB1234</identificationNumber>
      </HolderOfTheTransitProcedure>
    </ncts:CC013C>

  val validArrivalXml: NodeSeq =
    <ncts:CC044C PhaseID="NCTS5.0" xmlns:ncts="http://ncts.dgtaxud.ec">
      <TransitOperation>
        <MRN>27WF9X1FQ9RCKN0TM3</MRN>
      </TransitOperation>
    </ncts:CC044C>

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
    "if it is valid Departure Message Type, return an appropriate Departure Message Type" in {
      val xmlParsingService = new XmlMessageParsingService()
      val payload           = createStream(validDepartureXml)
      val response =
        xmlParsingService.extractMessageType(payload, MessageType.updateMessageTypesSentByDepartureTrader)

      whenReady(response.value) {
        _ mustBe Right(MessageType.DeclarationAmendment)
      }
    }

    "if it is valid Arrival Message Type, return an appropriate Arrival Message Type" in {
      val xmlParsingService = new XmlMessageParsingService()
      val payload           = createStream(validArrivalXml)
      val response =
        xmlParsingService.extractMessageType(payload, MessageType.updateMessageTypesSentByArrivalTrader)

      whenReady(response.value) {
        _ mustBe Right(MessageType.UnloadingRemarks)
      }
    }

    "if it doesn't have a valid message type, return ExtractionError.MessageTypeNotFound" in {
      val xmlParsingService = new XmlMessageParsingService()
      val payload           = createStream(invalidMessageType)
      val response =
        xmlParsingService.extractMessageType(payload, MessageType.updateMessageTypesSentByArrivalTrader)

      whenReady(response.value) {
        _ mustBe Left(ExtractionError.MessageTypeNotFound("CC015C"))
      }
    }

    "if it doesn't have a message type entry, return ExtractionError.MessageTypeNotFound" in {
      val xmlParsingService = new XmlMessageParsingService()
      val payload           = createStream(withNoMessageTypeEntry)
      val response =
        xmlParsingService.extractMessageType(payload, MessageType.updateMessageTypesSentByArrivalTrader)

      whenReady(response.value) {
        _ mustBe Left(ExtractionError.MessageTypeNotFound("HolderOfTheTransitProcedure"))
      }
    }

    "if the input is malformed, return ExtractionError.MalformedInput" in {
      val xmlParsingService = new XmlMessageParsingService()
      val payload           = createStream("malformed")
      val response =
        xmlParsingService.extractMessageType(payload, MessageType.updateMessageTypesSentByArrivalTrader)

      whenReady(response.value) {
        r => r mustBe Left(ExtractionError.MalformedInput)
      }

    }
  }
}
