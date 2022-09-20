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

package v2.utils

import cats.data.EitherT

import scala.concurrent.Future

sealed trait FutureConversion[A] {
  def toFuture(a: A): Future[_]
}

object FutureConversions extends FutureConversions

trait FutureConversions {

  implicit def eitherTInstance[L, R]: FutureConversion[EitherT[Future, L, R]] = new FutureConversion[EitherT[Future, L, R]] {
    override def toFuture(a: EitherT[Future, L, R]): Future[_] = a.value
  }

  implicit def futureResultInstance[A]: FutureConversion[Future[A]] = new FutureConversion[Future[A]] {
    override def toFuture(a: Future[A]): Future[_] = a
  }
}
