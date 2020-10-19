package models.request

import play.api.mvc.{Request, WrappedRequest}

import scala.xml.NodeSeq

case class GuaranteedRequest[A](request: Request[A], newXml: NodeSeq) extends WrappedRequest[A](request){

}
