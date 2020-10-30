/*
 * Copyright 2020 HM Revenue & Customs
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

package models

import models.ParseError.{InvalidItemNumber, MissingItemNumber}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

class ParseErrorSpec extends AnyFreeSpec with Matchers{

  "liftParseError" - {

    val failedParse: Seq[Either[ParseError, String]] =
      Seq(Left(InvalidItemNumber("Bad Item Number")),
        Left(MissingItemNumber("Missing Item Number")),
        Right("Good Result"))

    val goodParse: Seq[Either[ParseError, String]] =
      Seq(Right("Good Result"), Right("Other Result"))

    "lifts the first parseError when there are parseErrors" in {
      ParseError.liftParseError(failedParse) mustBe a[Left[InvalidItemNumber, _]]
    }

    "returns the result sequence when there are no parseErrors" in {
      val result = ParseError.liftParseError(goodParse)
      result mustBe a[Right[Seq[String], _]]
      result.right.get.length mustBe 2
    }
  }


}
