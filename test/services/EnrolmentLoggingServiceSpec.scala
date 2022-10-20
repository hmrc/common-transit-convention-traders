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

package services

import config.AppConfig
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Logger
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.auth.core.EnrolmentIdentifier
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.auth.core.authorise.EmptyPredicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.immutable.ListSet
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnrolmentLoggingServiceSpec
    extends AnyFreeSpec
    with MockitoSugar
    with BeforeAndAfterEach
    with Matchers
    with ScalaFutures
    with ScalaCheckDrivenPropertyChecks {

  val appConfigMock     = mock[AppConfig]
  val authConnectorMock = mock[AuthConnector]

  // Because Mockito doesn't like by-name parameters of the Play logger, we need to test against the underlying
  // logger (which is Java based, and so isn't by name).
  val underlyingLogger = mock[org.slf4j.Logger]

  object Harness extends EnrolmentLoggingServiceImpl(authConnectorMock, appConfigMock) {
    override protected val logger: Logger = new Logger(underlyingLogger)
  }

  private def enrolmentIdentifier(key: String) =
    Gen.listOfN(10, Gen.alphaNumStr).sample.map(_.mkString).map(EnrolmentIdentifier(key, _)).get

  override def beforeEach(): Unit = {
    reset(appConfigMock)
    reset(authConnectorMock)
    reset(underlyingLogger)
    when(underlyingLogger.isInfoEnabled).thenReturn(true)
    when(underlyingLogger.isWarnEnabled).thenReturn(true)
  }

  "Logging Enrolment Identifiers" - {

    "should hide all values except the last three" in {

      val value1 = enrolmentIdentifier("key1")
      val value2 = enrolmentIdentifier("key2")

      // Given these enrolment identifiers
      val identifierSeq = Seq(value1, value2)

      // when we create the log string
      val result = Harness.createIdentifierString(identifierSeq)

      // we should get the expected string
      result mustBe s"key1: ***${value1.value.takeRight(3)}, key2: ***${value2.value.takeRight(3)}"

    }

  }

  "Logging an enrolment" - {

    "should produce a string with the key, whether it's activated, and its identifiers" in {

      val key            = Gen.alphaNumStr.sample.get
      val value1         = enrolmentIdentifier("key1")
      val value2         = enrolmentIdentifier("key2")
      val identifierSeq  = Seq(value1, value2)
      val activatedState = Gen.oneOf(true, false).sample.getOrElse(false)

      // doesn't matter what else is, the string "activated" triggers the isActivated check
      val activatedString = if (activatedState) "activated" else "pending"

      val enrolment = Enrolment(key, identifierSeq, activatedString)

      val result = Harness.createLogString(enrolment)

      result mustBe s"Enrolment Key: $key, Activated: $activatedState, Identifiers: [ key1: ***${value1.value.takeRight(3)}, key2: ***${value2.value.takeRight(3)} ]"

    }

  }

  "Creating a message" - {

    "should produce a string of the expected format for a gaUserId of length 10 " in {
      val clientId               = Gen.alphaNumStr.sample
      val gaUserId               = Gen.listOfN(10, Gen.alphaNumChar).map(_.mkString).sample.getOrElse("0123456789")
      implicit val headerCarrier = HeaderCarrier(gaUserId = Some(gaUserId))

      val redactedGaUserId = s"***${gaUserId.takeRight(3)}"

      val message: String = s"""Insufficient enrolments were received for the following request:
                               |Client ID: ${clientId.getOrElse("Not provided")}
                               |Gateway User ID: $redactedGaUserId
                               |message1
                               |message2""".stripMargin

      // use a list set for ordering in testing purposes
      Harness.createMessage(clientId)(ListSet("message1", "message2")) mustBe message
    }

    "should produce a string of the expected format for a gaUserId of length 2 " in {
      val clientId               = Gen.alphaNumStr.sample
      val gaUserId               = Gen.listOfN(2, Gen.alphaNumChar).map(_.mkString).sample.getOrElse("ga")
      implicit val headerCarrier = HeaderCarrier(gaUserId = Some(gaUserId))

      val message: String = s"""Insufficient enrolments were received for the following request:
                               |Client ID: ${clientId.getOrElse("Not provided")}
                               |Gateway User ID: ***
                               |message1
                               |message2""".stripMargin

      // use a list set for ordering in testing purposes
      Harness.createMessage(clientId)(ListSet("message1", "message2")) mustBe message
    }

    "should produce a string of the expected format where a gaUserId is not provided" in {
      val clientId               = Gen.alphaNumStr.sample
      implicit val headerCarrier = HeaderCarrier()

      val message: String = s"""Insufficient enrolments were received for the following request:
                               |Client ID: ${clientId.getOrElse("Not provided")}
                               |Gateway User ID: Not provided
                               |message1
                               |message2""".stripMargin

      // use a list set for ordering in testing purposes
      Harness.createMessage(clientId)(ListSet("message1", "message2")) mustBe message
    }

  }

  "Requesting logging" - {

    "when the flag is not enabled will not log anything" in {
      when(appConfigMock.logInsufficientEnrolments).thenReturn(false)
      implicit val hc: HeaderCarrier = HeaderCarrier()

      whenReady(Harness.logEnrolments(Some("1234"))) {
        _ =>
          verify(underlyingLogger, times(0)).warn(any())
          verify(authConnectorMock, times(0)).authorise(eqTo(EmptyPredicate), eqTo(Retrievals.allEnrolments))(any(), any())
      }
    }

    "when the flag is enabled will request logging" in forAll(Gen.oneOf(None, Gen.alphaNumStr.sample), Gen.oneOf(None, Gen.alphaNumStr.sample)) {
      (clientId, gaUserId) =>
        beforeEach() // need to reset the mocks
        implicit val hc: HeaderCarrier = HeaderCarrier(gaUserId = gaUserId)
        val enrolments                 = Enrolments(Set(Enrolment("key", Seq(EnrolmentIdentifier("key1", "value1")), "activated")))

        when(appConfigMock.logInsufficientEnrolments).thenReturn(true)
        when(authConnectorMock.authorise(eqTo(EmptyPredicate), eqTo(Retrievals.allEnrolments))(any(), any()))
          .thenReturn(Future.successful(enrolments))

        val redactedGaUserId = gaUserId
          .map {
            case x if x.length > 3 => s"***${x.takeRight(3)}"
            case _                 => "***"
          }
          .getOrElse("Not provided")

        val message: String =
          s"""Insufficient enrolments were received for the following request:
                 |Client ID: ${clientId.getOrElse("Not provided")}
                 |Gateway User ID: $redactedGaUserId
                 |Enrolment Key: key, Activated: true, Identifiers: [ key1: ***ue1 ]""".stripMargin

        whenReady(Harness.logEnrolments(clientId)) {
          _ =>
            verify(underlyingLogger, times(1)).warn(eqTo(message))
            verify(authConnectorMock, times(1)).authorise(eqTo(EmptyPredicate), eqTo(Retrievals.allEnrolments))(any(), any())
        }
    }
  }

  "Redact" - {
    "redacts a string" in {
      Harness.redact("abcde") mustBe "***cde"
    }

    "redacts an optional string" in {
      Harness.redact(Some("abcde")) mustBe "***cde"
    }

    "redacts a full string if three characters" in {
      Harness.redact("abc") mustBe "***"
    }

    "redacts a full string if two characters" in {
      Harness.redact("ab") mustBe "***"
    }

    "redacts a full string if one character" in {
      Harness.redact("a") mustBe "***"
    }

    "does not redact a None" in {
      Harness.redact(None) mustBe "Not provided"
    }
  }

}
