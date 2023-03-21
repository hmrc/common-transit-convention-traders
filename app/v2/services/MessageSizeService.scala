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

package v2.services

import cats.data.EitherT
import config.AppConfig
import v2.models.errors.PresentationError

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.Future

@Singleton
class MessageSizeService @Inject() (config: AppConfig) {
  private lazy val messageSizeLimit = config.messageSizeLimit

  def contentSizeIsLessThanLimit(size: Long): EitherT[Future, PresentationError, Unit] = EitherT {
    if (size <= messageSizeLimit) Future.successful(Right(()))
    else Future.successful(Left(PresentationError.entityTooLargeError(s"Your message size must be less than $messageSizeLimit bytes")))
  }

}
