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

package models.response

import controllers.routes
import models.domain.Arrivals
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import utils.CallOps._

object HateoasResponseArrivals {

  def apply(arrivals: Arrivals): JsObject = {
    val arrivalUrl = routing.routes.ArrivalsRouter.getArrivalsForEori().urlWithContext

    Json.obj(
      "_links" -> Json.obj(
        "self" -> Json.obj("href" -> arrivalUrl)
      ),
      "_embedded" -> Json.obj(
        "arrivals" -> arrivals.arrivals.map {
          x =>
            HateoasResponseArrival(x)
        },
        "retrievedArrivals" -> arrivals.retrievedArrivals,
        "totalArrivals"     -> arrivals.totalArrivals
      )
    )
  }
}
