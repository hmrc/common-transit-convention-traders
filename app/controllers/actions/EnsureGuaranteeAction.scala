package controllers.actions

import com.google.inject.Inject
import models.request.GuaranteedRequest
import play.api.mvc.{ActionRefiner, Request, Result}

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq
import play.api.mvc.Results.BadRequest
import services.EnsureGuaranteeService

class EnsureGuaranteeAction @Inject()(ensureGuaranteeService: EnsureGuaranteeService)(
  implicit val executionContext: ExecutionContext)
  extends ActionRefiner[Request, GuaranteedRequest] {

  override protected def refine[A](request: Request[A]): Future[Either[Result, GuaranteedRequest[A]]] = {
    request.body match {
      case body: NodeSeq =>
        if (body.nonEmpty) {
          ensureGuaranteeService.ensureGuarantee(body) match {
            case Left(error) => Future.successful(Left(BadRequest(error)))
            case Right(newBody) => Future.successful(Right(GuaranteedRequest[A](request, newBody)))
          }
        } else {
          Future.successful(Left(BadRequest))
        }
      case _ =>
        Future.successful(Left(BadRequest))
    }
  }
}
