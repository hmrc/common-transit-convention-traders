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

package v2.controllers

import cats.syntax.all._
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import v2.models.errors.FailedToValidateError

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ErrorTranslatorSpec extends AnyFreeSpec with Matchers with OptionValues with ScalaFutures with MockitoSugar {

  object Harness extends ErrorTranslator

  import Harness._

  "Validation error" - {
    "for a success" in {
      val input = Right[FailedToValidateError, Unit](()).toEitherT[Future]
      whenReady(input.asBaseError.value) {
        _ mustBe Right(())
      }
    }
  }

}
