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

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import com.google.inject.ImplementedBy
import com.google.inject.Singleton

import scala.concurrent.Future

/*
 * This abstracts out logic for our stream system that prevents some of our tests from completing.
 *
 * The problem is that when we run our tests, we mock out calls that would normally consume the stream.
 * This means that the stream would never get consumed, so if we call the "await" function having not yet
 * consumed the stream, then we get a deadlock. This allows us to stub out this behaviour.
 */
@ImplementedBy(classOf[PreMaterialisedFutureProviderImpl])
trait PreMaterialisedFutureProvider {
  def apply(sink: Sink[ByteString, Future[_]])(implicit mat: Materializer): (Future[_], Sink[ByteString, _])
}

@Singleton
class PreMaterialisedFutureProviderImpl extends PreMaterialisedFutureProvider {

  override def apply(sink: Sink[ByteString, Future[_]])(implicit mat: Materializer): (Future[_], Sink[ByteString, _]) =
    sink.preMaterialize()
}
