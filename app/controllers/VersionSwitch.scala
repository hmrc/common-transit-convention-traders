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

package controllers

import play.api.Logging
import play.api.mvc.{Action, Request}

import scala.concurrent.Future
import scala.xml.NodeSeq
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

trait VersionSwitch {
 self: BackendController with Logging =>

  def versionSwitch(v1Action: Action[NodeSeq], v2Action: Action[NodeSeq]): Action[NodeSeq] =
    Action.async(parse.xml) {
    (request: Request[NodeSeq]) =>
      request.headers.get("accept") match {
        case Some("application/vnd.hmrc.2.0+json") => v2Action(request)

        case Some("application/vnd.hmrc.1.0+json") => v1Action(request)

        case None => v1Action(request)

        case Some(headerVal) => {
          logger.info("Unknown Accept-Header found: $headerVal")
          Future.successful(UnsupportedMediaType(s"Unsupported Accept-Header: $headerVal"))
        }
      }
  }
}