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

import akka.stream.scaladsl.Sink
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import v2.base.TestActorSystem
import v2.base.TestSourceProvider

import scala.concurrent.ExecutionContext.Implicits.global

class SourceParallelisationSpec extends AnyFreeSpec with Matchers with ScalaFutures with TestActorSystem with TestSourceProvider {

  val expected = 1 to 10

  object Harness extends SourceParallelisation

  "With the source parallelisation, and a single use source that runs once from 1 to 10" - {

    "with one sink, should get 1 to 10 from the sink" in {
      val result = Harness.callInParallel(singleUseSource((1 to 10))) {
        source =>
          source.runWith(Sink.seq)
      }

      whenReady(result) {
        _ mustBe expected
      }
    }

    "with two sinks, should get 1 to 10 from both sinks" in {
      val result = Harness.callInParallel(singleUseSource((1 to 10))) {
        source =>
          val a = source.runWith(Sink.seq)
          val b = source.runWith(Sink.seq)

          // we have to run the above
          for {
            first  <- a
            second <- b
          } yield (first, second)
      }

      whenReady(result) {
        r =>
          r._1 mustBe expected
          r._2 mustBe expected
      }
    }

    "with three sinks, should get 1 to 10 from all sinks" in {
      val result = Harness.callInParallel(singleUseSource((1 to 10))) {
        source =>
          val a = source.runWith(Sink.seq)
          val b = source.runWith(Sink.seq)
          val c = source.runWith(Sink.seq)

          // we have to run the above
          for {
            first  <- a
            second <- b
            third  <- c
          } yield (first, second, third)
      }

      whenReady(result) {
        r =>
          r._1 mustBe expected
          r._2 mustBe expected
          r._3 mustBe expected
      }
    }

  }

}
