package v2.utils

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import v2.controllers.ErrorTranslator
import v2.models.errors.MessageFormatError
import v2.models.errors.PresentationError

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

trait MessageFormat extends ErrorTranslator {

  def convertSourceToString(
    source: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): EitherT[Future, MessageFormatError, String] = EitherT {
    source
      .reduce(_ ++ _)
      .map(_.utf8String)
      .runWith(Sink.head[String])
      .map(Right[MessageFormatError, String])
      .recover {
        case NonFatal(ex) => Left[MessageFormatError, String](MessageFormatError.UnexpectedError(Some(ex)))
      }
  }
}
