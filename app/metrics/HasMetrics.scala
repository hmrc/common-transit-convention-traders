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
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.Result
import uk.gov.hmrc.http.HttpResponse

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

trait HasActionMetrics extends HasMetrics {
  self: BaseController =>

  /** Execute a [[play.api.mvc.Action]] with a metrics timer. Intended for use in controllers that return HTTP responses.
    *
    * @param metricKey
    *   The id of the metric to be collected
    * @param action
    *   The action to wrap with metrics collection
    * @param ec
    *   The [[scala.concurrent.ExecutionContext]] on which the block of code should run
    * @return
    *   an action which captures metrics about the wrapped action
    */
  def withMetricsTimerAction[A](metricKey: String)(action: Action[A])(implicit ec: ExecutionContext): Action[A] =
    Action(action.parser).async {
      request =>
        withMetricsTimerResult(metricKey)(action(request))
    }
}

trait HasMetrics {
  def metrics: MetricRegistry

  def histo(metricKey: String): Histogram =
    metrics.histogram(metricKey)

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

  /** Execute a block of code with a metrics timer. Intended for use in controllers that return HTTP responses.
    *
    * @param metricKey
    *   The id of the metric to be collected
    * @param block
    *   The block of code to execute asynchronously
    * @param ec
    *   The [[scala.concurrent.ExecutionContext]] on which the block of code should run
    * @return
    *   The result of the block of code
    */
  def withMetricsTimerResponse(metricKey: String)(block: => Future[HttpResponse])(implicit ec: ExecutionContext): Future[HttpResponse] =
    withMetricsTimer(metricKey) {
      timer =>
        val response = block

        // Clean up timer according to server response
        response.foreach {
          response =>
            if (isFailureStatus(response.status))
              timer.completeWithFailure()
            else
              timer.completeWithSuccess()
        }

        // Clean up timer for unhandled exceptions
        response.failed.foreach(
          _ => timer.completeWithFailure()
        )

        response
    }

  /** Execute a block of code with a metrics timer. Intended for use in controllers that return HTTP responses.
    *
    * @param metricKey
    *   The id of the metric to be collected
    * @param block
    *   The block of code to execute asynchronously
    * @param ec
    *   The [[scala.concurrent.ExecutionContext]] on which the block of code should run
    * @return
    *   The result of the block of code
    */
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

  /** Execute a block of code with a metrics timer.
    *
    * Intended for use in connectors that call other microservices.
    *
    * It's expected that the user of this method might want to handle connector failures gracefully and therefore they are given a [[MetricsTimer]] to
    * optionally record whether the call was a success or a failure.
    *
    * If the user does not specify otherwise the status of the result Future is used to determine whether the block was successful or not.
    *
    * @param metricKey
    *   The id of the metric to be collected
    * @param block
    *   The block of code to execute asynchronously
    * @param ec
    *   The [[scala.concurrent.ExecutionContext]] on which the block of code should run
    * @return
    *   The result of the block of code
    */
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

  def withMetricsTimer[T](metricKey: String)(block: MetricsTimer => T): T = {
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
