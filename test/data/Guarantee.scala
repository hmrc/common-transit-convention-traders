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

package data

case class Guarantee(gType: Char, gReference: String) {
  def isDefaulting: Boolean = Guarantee.referenceTypes.contains(gType)
}

object Guarantee {
  val referenceTypes: Seq[Char] = Seq[Char]('0', '1', '2', '4', '9')
  val validTypes: Seq[Char]     = Seq[Char]('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B')

  def isOther(gType: Char): Boolean = !referenceTypes.contains(gType)
}
