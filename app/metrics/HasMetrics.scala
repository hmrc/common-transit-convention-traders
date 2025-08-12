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
import com.codahale.metrics.MetricRegistry
import com.codahale.metrics.Timer
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.Result

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

trait HasActionMetrics extends HasMetrics {
  self: BaseController =>

  def withMetricsTimerAction[A](metricKey: String)(action: Action[A])(implicit ec: ExecutionContext): Action[A] =
    Action(action.parser).async {
      request =>
        withMetricsTimerResult(metricKey)(action(request))
    }
}

trait HasMetrics {
  def metrics: MetricRegistry

  def counter(metricsKey: String): Counter =
    metrics.counter(metricsKey)

  class MetricsTimer(metricKey: String) {
    val timerContext: Timer.Context = metrics.timer(s"$metricKey-timer").time()
    val successCounter: Counter     = metrics.counter(s"$metricKey-success-counter")
    val failureCounter: Counter     = metrics.counter(s"$metricKey-failed-counter")
    private val timerRunning        = new AtomicBoolean(true)

    def completeWithSuccess(): Unit =
      if (timerRunning.compareAndSet(true, false)) {
        timerContext.stop()
        successCounter.inc()
      }

    def completeWithFailure(): Unit =
      if (timerRunning.compareAndSet(true, false)) {
        timerContext.stop()
        failureCounter.inc()
      }
  }

  def withMetricsTimerResult(metricKey: String)(block: => Future[Result])(implicit ec: ExecutionContext): Future[Result] =
    withMetricsTimer(metricKey) {
      timer =>
        val result = block

        // Clean up timer according to server response
        result.foreach {
          result =>
            if (isFailureStatus(result.header.status))
              timer.completeWithFailure()
            else
              timer.completeWithSuccess()
        }

        // Clean up timer for unhandled exceptions
        result.failed.foreach(
          _ => timer.completeWithFailure()
        )

        result
    }

  def withMetricsTimerAsync[T](
    metricKey: String
  )(block: MetricsTimer => Future[T])(implicit ec: ExecutionContext): Future[T] =
    withMetricsTimer(metricKey) {
      timer =>
        val result = block(timer)

        // Clean up timer if the user doesn't
        result.foreach(
          _ => timer.completeWithSuccess()
        )

        // Clean up timer on unhandled exceptions
        result.failed.foreach(
          _ => timer.completeWithFailure()
        )

        result
    }

  private def withMetricsTimer[T](metricKey: String)(block: MetricsTimer => T): T = {
    val timer = new MetricsTimer(metricKey)

    try block(timer)
    catch {
      case NonFatal(e) =>
        timer.completeWithFailure()
        throw e
    }
  }

  private def isFailureStatus(status: Int) =
    status / 100 >= 4
}
