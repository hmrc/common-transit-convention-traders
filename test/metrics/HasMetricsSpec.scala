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

package metrics

import com.codahale.metrics.Counter
import com.codahale.metrics.Histogram
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.OptionValues
import org.scalatest.compatible.Assertion
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.AbstractController
import play.api.mvc.Results
import play.api.test.FakeRequest
import play.api.test.Helpers

import scala.concurrent.Future

class HasMetricsSpec extends AsyncWordSpecLike with Matchers with OptionValues with MockitoSugar with BeforeAndAfterAll {

  trait MockHasMetrics {
    self: HasMetrics =>
    val timerContext   = mock[Timer.Context]
    val timer          = mock[Timer]
    val successCounter = mock[Counter]
    val failureCounter = mock[Counter]
    val histogram      = mock[Histogram]
    val metrics        = mock[MetricRegistry]

    when(metrics.timer(anyString())) thenReturn timer
    when(metrics.counter(endsWith("success-counter"))) thenReturn successCounter
    when(metrics.counter(endsWith("failed-counter"))) thenReturn failureCounter
    when(metrics.histogram(anyString())) thenReturn histogram
    when(timer.time()) thenReturn timerContext
    when(timerContext.stop()) thenReturn 0L
  }

  class TestHasMetrics extends HasMetrics with MockHasMetrics

  class TestHasActionMetrics extends AbstractController(Helpers.stubMessagesControllerComponents()) with HasActionMetrics with MockHasMetrics

  def withTestMetrics[A](test: TestHasMetrics => A): A =
    test(new TestHasMetrics)

  def withTestActionMetrics[A](test: TestHasActionMetrics => A): A =
    test(new TestHasActionMetrics)

  def verifyCompletedWithSuccess(metrics: MockHasMetrics): Assertion = {
    val inOrder = Mockito.inOrder(metrics.timer, metrics.timerContext, metrics.successCounter)
    inOrder.verify(metrics.timer, times(1)).time()
    inOrder.verify(metrics.timerContext, times(1)).stop()
    inOrder.verify(metrics.successCounter, times(1)).inc()
    verifyNoMoreInteractions(metrics.timer)
    verifyNoMoreInteractions(metrics.timerContext)
    verifyNoMoreInteractions(metrics.successCounter)
    verifyNoInteractions(metrics.failureCounter)
    succeed
  }

  def verifyCompletedWithFailure(metrics: MockHasMetrics): Assertion = {
    val inOrder = Mockito.inOrder(metrics.timer, metrics.timerContext, metrics.failureCounter)
    inOrder.verify(metrics.timer, times(1)).time()
    inOrder.verify(metrics.timerContext, times(1)).stop()
    inOrder.verify(metrics.failureCounter, times(1)).inc()
    verifyNoMoreInteractions(metrics.timer)
    verifyNoMoreInteractions(metrics.timerContext)
    verifyNoMoreInteractions(metrics.failureCounter)
    verifyNoInteractions(metrics.successCounter)
    succeed
  }

  val TestMetric = "test-metric"

