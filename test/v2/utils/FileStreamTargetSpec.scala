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

import cats.data.EitherT
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers

import scala.concurrent.Future

class FileStreamTargetSpec extends AnyFreeSpec with Matchers {

  "Conversion to Future returns" - {
    "the same future for the Future -> Future conversion" in {
      val f = Future.successful("test")

      FutureConversions.futureResultInstance[String].toFuture(f) must be theSameInstanceAs f
    }

    "the backing future for the EitherT -> Future conversion" in {

      val futureEither: Future[Either[Throwable, Unit]] = Future.successful(Right(()))
      val eitherT                                       = EitherT(futureEither)

      FutureConversions.eitherTInstance[Throwable, Unit].toFuture(eitherT) must be theSameInstanceAs futureEither
    }
  }

}
