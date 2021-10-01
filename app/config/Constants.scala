/*
 * Copyright 2021 HM Revenue & Customs
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

package config

object Constants {
  val AcceptHeaderPattern = "^application/vnd[.]{1}hmrc[.]{1}(.*?)[+]{1}(.*)$".r

  val AcceptHeaderMissing = "The accept header is missing or invalid"

  val BoxName = "customs/transits##1.0##notificationUrl"

  val Context = "/customs/transits"

  val XClientIdHeader = "X-Client-Id"

  val ChannelHeader = "channel"

  val LegacyEnrolmentKey: String   = "HMCE-NCTS-ORG"
  val LegacyEnrolmentIdKey: String = "VATRegNoTURN"

  val NewEnrolmentKey: String   = "HMRC-CTC-ORG"
  val NewEnrolmentIdKey: String = "EORINumber"
}
