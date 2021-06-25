package controllers.actions

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import javax.inject.Inject
import utils.analysis.MessageAnalyser
import scala.xml.NodeSeq
import play.api.mvc.Request
import play.api.mvc.ActionTransformer

trait AnalyseMessageActionProvider {
  def apply[F[_] <: Request[_]](): ActionTransformer[F, F]
}

class AnalyseMessageActionProviderImpl @Inject() (
  analyser: MessageAnalyser,
  ec: ExecutionContext
) extends AnalyseMessageActionProvider {

  override def apply[F[_] <: Request[_]](): ActionTransformer[F, F] =
    new AnalyseMessageAction[F](analyser, ec)
}

class AnalyseMessageAction[F[_] <: Request[_]](analyser: MessageAnalyser, ec: ExecutionContext) extends ActionTransformer[F, F] {

  override protected def transform[A](request: F[A]): Future[F[A]] = {
    request.body match {
      case body: NodeSeq if body.nonEmpty =>
        analyser.trackMessageStats(body)
      case _ =>
    }
    Future.successful(request)
  }

  override protected def executionContext: ExecutionContext = ec

}
