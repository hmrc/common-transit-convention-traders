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

package v2.models.errors

import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.MovementType

sealed trait PersistenceError

object PersistenceError {
  case class MessageNotFound(movementId: MovementId, messageId: MessageId)        extends PersistenceError
  case class MovementNotFound(movementId: MovementId, movementType: MovementType) extends PersistenceError
  case class MovementsNotFound(eori: EORINumber, movementType: MovementType)      extends PersistenceError
  case class UnexpectedError(thr: Option[Throwable] = None)                       extends PersistenceError
  case class MessageIdError()                                                     extends PersistenceError
}
