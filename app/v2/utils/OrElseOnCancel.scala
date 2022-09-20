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

import akka.stream.Attributes
import akka.stream.Attributes.Name
import akka.stream.FlowShape
import akka.stream.Graph
import akka.stream.Inlet
import akka.stream.Outlet
import akka.stream.SourceShape
import akka.stream.UniformFanInShape
import akka.stream.scaladsl.GraphDSL
import akka.stream.stage.GraphStage
import akka.stream.stage.GraphStageLogic
import akka.stream.stage.InHandler
import akka.stream.stage.OutHandler

import scala.annotation.unchecked.uncheckedVariance

object OrElseOnCancel {
  private val instance = new OrElseOnCancel[Nothing]

  def apply[T](): OrElseOnCancel[T] = instance.asInstanceOf[OrElseOnCancel[T]]

  // This has been adapted from https://github.com/akka/akka/blob/7abc41cf4e7e8827393b181cd06c5f8ea684e696/akka-stream/src/main/scala/akka/stream/scaladsl/Graph.scala#L1370
  // code used from February 2022
  // Used under the Apache Licence: https://github.com/akka/akka/blob/7abc41cf4e7e8827393b181cd06c5f8ea684e696/LICENSE
  def orElseOrCancelGraph[Out, Mat2](secondary: Graph[SourceShape[Out], Mat2]): Graph[FlowShape[Out @uncheckedVariance, Out], Mat2] =
    GraphDSL.createGraph(secondary) {
      implicit b => secondary =>
        import GraphDSL.Implicits._

        val orElse = b.add(OrElseOnCancel[Out]())

        secondary ~> orElse.in(1)

        FlowShape(orElse.in(0), orElse.out)
    }

  final class OrElseOnCancel[T] extends GraphStage[UniformFanInShape[T, T]] {
    val incomingRequest = Inlet[T]("OrElse.primary")
    val fileBasedSource = Inlet[T]("OrElse.secondary")
    val out             = Outlet[T]("OrElse.out")

    override val shape: UniformFanInShape[T, T] = UniformFanInShape(out, incomingRequest, fileBasedSource)

    override protected def initialAttributes: Attributes = Attributes(Name("orElseOnCancel"))

    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
      new GraphStageLogic(shape) with OutHandler with InHandler {

        private[this] var currentSource            = incomingRequest
        private[this] var incomingRequestHasPushed = false

        /*
         * When the stream is attached, we start asking to pull from the current source.
         * If that source is an expended request stream, this will cause an exception,
         * bubbling to onUpstreamFailure below. Otherwise, this will start to signal to
         * the source that it needs to start pushing elements, see below.
         */
        override def onPull(): Unit =
          pull(currentSource)

        /*
         * This method is called when the request stream has pushed its data to here. This indicates
         * a success. If the request source has pushed something, then we know it's live and we
         * cancel the file based source for this run (every time we call runWith/run we create a
         * new graph stage so the file source will be re-initialised each time the source is attached
         * to a sink) -- any exceptions after the first push is a different error resulting in incomplete
         * data.
         *
         * If the request has been expended, this will not be called - setHandler below adds the file based
         * push handler.
         */
        override def onPush(): Unit = {
          if (!incomingRequestHasPushed) {
            incomingRequestHasPushed = true
            cancel(fileBasedSource)
          }
          val elem = grab(incomingRequest)
          push(out, elem)
        }

        /** This will be called if the request body is empty.
          */
        override def onUpstreamFinish(): Unit = {
          if (!incomingRequestHasPushed && !isClosed(fileBasedSource)) {
            cancel(fileBasedSource)
          }
          completeStage()
        }

        /*
         * This is the key method that controls which stream we pull from.
         *
         * When the source attaches to a sink this starts the pipeline. If
         * we start pulling from the Play request stream and the request has
         * already been consumed, an exception will be thrown and the application
         * will then bubble up the cancellation. That cancellation will end up
         * at this part of the stage -- it's an upstream failure!
         *
         * If we receive a failure, and we haven't yet have an element pushed from the
         * incoming request, then we fallback to the file that we should have populated.
         * We check that the file source hasn't been closed (if it has, we can't pull from it!)
         * and then we switch out the source that we're pulling from so that we automatically
         * get our data from the file instead.
         */
        override def onUpstreamFailure(ex: Throwable): Unit =
          // TODO: Get the error when the primary error is the stream failing
          if (!incomingRequestHasPushed && !isClosed(fileBasedSource)) {
            // If we get an error immediately, then attach to secondary as if nothing
            // has happened
            currentSource = fileBasedSource
            if (isAvailable(out)) pull(fileBasedSource)
          } else fail(out, ex)

        // Attaches the file based source with an extra handler for receiving data from the file source
        // (pulling is handled above)
        setHandler(
          fileBasedSource,
          new InHandler {

            override def onPush(): Unit =
              push(out, grab(fileBasedSource))

            override def onUpstreamFinish(): Unit =
              if (isClosed(incomingRequest)) completeStage()
          }
        )

        // The request handler.
        setHandlers(incomingRequest, out, this)
      }

    override def toString: String = s"OrElseOrCancel"

  }

}
