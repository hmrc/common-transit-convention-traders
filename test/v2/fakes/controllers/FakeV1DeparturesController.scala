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

package v2.fakes.controllers

import controllers.V1DeparturesController
import models.domain.DepartureId
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.BaseController
import play.api.mvc.Request
import play.api.test.Helpers.stubControllerComponents

import java.time.OffsetDateTime
import scala.xml.NodeSeq

class FakeV1DeparturesController extends BaseController with V1DeparturesController {

  override val controllerComponents = stubControllerComponents()

  override def submitDeclaration(): Action[NodeSeq] = Action(parse.xml) {
    _: Request[NodeSeq] =>
      Accepted(Json.obj("version" -> 1))
  }

  override def getDeparture(departureId: DepartureId): Action[AnyContent] = Action {
    _ =>
      Ok(Json.obj("version" -> 1))
  }

  override def getDeparturesForEori(updatedSince: Option[OffsetDateTime]): Action[AnyContent] = Action {
    _ =>
      Ok(Json.obj("version" -> 1))
  }

}
