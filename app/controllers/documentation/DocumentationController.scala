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

package controllers.documentation

import config.AppConfig
import controllers.Assets

import javax.inject.Inject
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.Future

class DocumentationController @Inject() (assets: Assets, cc: ControllerComponents, appConfig: AppConfig) extends BackendController(cc) {

  def definition(): Action[AnyContent] =
    if (appConfig.version21BetaEnabled) {
      assets.at("/public/api", "v2_1-definition.json")
    } else { assets.at("/public/api", "definition.json") }

  def raml(version: String, file: String): Action[AnyContent] =
    if (!appConfig.version21BetaEnabled && version == "2.1") {
      Action.async(Future.successful(NotFound("Resource not found by Assets controller")))
    } else assets.at(s"/public/api/conf/$version", file)
}
