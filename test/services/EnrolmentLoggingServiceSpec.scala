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
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.auth.core.EnrolmentIdentifier

class EnrolmentLoggingServiceSpec
  extends AnyFreeSpec
  with MockitoSugar
  with BeforeAndAfterEach
  with Matchers {

  val appConfigMock     = mock[AppConfig]
  val authConnectorMock = mock[AuthConnector]
  val sut               = new EnrolmentLoggingServiceImpl(authConnectorMock, appConfigMock)

  "Logging Enrolment Identifiers" - {

    "should hide all values except the last three" in {

      val value1 = Gen.alphaNumStr.sample.map(EnrolmentIdentifier("key1", _)).get
      val value2 = Gen.alphaNumStr.sample.map(EnrolmentIdentifier("key2", _)).get

      // Given these enrolment identifiers
      val identifierString = Seq(value1, value2)

      // when we create the log string
      val result = sut.createIdentifierString(identifierString)

      // we should get the expected string
      result mustBe s"key1: ***${value1.value.takeRight(3)}, key2: ***${value2.value.takeRight(3)}"

    }

  }

}
