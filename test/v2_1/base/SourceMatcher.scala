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

package v2_1.base

import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatcher
import play.api.test.Helpers.await
import play.api.test.Helpers.defaultAwaitTimeout

object SourceMatcher {
  def apply(comparison: String => Boolean)(implicit mat: Materializer): ArgumentMatcher[Source[ByteString, _]] = new SourceMatcher(comparison)
}

class SourceMatcher(comparison: String => Boolean)(implicit mat: Materializer) extends ArgumentMatcher[Source[ByteString, _]] {

  override def matches(argument: Source[ByteString, _]): Boolean =
    comparison(await(argument.reduce(_ ++ _).map(_.utf8String).runWith(Sink.head[String])))
}
