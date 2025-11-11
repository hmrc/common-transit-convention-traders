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

class DocumentationController @Inject() (assets: Assets, cc: ControllerComponents, config: AppConfig) extends BackendController(cc) {

  def determineDefinition: String =
    (config.deployV3Beta, config.deployV3Alpha) match {
      case (true, _) => "v2_1-v3_0-beta-definition.json"
      case (_, true) => "v2_1-v3_0-alpha-definition.json"
      case _         => "v2_1-definition.json"
    }

  def definition(): Action[AnyContent] = assets.at("/public/api", determineDefinition)

  def raml(version: String, file: String): Action[AnyContent] =
    assets.at(s"/public/api/conf/$version", file)
}
