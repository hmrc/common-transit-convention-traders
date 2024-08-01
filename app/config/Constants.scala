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

package config

import scala.util.matching.Regex

object Constants {
  val AcceptHeaderPattern: Regex = "^application/vnd[.]{1}hmrc[.]{1}(.*?)[+]{1}(.*)$".r

  val AcceptHeaderMissing: String = "The accept header is missing or invalid"

  val BoxName: String = "customs/transits##1.0##notificationUrl"

  val Context: String = "/customs/transits"

  val XClientIdHeader: String = "X-Client-Id"

  val XMessageTypeHeader: String = "X-Message-Type"

  val XCallbackBoxIdHeader: String = "X-Callback-Box-Id"

  val XContentLengthHeader: String = "X-ContentLength"

  val ChannelHeader: String = "channel"

  val LegacyEnrolmentKey: String   = "HMCE-NCTS-ORG"
  val LegacyEnrolmentIdKey: String = "VATRegNoTURN"

  val NewEnrolmentKey: String   = "HMRC-CTC-ORG"
  val NewEnrolmentIdKey: String = "EORINumber"

  val XMissingECCEnrolment: String = "X-Missing-ECC-Enrolment"

  val MissingECCEnrolmentMessage: String =
    "User does not have the ECC enrolment, and will be unable to submit phase 5 declarations. See https://www.gov.uk/guidance/how-to-subscribe-to-the-new-computerised-transit-system"

  val APIVersionHeaderKey        = "APIVersion"
  val APIVersionFinalHeaderValue = "final"
}
