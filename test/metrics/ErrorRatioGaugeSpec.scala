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

package metrics

import com.codahale.metrics.Counter
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class ErrorRatioGaugeSpec extends AnyFreeSpec with Matchers {

  def createCounters(): (Counter, Counter, ErrorRatioGauge) = {
    val errorCount   = new Counter
    val successCount = new Counter
    (errorCount, successCount, ErrorRatioGauge(errorCount, successCount))
  }

  "should be NaN if no values set" in {
    val (_, _, g) = createCounters()
    assert(g.getRatio.getValue.equals(Double.NaN))
  }

  "should be Nan if only error is 1" in {
    val (e, _, g) = createCounters()
    e.inc()
    assert(g.getRatio.getValue.equals(Double.NaN))
  }

  "should be zero if only success is 1" in {
    val (_, s, g) = createCounters()
    s.inc()
    assert(g.getRatio.getValue.equals(0.0))
  }

  "should be 1 if both are 1" in {
    val (e, s, g) = createCounters()
    e.inc()
    s.inc()
    assert(g.getRatio.getValue.equals(1.0))
  }
}
