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

import akka.stream.scaladsl.Source
import akka.util.ByteString

import java.nio.charset.StandardCharsets

object TestSourceProvider extends TestSourceProvider

trait TestSourceProvider {

  def singleUseStringSource(str: => String): Source[ByteString, _] =
    singleUseSource0(Seq(ByteString(str, StandardCharsets.UTF_8)).iterator)

  def singleUseSource[A](a: => Iterable[A]): Source[A, _] =
    singleUseSource0(a.iterator)

  private def singleUseSource0[A](iterator: => Iterator[A]): Source[A, _] = {
    lazy val singleUseIterator = iterator
    Source.fromIterator {
      () =>
        if (singleUseIterator.hasNext) singleUseIterator
        else throw new IllegalStateException("Iterator used more than once!")
    }
  }

}
