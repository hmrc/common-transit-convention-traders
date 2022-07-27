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

import akka.stream.scaladsl.Source
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import v2.connectors.AuditingConnector
import v2.models.AuditType

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuditingServiceSpec extends AnyFreeSpec with Matchers with ScalaFutures with MockitoSugar {

  implicit val hc = HeaderCarrier()

  "Posting an audit message" - {
    "on success, return the successful future" in {
      val mockConnector = mock[AuditingConnector]
      when(mockConnector.post(any(), any())(any(), any())).thenReturn(Future.successful(()))
      val sut = new AuditingServiceImpl(mockConnector)

      whenReady(sut.audit(AuditType.DeclarationData, Source.empty)) {
        _ =>
          verify(mockConnector, times(1)).post(any(), any())(any(), any())
      }
    }

    "on failure, will log a message" in {
      val mockConnector = mock[AuditingConnector]
      val exception     = new IllegalStateException("failed")
      when(mockConnector.post(any(), any())(any(), any())).thenReturn(Future.failed(exception))

      object Harness extends AuditingServiceImpl(mockConnector) {
        val logger0 = mock[org.slf4j.Logger]
        when(logger0.isWarnEnabled()).thenReturn(true)
        override val logger: Logger = new Logger(logger0)
      }

      whenReady(Harness.audit(AuditType.DeclarationData, Source.empty)) {
        _ =>
          verify(mockConnector, times(1)).post(any(), any())(any(), any())
          verify(Harness.logger0, times(1)).warn(eqTo("Unable to audit payload due to an exception"), eqTo(exception))
      }

    }
  }

}