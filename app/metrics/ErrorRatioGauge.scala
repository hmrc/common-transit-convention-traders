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
import com.codahale.metrics.RatioGauge
import com.codahale.metrics.RatioGauge.Ratio

case class ErrorRatioGauge(e: Counter, s: Counter) extends RatioGauge {

//  private var err: Double  = 0.0
//  private var succ: Double = 0.0

  override def getRatio: Ratio = Ratio.of(e.getCount.asInstanceOf[Double], s.getCount.asInstanceOf[Double])

//  def incError(): Unit   = err += 1.0
//  def incSuccess(): Unit = succ += 1.0
}
