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

package utils

import base.TestActorSystem
import base.TestSourceProvider
import cats.data.EitherT
import models.common.errors.PresentationError
import org.apache.pekko.stream.scaladsl.Sink
import org.scalacheck.Gen
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.Logging
import play.api.libs.Files
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.Files.TemporaryFileCreator

import java.nio.file.Path
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import scala.util.Success
import scala.util.Try

class StreamWithFileSpec
    extends AnyFreeSpec
    with TestActorSystem
    with Matchers
    with TestSourceProvider
    with ScalaCheckDrivenPropertyChecks
    with OptionValues
    with ScalaFutures {

  case class DummyTemporaryFileCreator(temporaryFile: Files.TemporaryFile) extends TemporaryFileCreator {

    override def create(prefix: String, suffix: String): Files.TemporaryFile = temporaryFile

    override def create(path: Path): Files.TemporaryFile = temporaryFile

    override def delete(file: Files.TemporaryFile): Try[Boolean] = Success(true)
  }

  "withReusableSource" - {
    object Harness extends StreamWithFile with Logging

    "using a reusable source on a single use source should create a file when streamed once" in {

      // create the file now so we can check it later.
      implicit val temporaryFileCreator: DummyTemporaryFileCreator = DummyTemporaryFileCreator(SingletonTemporaryFileCreator.create())
      temporaryFileCreator.temporaryFile.deleteOnExit()

      val string = Gen.stringOfN(10, Gen.alphaNumChar).sample.value
      val source = singleUseStringSource(string)

      Harness.withReusableSource(source) {
        source =>
          EitherT[Future, PresentationError, Unit] {
            source
              .runWith(Sink.ignore)
              .map {
                _ =>
                  // we now load the file and it should contain the string
                  val fileContents = Source.fromFile(temporaryFileCreator.temporaryFile.toFile)
                  try fileContents.mkString mustBe string
                  finally fileContents.close()
                  Right(()) // this simply fulfils the contract
              }
          }
      }
    }

    "using a reusable source on a single use source can be used multiple times in succession and get the same result" in {

      // create the file now so we can check it later.
      implicit val temporaryFileCreator: DummyTemporaryFileCreator = DummyTemporaryFileCreator(SingletonTemporaryFileCreator.create())
      temporaryFileCreator.temporaryFile.deleteOnExit()

      val string = Gen.stringOfN(10, Gen.alphaNumChar).sample.value
      val source = singleUseStringSource(string)

      Harness.withReusableSource(source) {
        source =>
          val future = for {
            first  <- source.runWith(Sink.head).map(_.utf8String)
            second <- source.runWith(Sink.head).map(_.utf8String)
            third  <- source.runWith(Sink.head).map(_.utf8String)
            fourth <- source.runWith(Sink.head).map(_.utf8String)
          } yield (first, second, third, fourth)

          whenReady(future) {
            result =>
              result._1 mustBe string
              result._2 mustBe string
              result._3 mustBe string
              result._4 mustBe string
          }

          EitherT.rightT[Future, PresentationError](())
      }
    }
  }

  "withReusableSourceAndSize" - {
    object Harness extends StreamWithFile with Logging

    "using a reusable source on a single use source should create a file when streamed once" in {

      // create the file now so we can check it later.
      implicit val temporaryFileCreator: DummyTemporaryFileCreator = DummyTemporaryFileCreator(SingletonTemporaryFileCreator.create())
      temporaryFileCreator.temporaryFile.deleteOnExit()

      val string = Gen.stringOfN(10, Gen.alphaNumChar).sample.value
      val source = singleUseStringSource(string)

      Harness.withReusableSourceAndSize(source) {
        (source, size) =>
          EitherT[Future, PresentationError, Unit] {
            source
              .runWith(Sink.ignore)
              .map {
                _ =>
                  // we now load the file and it should contain the string
                  val fileContents = Source.fromFile(temporaryFileCreator.temporaryFile.toFile)
                  size mustBe string.size
                  try fileContents.mkString mustBe string
                  finally fileContents.close()
                  Right(()) // this simply fulfils the contract
              }
          }
      }
    }

    "using a reusable source on a single use source can be used multiple times in succession and get the same result" in {

      // create the file now so we can check it later.
      implicit val temporaryFileCreator: DummyTemporaryFileCreator = DummyTemporaryFileCreator(SingletonTemporaryFileCreator.create())
      temporaryFileCreator.temporaryFile.deleteOnExit()

      val string = Gen.stringOfN(10, Gen.alphaNumChar).sample.value
      val source = singleUseStringSource(string)

      Harness.withReusableSourceAndSize(source) {
        (source, size) =>
          val future = for {
            first  <- source.runWith(Sink.head).map(_.utf8String)
            second <- source.runWith(Sink.head).map(_.utf8String)
            third  <- source.runWith(Sink.head).map(_.utf8String)
            fourth <- source.runWith(Sink.head).map(_.utf8String)
          } yield (first, second, third, fourth)

          whenReady(future) {
            result =>
              size mustBe string.size
              result._1 mustBe string
              result._2 mustBe string
              result._3 mustBe string
              result._4 mustBe string
          }

          EitherT.rightT[Future, PresentationError](())
      }
    }
  }

}