  "HasMetrics" when {
    "withMetricsTimerAsync" should {
      "increment success counter for a successful future" in withTestMetrics {
        metrics =>
          metrics
            .withMetricsTimerAsync(TestMetric)(
              _ => Future.successful(())
            )
            .map {
              _ =>
                verifyCompletedWithSuccess(metrics)
            }
      }

      "increment success counter for a successful future where completeWithSuccess is called explicitly" in withTestMetrics {
        metrics =>
          metrics
            .withMetricsTimerAsync(TestMetric) {
              timer =>
                timer.completeWithSuccess()
                Future.successful(())
            }
            .map(
              _ => verifyCompletedWithSuccess(metrics)
            )
      }

      "increment failure counter for a failed future" in withTestMetrics {
        metrics =>
          metrics
            .withMetricsTimerAsync(TestMetric)(
              _ => Future.failed(new Exception)
            )
            .recover {
              case _ =>
                verifyCompletedWithFailure(metrics)
            }
      }

      "increment failure counter for a successful future where completeWithFailure is called explicitly" in withTestMetrics {
        metrics =>
          metrics
            .withMetricsTimerAsync(TestMetric) {
              timer =>
                timer.completeWithFailure()
                Future.successful(())
            }
            .map(
              _ => verifyCompletedWithFailure(metrics)
            )
      }

      "only increment counters once regardless of how many times the user calls complete with success" in withTestMetrics {
        metrics =>
          metrics
            .withMetricsTimerAsync(TestMetric) {
              timer =>
                Future(timer.completeWithSuccess())
                Future(timer.completeWithSuccess())
                Future(timer.completeWithSuccess())
                Future(timer.completeWithSuccess())
                Future.successful(())
            }
            .map(
              _ => verifyCompletedWithSuccess(metrics)
            )
      }

      "only increment counters once regardless of how many times the user calls complete with failure" in withTestMetrics {
        metrics =>
          metrics
            .withMetricsTimerAsync(TestMetric) {
              timer =>
                Future(timer.completeWithFailure())
                Future(timer.completeWithFailure())
                Future(timer.completeWithFailure())
                Future(timer.completeWithFailure())
                timer.completeWithFailure()
                Future.successful(())
            }
            .map(
              _ => verifyCompletedWithFailure(metrics)
            )
      }

      "increment failure counter when the user throws an exception constructing their code block" in withTestMetrics {
        metrics =>
          assertThrows[RuntimeException] {
            metrics.withMetricsTimerAsync(TestMetric)(
              _ => Future.successful(throw new RuntimeException)
            )
          }

          Future.successful(verifyCompletedWithFailure(metrics))
      }
    }

    "withMetricsTimerResult" should {
      "increment success counter for a successful future of an informational Result" in withTestMetrics {
        metrics =>
          metrics
            .withMetricsTimerResult(TestMetric) {
              Future.successful(Results.Continue)
            }
            .map(
              _ => verifyCompletedWithSuccess(metrics)
            )
      }

      "increment success counter for a successful future of a successful Result" in withTestMetrics {
        metrics =>
          metrics
            .withMetricsTimerResult(TestMetric) {
              Future.successful(Results.Ok)
            }
            .map(
              _ => verifyCompletedWithSuccess(metrics)
            )
      }

      "increment success counter for a successful future of a redirect Result" in withTestMetrics {
        metrics =>
          metrics
            .withMetricsTimerResult(TestMetric) {
              Future.successful(Results.NotModified)
            }
            .map(
              _ => verifyCompletedWithSuccess(metrics)
            )
      }

      "increment failure counter for a successful future of a client error Result" in withTestMetrics {
        metrics =>
          metrics
            .withMetricsTimerResult(TestMetric) {
              Future.successful(Results.EntityTooLarge)
            }
            .map(
              _ => verifyCompletedWithFailure(metrics)
            )
      }

      "increment failure counter for a successful future of a server error Result" in withTestMetrics {
        metrics =>
          metrics
            .withMetricsTimerResult(TestMetric) {
              Future.successful(Results.BadGateway)
            }
            .map(
              _ => verifyCompletedWithFailure(metrics)
            )
      }

      "increment failure counter for a failed future" in withTestMetrics {
        metrics =>
          metrics
            .withMetricsTimerResult(TestMetric) {
              Future.failed(new Exception)
            }
            .transformWith {
              case _ =>
                verifyCompletedWithFailure(metrics)
            }
      }

      "increment failure counter when the user throws an exception constructing their code block" in withTestMetrics {
        metrics =>
          assertThrows[RuntimeException] {
            metrics.withMetricsTimerResult(TestMetric) {
              Future.successful(throw new RuntimeException)
            }
          }

          Future.successful(verifyCompletedWithFailure(metrics))
      }
    }

    "withMetricsTimerAction" should {
      def fakeRequest = FakeRequest()

      "increment success counter for an informational Result" in withTestActionMetrics {
        metrics =>
          metrics
            .withMetricsTimerAction(TestMetric) {
              metrics.Action(Results.SwitchingProtocols)
            }
            .apply(fakeRequest)
            .map(
              _ => verifyCompletedWithSuccess(metrics)
            )
      }

      "increment success counter for a successful Result" in withTestActionMetrics {
        metrics =>
          metrics
            .withMetricsTimerAction(TestMetric) {
              metrics.Action(Results.Ok)
            }
            .apply(fakeRequest)
            .map(
              _ => verifyCompletedWithSuccess(metrics)
            )
      }

      "increment success counter for a redirect Result" in withTestActionMetrics {
        metrics =>
          metrics
            .withMetricsTimerAction(TestMetric) {
              metrics.Action(Results.Found("https://wikipedia.org"))
            }
            .apply(fakeRequest)
            .map(
              _ => verifyCompletedWithSuccess(metrics)
            )
      }

      "increment failure counter for a client error Result" in withTestActionMetrics {
        metrics =>
          metrics
            .withMetricsTimerAction(TestMetric) {
              metrics.Action(Results.Conflict)
            }
            .apply(fakeRequest)
            .map(
              _ => verifyCompletedWithFailure(metrics)
            )
      }

      "increment failure counter for a server error Result" in withTestActionMetrics {
        metrics =>
          metrics
            .withMetricsTimerAction(TestMetric) {
              metrics.Action(Results.ServiceUnavailable)
            }
            .apply(fakeRequest)
            .map(
              _ => verifyCompletedWithFailure(metrics)
            )
      }
    }
  }

}
