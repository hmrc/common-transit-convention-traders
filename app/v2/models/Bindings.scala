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

package v2.models

import play.api.mvc.PathBindable

object Bindings {

  val idPattern = "^[0-9a-f]{16}$".r

  def hexBinding[T](bindFn: String => T, unbindFn: T => String): PathBindable[T] = new PathBindable[T] {

    override def bind(key: String, value: String): Either[String, T] =
      if (idPattern.pattern.matcher(value).matches()) Right(bindFn(value))
      else Left(s"$key: Value $value is not a 16 character hexadecimal string")

    override def unbind(key: String, value: T): String = unbindFn(value)
  }

  implicit val messageIdBinding  = hexBinding[MessageId](MessageId.apply, _.value)
  implicit val movementIdBinding = hexBinding[MovementId](MovementId.apply, _.value)

}
