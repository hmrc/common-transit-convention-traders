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

package v2.services

import org.apache.pekko.stream.scaladsl.Sink
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import v2.base.StreamTestHelpers
import v2.base.TestActorSystem
import models.common.errors.ExtractionError
import v2.models.request.MessageType

import scala.xml.NodeSeq

class XmlParsersSpec extends AnyFreeSpec with TestActorSystem with Matchers with StreamTestHelpers with ScalaFutures with ScalaCheckPropertyChecks {
  "messageTypeExtractor parser" - {

    val validDepartureMessageType: NodeSeq =
      <ncts:CC014C PhaseID="NCTS5.0" xmlns:ncts="http://ncts.dgtaxud.ec">
        <HolderOfTheTransitProcedure>
          <identificationNumber>GB1234</identificationNumber>
        </HolderOfTheTransitProcedure>
      </ncts:CC014C>

    val validArrivalMessageType: NodeSeq =
      <ncts:CC044C PhaseID="NCTS5.0" xmlns:ncts="http://ncts.dgtaxud.ec">
        <TransitOperation>
          <MRN>27WF9X1FQ9RCKN0TM3</MRN>
        </TransitOperation>
      </ncts:CC044C>

    val invalidDepartureMessageType: NodeSeq =
      <ncts:CC015C PhaseID="NCTS5.0" xmlns:ncts="http://ncts.dgtaxud.ec">
        <HolderOfTheTransitProcedure>
          <identificationNumber>GB1234</identificationNumber>
        </HolderOfTheTransitProcedure>
      </ncts:CC015C>

    val invalidArrivalMessageType: NodeSeq =
      <ncts:CC007C PhaseID="NCTS5.0" xmlns:ncts="http://ncts.dgtaxud.ec">
        <TransitOperation>
          <MRN>27WF9X1FQ9RCKN0TM3</MRN>
        </TransitOperation>
      </ncts:CC007C>

    val invalidMessageType: NodeSeq =
      <HolderOfTheTransitProcedure>
        <CC013C>IE013</CC013C>
      </HolderOfTheTransitProcedure>

    "when provided with a valid departure message type" in {
      val stream       = createParsingEventStream(validDepartureMessageType)
      val parsedResult = stream.via(XmlParsers.messageTypeExtractor(MessageType.updateMessageTypesSentByDepartureTrader)).runWith(Sink.head)

      whenReady(parsedResult) {
        _ mustBe Right(MessageType.DeclarationInvalidationRequest)
      }
    }

    "when provided with an invalid  departure message type" in {
      val stream       = createParsingEventStream(invalidDepartureMessageType)
      val parsedResult = stream.via(XmlParsers.messageTypeExtractor(MessageType.updateMessageTypesSentByDepartureTrader)).runWith(Sink.head)

      whenReady(parsedResult) {
        _ mustBe Left(ExtractionError.MessageTypeNotFound("CC015C"))
      }
    }

    "when provided with a valid arrival message type" in {
      val stream       = createParsingEventStream(validArrivalMessageType)
      val parsedResult = stream.via(XmlParsers.messageTypeExtractor(MessageType.updateMessageTypesSentByArrivalTrader)).runWith(Sink.head)

      whenReady(parsedResult) {
        _ mustBe Right(MessageType.UnloadingRemarks)
      }
    }

    "when provided with an invalid  arrival message type" in {
      val stream       = createParsingEventStream(invalidArrivalMessageType)
      val parsedResult = stream.via(XmlParsers.messageTypeExtractor(MessageType.updateMessageTypesSentByArrivalTrader)).runWith(Sink.head)

      whenReady(parsedResult) {
        _ mustBe Left(ExtractionError.MessageTypeNotFound("CC007C"))
      }
    }

    "when provided with an invalid message type" in {
      val stream       = createParsingEventStream(invalidMessageType)
      val parsedResult = stream.via(XmlParsers.messageTypeExtractor(MessageType.updateMessageTypesSentByArrivalTrader)).runWith(Sink.head)

      whenReady(parsedResult) {
        _ mustBe Left(ExtractionError.MessageTypeNotFound("HolderOfTheTransitProcedure"))
      }
    }

  }

}
