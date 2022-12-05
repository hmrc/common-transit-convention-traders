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

import akka.stream.scaladsl.Sink
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import v2.base.StreamTestHelpers
import v2.base.TestActorSystem
import v2.models.MovementType
import v2.models.errors.ExtractionError
import v2.models.request.MessageType

import scala.xml.NodeSeq

class XmlParsersSpec extends AnyFreeSpec with TestActorSystem with Matchers with StreamTestHelpers with ScalaFutures with ScalaCheckPropertyChecks {
  "messageTypeExtractor parser" - {

    val validMessageType: NodeSeq =
      <ncts:CC014C PhaseID="NCTS5.0" xmlns:ncts="http://ncts.dgtaxud.ec">
        <HolderOfTheTransitProcedure>
          <identificationNumber>GB1234</identificationNumber>
        </HolderOfTheTransitProcedure>
      </ncts:CC014C>

    val invalidMessageType: NodeSeq =
      <HolderOfTheTransitProcedure>
        <CC013C>IE013</CC013C>
      </HolderOfTheTransitProcedure>

    "when provided with a valid message type" in {
      val stream       = createParsingEventStream(validMessageType)
      val parsedResult = stream.via(XmlParsers.messageTypeExtractor(MovementType.Departure)).runWith(Sink.head)

      whenReady(parsedResult) {
        _ mustBe Right(MessageType.DeclarationInvalidationRequest)
      }
    }

    "when provided with an invalid message type" in {
      val stream       = createParsingEventStream(invalidMessageType)
      val parsedResult = stream.via(XmlParsers.messageTypeExtractor(MovementType.Departure)).runWith(Sink.head)

      whenReady(parsedResult) {
        _ mustBe Left(ExtractionError.MessageTypeNotFound("HolderOfTheTransitProcedure"))
      }
    }

  }

}
