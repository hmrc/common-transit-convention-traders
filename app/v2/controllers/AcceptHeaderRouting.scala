package v2.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.http.HeaderNames
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.BaseController
import play.api.mvc.Request
import v2.controllers.stream.StreamingParsers
import v2.models.errors.PresentationError

import scala.concurrent.Future

trait AcceptHeaderRouting {
  self: BaseController with StreamingParsers =>

  def acceptHeaderRoute(routes: PartialFunction[Option[String], Action[_]])(implicit materializer: Materializer): Action[Source[ByteString, _]] =
    Action.async(streamFromMemory) {
      (request: Request[Source[ByteString, _]]) =>
        routes
          .lift(request.headers.get(HeaderNames.ACCEPT))
          .map(
            action => action(request).run(request.body)
          )
          .getOrElse {
            // To avoid a memory leak, we need to ensure we run the request stream and ignore it.
            request.body.to(Sink.ignore).run()
            Future.successful(
              NotAcceptable(
                Json.toJson(
                  PresentationError.notAcceptableError(
                    request.headers
                      .get(HeaderNames.ACCEPT)
                      .map(
                        headerValue => s"Accept header $headerValue is not supported!"
                      )
                      .getOrElse(s"Accept header is required!")
                  )
                )
              )
            )
          }
    }
}
