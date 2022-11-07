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

package routing

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.Inject
import controllers.V1ArrivalMovementController
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import v2.controllers.V2ArrivalsController
import v2.controllers.stream.StreamingParsers
import v2.models.{MovementId => V2ArrivalId}
import models.domain.{ArrivalId => V1ArrivalId}

import java.time.OffsetDateTime

import java.time.OffsetDateTime

class ArrivalsRouter @Inject() (
  val controllerComponents: ControllerComponents,
  v1Arrivals: V1ArrivalMovementController,
  v2Arrivals: V2ArrivalsController
)(implicit
  val materializer: Materializer
) extends BaseController
    with StreamingParsers
    with VersionedRouting {

  def createArrivalNotification(): Action[Source[ByteString, _]] =
    route {
      case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON) => v2Arrivals.createArrivalNotification()
      case _                                                         => v1Arrivals.createArrivalNotification()
    }

<<<<<<< HEAD
  def getArrival(arrivalId: String): Action[Source[ByteString, _]] =
    route {
      case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON) =>
        runIfBound[V2ArrivalId](
          "arrivalId",
          arrivalId,
          v2Arrivals.getArrival(_)
        )
      case _ =>
        runIfBound[V1ArrivalId](
          "arrivalId",
          arrivalId,
          v1Arrivals.getArrival(_)
        )
    }

=======
>>>>>>> origin/CTCP-1452-get-arrivals-eori
  def getArrivalsForEori(updatedSince: Option[OffsetDateTime] = None): Action[Source[ByteString, _]] = route {
    case Some(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON) => v2Arrivals.getArrivalsForEori(updatedSince)
    case _                                                         => v1Arrivals.getArrivalsForEori(updatedSince)
  }

}
