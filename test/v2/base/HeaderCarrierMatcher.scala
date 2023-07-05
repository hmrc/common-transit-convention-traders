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

package v2.base

import org.mockito.ArgumentMatcher
import uk.gov.hmrc.http.HeaderCarrier
import v2.models.ClientId

object HeaderCarrierMatcher {
  def apply(comparison: HeaderCarrier => Boolean): ArgumentMatcher[HeaderCarrier] = new HeaderCarrierMatcher(comparison)

  def clientId(expected: ClientId): ArgumentMatcher[HeaderCarrier] = apply(
    hc => hc.headers(Seq("X-Client-Id")).exists(_._2 == expected.value)
  )
}

class HeaderCarrierMatcher(comparison: HeaderCarrier => Boolean) extends ArgumentMatcher[HeaderCarrier] {

  override def matches(argument: HeaderCarrier): Boolean = comparison(argument)
}
