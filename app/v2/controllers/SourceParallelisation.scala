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

import akka.stream.Materializer
import akka.stream.contrib.SwitchMode
import akka.stream.contrib.Valve
import akka.stream.scaladsl.BroadcastHub
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Source
import cats.syntax.all._

import scala.concurrent.ExecutionContext

trait SourceParallelisation {

  def callInParallel[In, Out](in: Source[In, _])(block: Source[In, _] => Out)(implicit materializer: Materializer, ec: ExecutionContext): Out = {
    // First, attach the valve and retrieve it via pre-materialisation - we create it CLOSED so that we don't start
    // pushing the stream as soon as we attach the first subscriber
    val (valve, preMatSource) = in.viaMat(Valve(SwitchMode.Close))(Keep.right).preMaterialize()

    // Now we attach the returned source to a Broadcast Hub, which creates a source that we'll attach to
    // our multiple subscribers
    //
    // This buffer size can be whatever.
    val broadcastSource = preMatSource.toMat(BroadcastHub.sink(bufferSize = 256))(Keep.right).run()

    // Wire up the services as specified
    val result = block(broadcastSource)

    // Now we've completed setup, we open the valve and let the data flow
    valve.flatTap(
      x => x.flip(SwitchMode.Open)
    )

    // Return the result from the block, whatever that might be (generally a future based object)
    result
  }

}
