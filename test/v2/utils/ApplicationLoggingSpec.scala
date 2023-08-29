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

package v2.utils

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.Logger

class ApplicationLoggingSpec extends AnyFreeSpec with Matchers {

  object Harness extends ApplicationLogging {
    // logger is protected, so we create an accessor to get it
    def getLogger: Logger = this.logger
  }

  "ApplicationLogging#logger must have the logger name prepended with uk.gov" in {
    Harness.getLogger.logger.getName mustBe "uk.gov.hmrc.commontransitconventiontraders.v2.utils.ApplicationLoggingSpec$Harness"
  }

}
