/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.actions

import play.api.mvc.Action
import play.api.mvc.Results._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.AnyContent
import play.api.mvc.DefaultActionBuilder
import utils.analysis.MessageAnalyser
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.test.Helpers._
import play.api.test.FakeRequest
import scala.xml.NodeSeq
import play.api.mvc.AbstractController
import play.api.test.Helpers
import play.api.inject.bind

class AnalyseMessageActionSpec extends AnyFreeSpec with Matchers with MockitoSugar {

  class Harness(actionBuilder: DefaultActionBuilder, analyseMessage: AnalyseMessageActionProvider)
      extends AbstractController(Helpers.stubControllerComponents()) {

    def getAny(): Action[AnyContent] = (actionBuilder andThen analyseMessage()) {
      _ =>
        NoContent
    }

    def getXml(): Action[NodeSeq] = (actionBuilder(parse.xml) andThen analyseMessage()) {
      _ =>
        NoContent
    }
  }

  "AnalyseMessageAction must" - {
    "track message stats when given XML" in {
      val messageAnalyser = mock[MessageAnalyser]

      doNothing().when(messageAnalyser).trackMessageStats(any())

      val application = GuiceApplicationBuilder()
        .overrides(bind[MessageAnalyser].toInstance(messageAnalyser))
        .configure(
          "metrics.jvm" -> false
        )
        .build()

      val actionBuilder  = application.injector.instanceOf[DefaultActionBuilder]
      val actionProvider = application.injector.instanceOf[AnalyseMessageActionProvider]

      val controller = new Harness(actionBuilder, actionProvider)
      val result     = controller.getXml()(FakeRequest().withBody(<test></test>))

      status(result) mustEqual NO_CONTENT
      verify(messageAnalyser, times(1)).trackMessageStats(any())
    }

    "ignore other request types" in {
      val messageAnalyser = mock[MessageAnalyser]

      doNothing().when(messageAnalyser).trackMessageStats(any())

      val application = GuiceApplicationBuilder()
        .overrides(bind[MessageAnalyser].toInstance(messageAnalyser))
        .configure(
          "metrics.jvm" -> false
        )
        .build()

      val actionBuilder  = application.injector.instanceOf[DefaultActionBuilder]
      val actionProvider = application.injector.instanceOf[AnalyseMessageActionProvider]

      val controller = new Harness(actionBuilder, actionProvider)
      val result     = controller.getAny()(FakeRequest())

      status(result) mustEqual NO_CONTENT
      verifyNoInteractions(messageAnalyser)
    }
  }
}
