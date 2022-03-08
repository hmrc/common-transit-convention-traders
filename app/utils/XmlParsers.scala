package utils

import play.api.mvc.{BaseControllerHelpers, BodyParser}

import scala.concurrent.ExecutionContext
import scala.xml.{Elem, NodeSeq}

trait XmlParsers { self: BaseControllerHelpers =>

  def removingXmlNamespaceParser(implicit ec: ExecutionContext): BodyParser[NodeSeq] = parse.xml.map {
    nodeSeq =>
      nodeSeq
        .headOption
        .flatMap {
          case x: Elem => Option(x)
          case _       => None
        }
        .filter(node => node.attribute("xmlns:xsi").isDefined || node.attribute("xmlns:xsd").isDefined)
        .map(node => node.copy(attributes = node.attributes.remove("xmlns:xsi").remove("xmlns:xsd")))
        .getOrElse(nodeSeq)
  }

}
