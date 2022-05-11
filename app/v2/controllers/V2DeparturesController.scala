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

package v2.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.syntax.all._
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import play.api.Logging
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.ControllerComponents
import play.api.mvc.Result
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import v2.controllers.actions.AuthNewEnrolmentOnlyAction
import v2.controllers.actions.MessageSizeAction
import v2.controllers.stream.StreamingParsers
import v2.models.errors.BaseError
import v2.services.ValidationService

import scala.concurrent.Future
import scala.util.Try

@ImplementedBy(classOf[V2DeparturesControllerImpl])
trait V2DeparturesController {

  def submitDeclaration(): Action[Source[ByteString, _]]

}

@Singleton
class V2DeparturesControllerImpl @Inject() (
    val controllerComponents: ControllerComponents,
    authActionNewEnrolmentOnly: AuthNewEnrolmentOnlyAction,
    validationService: ValidationService,
    messageSizeAction: MessageSizeAction)(implicit val materializer: Materializer) extends BaseController
  with V2DeparturesController
  with Logging
  with StreamingParsers
  with VersionedRouting {

  def submitDeclaration(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction).async(streamFromMemory) {
      implicit request =>
        logger.info("Version 2 of endpoint has been called")

        implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
        Try(Files.SingletonTemporaryFileCreator.create())
          .toEither
          .leftMap {
            thr =>
              request.body.runWith(Sink.ignore)
              BaseError.internalServiceError(cause = Some(thr))
          }
          .map(temporaryFile => {
            // TODO: MessageType
            // Because we have an open stream, we **must** do something with it. For now, we send it to the ignore sink.
            (for {
              validated <- validationService.validateXML("cc015c", request.body.alsoTo(FileIO.toPath(temporaryFile.path)))
            } yield validated)
              .fold[Result](
                baseError => Status(baseError.code.statusCode)(Json.toJson(baseError)),
                _         => Accepted
              )
              .flatTap(_ => Future.successful(temporaryFile.delete()))
          })
          .fold(error => Future.successful(InternalServerError(Json.toJson(error))), result => result)

    }

}
