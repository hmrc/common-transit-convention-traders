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

import config.AppConfig
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results.Ok
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.BodyParsers
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthActionSpec extends AnyFreeSpec with Matchers with MockitoSugar {

  class Harness(authAction: AuthAction) {

    def get(): Action[AnyContent] = authAction {
      authedRequest =>
        Ok(authedRequest.eori)
    }
  }

  val enrolmentsWithEori: Enrolments = Enrolments(
    Set(
      Enrolment(
        key = "IR-SA",
        identifiers = Seq(
          EnrolmentIdentifier(
            "UTR",
            "123"
          )
        ),
        state = "Activated"
      ),
      Enrolment(
        key = "IR-CT",
        identifiers = Seq(
          EnrolmentIdentifier(
            "UTR",
            "345"
          )
        ),
        state = "Activated"
      ),
      Enrolment(
        key = "HMCE-NCTS-ORG",
        identifiers = Seq(
          EnrolmentIdentifier(
            "VATRegNoTURN",
            "456"
          )
        ),
        state = "Activated"
      )
    )
  )

  val noValidEnrolments: Enrolments = Enrolments(
    Set(
      Enrolment(
        key = "IR-SA",
        identifiers = Seq(
          EnrolmentIdentifier(
            "UTR",
            "123"
          )
        ),
        state = "Activated"
      )
    )
  )

  val noValidEnrolmentIdentifier: Enrolments = Enrolments(
    Set(
      Enrolment(
        key = "IR-SA",
        identifiers = Seq(
          EnrolmentIdentifier(
            "UTR",
            "123"
          )
        ),
        state = "Activated"
      ),
      Enrolment(
        key = "IR-CT",
        identifiers = Seq(
          EnrolmentIdentifier(
            "UTR",
            "345"
          )
        ),
        state = "Activated"
      ),
      Enrolment(
        key = "HMCE-NCTS-ORG",
        identifiers = Seq(
          EnrolmentIdentifier(
            "VATRegNoTURN2",
            "456"
          )
        ),
        state = "Activated"
      )
    )
  )

  val notActivatedEnrolments: Enrolments = Enrolments(
    Set(
      Enrolment(
        key = "IR-SA",
        identifiers = Seq(
          EnrolmentIdentifier(
            "UTR",
            "123"
          )
        ),
        state = "Activated"
      ),
      Enrolment(
        key = "IR-CT",
        identifiers = Seq(
          EnrolmentIdentifier(
            "UTR",
            "345"
          )
        ),
        state = "Activated"
      ),
      Enrolment(
        key = "HMCE-NCTS-ORG",
        identifiers = Seq(
          EnrolmentIdentifier(
            "VATRegNoTURN",
            "456"
          )
        ),
        state = "NotYetActivated"
      )
    )
  )

  "must execute the block" - {
    "when the user is logged in and has the correct enrolment" in {
      val authConnector = mock[AuthConnector]

      when(authConnector.authorise[Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.successful(enrolmentsWithEori))

      val application = GuiceApplicationBuilder()
        .configure(
          "metrics.jvm" -> false
        )
        .build()

      val config: AppConfig = application.injector.instanceOf[AppConfig]

      val bodyParser = application.injector.instanceOf[BodyParsers.Default]

      val authAction = new AuthAction(authConnector, config, bodyParser)
      val controller = new Harness(authAction)
      val result     = controller.get()(FakeRequest())

      status(result) mustEqual OK
      contentAsString(result) mustEqual "456"
    }
  }

  "must return Forbidden" - {
    "when the user is logged in and doesn't have any valid enrolments" in {
      val authConnector = mock[AuthConnector]

      when(authConnector.authorise[Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.successful(noValidEnrolments))

      val application = GuiceApplicationBuilder()
        .configure(
          "metrics.jvm" -> false
        )
        .build()

      val config: AppConfig = application.injector.instanceOf[AppConfig]

      val bodyParser = application.injector.instanceOf[BodyParsers.Default]

      val authAction = new AuthAction(authConnector, config, bodyParser)
      val controller = new Harness(authAction)
      val result     = controller.get()(FakeRequest())

      status(result) mustEqual FORBIDDEN
      contentAsString(result) mustEqual "Current user doesn't have a valid EORI enrolment."
    }

    "when the user is logged in and has no valid enrolment identifier" in {
      val authConnector = mock[AuthConnector]

      when(authConnector.authorise[Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.successful(noValidEnrolmentIdentifier))

      val application = GuiceApplicationBuilder()
        .configure(
          "metrics.jvm" -> false
        )
        .build()

      val config: AppConfig = application.injector.instanceOf[AppConfig]

      val bodyParser = application.injector.instanceOf[BodyParsers.Default]

      val authAction = new AuthAction(authConnector, config, bodyParser)
      val controller = new Harness(authAction)
      val result     = controller.get()(FakeRequest())

      status(result) mustEqual FORBIDDEN
      contentAsString(result) mustEqual "Current user doesn't have a valid EORI enrolment."
    }

    "when the user is logged in and has no valid activated eori enrolments" in {
      val authConnector = mock[AuthConnector]

      when(authConnector.authorise[Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.failed(InsufficientEnrolments()))

      val application = GuiceApplicationBuilder()
        .configure(
          "metrics.jvm" -> false
        )
        .build()

      val config: AppConfig = application.injector.instanceOf[AppConfig]

      val bodyParser = application.injector.instanceOf[BodyParsers.Default]

      val authAction = new AuthAction(authConnector, config, bodyParser)
      val controller = new Harness(authAction)
      val result     = controller.get()(FakeRequest())

      status(result) mustEqual FORBIDDEN
      contentAsString(result) mustEqual "Current user doesn't have a valid EORI enrolment."
    }
  }

  "must return Unauthorized" - {
    "when the user hasn't logged in" in {
      val authConnector = mock[AuthConnector]

      when(authConnector.authorise[Enrolments](any(), any())(any(), any()))
        .thenReturn(Future.failed(new MissingBearerToken()))

      val application = GuiceApplicationBuilder()
        .configure(
          "metrics.jvm" -> false
        )
        .build()

      val config: AppConfig = application.injector.instanceOf[AppConfig]

      val bodyParser = application.injector.instanceOf[BodyParsers.Default]

      val authAction = new AuthAction(authConnector, config, bodyParser)
      val controller = new Harness(authAction)
      val result     = controller.get()(FakeRequest())

      status(result) mustEqual UNAUTHORIZED
    }
  }
}
