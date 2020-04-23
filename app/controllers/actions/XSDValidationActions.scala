/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.Inject
import models.request.{ArrivalNotificationXSD, UnloadingRemarksXSD}
import play.api.mvc.{ActionRefiner, Request, Result}
import play.api.mvc.Results.{BadRequest, NotImplemented}
import services.XmlValidationService

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

class ValidateArrivalNotification @Inject()(xmlValidationService: XmlValidationService)(
  implicit val executionContext: ExecutionContext)
  extends ActionRefiner[Request, Request] {

  override protected def refine[A](request: Request[A]): Future[Either[Result, Request[A]]] = {
    request.body match {
      case body: NodeSeq =>
        if (body.nonEmpty) {
          xmlValidationService.validate(body.toString, ArrivalNotificationXSD) match {
            case Right(_) =>
              Future.successful(Right(request))
            case Left(_) =>
              Future.successful(Left(BadRequest))
          }
        } else {
          Future.successful(Left(BadRequest))
        }
      case _ =>
        Future.successful(Left(BadRequest))
    }
  }
}

class ValidateMessage @Inject()(xmlValidationService: XmlValidationService)(
  implicit val executionContext: ExecutionContext)
  extends ActionRefiner[Request, Request] {

  private val supportedMessageTypes = Map("CC044A" -> UnloadingRemarksXSD)

  override protected def refine[A](request: Request[A]): Future[Either[Result, Request[A]]] = {
    request.body match {
      case body: NodeSeq =>
        if (body.nonEmpty) {
          val rootElementName = body.head.label

          supportedMessageTypes.get(rootElementName) match {
            case Some(xsd) =>
              xmlValidationService.validate(body.toString, xsd) match {
                case Right(_) =>
                  Future.successful(Right(request))
                case Left(_) =>
                  Future.successful(Left(BadRequest))
              }
            case None =>
              Future.successful(Left(NotImplemented))
          }
        } else {
          Future.successful(Left(BadRequest))
        }
      case _ =>
        Future.successful(Left(BadRequest))
    }
  }
}
