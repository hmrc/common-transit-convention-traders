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

package controllers.actions

import cats.data.NonEmptyList
import javax.inject.Inject
import models.SchemaValidationError
import models.request.XSDFile
import play.api.libs.json.Json
import play.api.mvc.Results.BadRequest
import play.api.mvc.Results.NotImplemented
import play.api.mvc.ActionRefiner
import play.api.mvc.Request
import play.api.mvc.Result
import services.XmlError
import services.XmlValidationService

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.xml.NodeSeq

class ValidateArrivalMessageAction @Inject() (xmlValidationService: XmlValidationService)(implicit val executionContext: ExecutionContext)
    extends ActionRefiner[Request, Request] {

  override protected def refine[A](request: Request[A]): Future[Either[Result, Request[A]]] =
    request.body match {
      case body: NodeSeq =>
        if (body.nonEmpty) {
          val rootElementName = body.head.label
          XSDFile.Arrival.SupportedMessages.get(rootElementName) match {
            case Some(xsd) =>
              xmlValidationService.validate(body.toString, xsd) match {
                case Right(_) =>
                  Future.successful(Right(request))
                case Left(errors: NonEmptyList[SchemaValidationError]) =>
                  val response = Json.toJson(errors.toList)
                  Future.successful(Left(BadRequest(response)))
              }
            case None =>
              Future.successful(Left(NotImplemented))
          }
        } else {
          Future.successful(Left(BadRequest(XmlError.RequestBodyEmptyMessage)))
        }
      case _ =>
        Future.successful(Left(BadRequest(XmlError.RequestBodyInvalidTypeMessage)))
    }
}
