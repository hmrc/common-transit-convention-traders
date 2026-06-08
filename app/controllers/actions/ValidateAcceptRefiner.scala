/*
 * Copyright 2025 HM Revenue & Customs
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

import cats.implicits.catsSyntaxEitherId
import models.MediaType
import models.Version
import models.VersionedHeader
import models.common.errors.PresentationError
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import play.api.libs.json.Json
import play.api.mvc.Results.NotAcceptable
import play.api.mvc.Results.Status
import play.api.mvc.ActionRefiner
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.mvc.WrappedRequest

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.matching.Regex

final case class ValidatedVersionedRequest[T](
  versionedHeader: VersionedHeader,
  authenticatedRequest: AuthenticatedRequest[T]
) extends WrappedRequest[T](authenticatedRequest)

final class ValidateAcceptRefiner @Inject() (implicit val ec: ExecutionContext, mat: Materializer)
    extends ActionRefiner[AuthenticatedRequest, ValidatedVersionedRequest] {

  private val versionedRegex: Regex = """^application/vnd\.hmrc\.(\d*\.\d*)\+(.+)""".r

  private def validateAcceptHeader(authenticatedRequest: AuthenticatedRequest[?]): Either[PresentationError, VersionedHeader] =
    authenticatedRequest.headers.get(play.api.http.HeaderNames.ACCEPT) match {
      case Some(versionedRegex(ver, ext)) =>
        for {
          mediaType <- MediaType.fromString(ext)
          version   <- Version.fromString(ver)
          result    <- VersionedHeader(mediaType, version).asRight
        } yield result
      case Some(invalidHeader) => PresentationError.notAcceptableError(s"Invalid accept header: $invalidHeader").asLeft
      case None                => PresentationError.notAcceptableError("The Accept header is missing.").asLeft
    }

  def refine[T](authenticatedRequest: AuthenticatedRequest[T]): Future[Either[Result, ValidatedVersionedRequest[T]]] =
    validateAcceptHeader(authenticatedRequest) match {
      case Left(err) =>
        clearSource(authenticatedRequest)
        Future.successful(Left(Status(err.code.statusCode)(Json.toJson(err))))
      case Right(versionHeader) =>
        Future.successful(Right(ValidatedVersionedRequest(versionHeader, authenticatedRequest)))
    }

  def apply(allowed: Set[VersionedHeader] = Set.empty): ActionRefiner[AuthenticatedRequest, ValidatedVersionedRequest] =
    new ActionRefiner[AuthenticatedRequest, ValidatedVersionedRequest] {
      def refine[T](authenticatedRequest: AuthenticatedRequest[T]): Future[Either[Result, ValidatedVersionedRequest[T]]] =
        validateAcceptHeader(authenticatedRequest) match {
          case Left(err) =>
            clearSource(authenticatedRequest)
            Future.successful(Left(Status(err.code.statusCode)(Json.toJson(err))))
          case Right(versionedHeader) =>
            if (allowed.nonEmpty && !allowed.contains(versionedHeader))
              Future.successful(NotAcceptable(Json.toJson(PresentationError.notAcceptableError("The Accept header is missing or invalid."))).asLeft)
            else
              Future.successful(Right(ValidatedVersionedRequest(versionedHeader, authenticatedRequest)))
        }

      override protected def executionContext: ExecutionContext = ec
    }

  private def clearSource(request: Request[?]): Unit =
    request.body match {
      case source: Source[_, _] => val _ = source.runWith(Sink.ignore)
      case _                    => ()
    }

  override protected def executionContext: ExecutionContext = ec
}
