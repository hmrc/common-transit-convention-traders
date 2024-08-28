/*
 * Copyright 2024 HM Revenue & Customs
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

package routing

import cats.implicits.catsSyntaxOptionId
import cats.implicits.none
import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.http.HeaderNames
import play.api.http.Status.NOT_ACCEPTABLE
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.defaultAwaitTimeout
import play.api.test.Helpers.status
import play.api.test.Helpers.stubControllerComponents
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import v2.base.TestActorSystem
import v2_1.fakes.controllers.FakeV2MovementsController
import v2_1.fakes.controllers.FakeV2TransitionalMovementsController

class GenericRoutingSpec extends AnyWordSpec with Matchers with TestActorSystem with ScalaFutures {

  ".getMessageBody" should {

    "Route to transitional controller (v2) when the accept header is 'application/vnd.hmrc.2.0+json'" in new Setup {
      val result = controller.getMessageBody(movementType, movementId, messageId)(createFakeRequest("application/vnd.hmrc.2.0+json".some))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.obj("version" -> 2)
    }

    "Route to transitional controller (v2) when the accept header is 'application/vnd.hmrc.2.0+xml'" in new Setup {
      val result = controller.getMessageBody(movementType, movementId, messageId)(createFakeRequest("application/vnd.hmrc.2.0+xml".some))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.obj("version" -> 2)
    }

    "Route to transitional controller (v2.1) when the accept header is 'application/vnd.hmrc.2.1+json'" in new Setup {
      val result = controller.getMessageBody(movementType, movementId, messageId)(createFakeRequest("application/vnd.hmrc.2.1+json".some))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.obj("version" -> 2.1)
    }

    "Route to transitional controller (v2.1) when the accept header is 'application/vnd.hmrc.2.1+xml'" in new Setup {
      val result = controller.getMessageBody(movementType, movementId, messageId)(createFakeRequest("application/vnd.hmrc.2.1+xml".some))
      status(result) shouldBe OK
      contentAsJson(result) shouldBe Json.obj("version" -> 2.1)
    }

    "Return NotAccepted when the ACCEPT header is missing" in new Setup {
      val result = controller.getMessageBody(movementType, movementId, messageId)(createFakeRequest(none))
      status(result) shouldBe NOT_ACCEPTABLE
      contentAsJson(result) shouldBe Json.obj("message" -> "The Accept header is missing or invalid.", "code" -> "NOT_ACCEPTABLE")

    }

    "Return NotAccepted when the ACCEPT header value does not match the required headers" in new Setup {
      val result = controller.getMessageBody(movementType, movementId, messageId)(createFakeRequest("invalid-accept-header".some))
      status(result) shouldBe NOT_ACCEPTABLE
      contentAsJson(result) shouldBe Json.obj("message" -> "The Accept header is missing or invalid.", "code" -> "NOT_ACCEPTABLE")
    }
  }

  trait Setup {

    val controller = new GenericRouting(
      stubControllerComponents(),
      new FakeV2TransitionalMovementsController(),
      new FakeV2MovementsController()
    )

    val movementType: MovementType = MovementType.Arrival
    val movementId: MovementId     = MovementId("someId")
    val messageId: MessageId       = MessageId("someMessageId")

    def createFakeRequest(headerValue: Option[String]) =
      FakeRequest(
        method = "",
        uri = "",
        body = <test></test>,
        headers = FakeHeaders(
          headerValue
            .map(
              hv => Seq(HeaderNames.ACCEPT -> hv)
            )
            .getOrElse(Seq.empty)
        )
      )
  }
}
