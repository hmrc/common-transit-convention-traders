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

package utils

import play.api.mvc.BaseControllerHelpers
import play.api.mvc.BodyParser

import scala.concurrent.ExecutionContext
import scala.xml.Elem
import scala.xml.MetaData
import scala.xml.Node
import scala.xml.NodeSeq
import scala.xml.PrefixedAttribute
import scala.xml.TopScope
import scala.xml.UnprefixedAttribute

trait XmlHelper {

  def stripNamespaceFromRoot(nodeSeq: NodeSeq): NodeSeq =
    nodeSeq.headOption
      .map {
        case x: Elem if x.scope != TopScope => removeNamespaceFromElem(x)
        case x                              => x
      }
      .getOrElse(nodeSeq)

  // The following is inspired by https://stackoverflow.com/questions/12535014/scala-completely-remove-namespace-from-xml
  def removeNamespaceFromElem(elem: Elem): Elem =
    elem.copy(
      scope = TopScope,
      prefix = null,
      attributes = removeNamespacesFromAttributes(elem.attributes),
      child = elem.child.map(removeNamespaceFromNode)
    )

  def removeNamespaceFromNode(node: Node): Node = node match {
    case elem: Elem => removeNamespaceFromElem(elem)
    case _          => node
  }

  def removeNamespacesFromAttributes(metadata: MetaData): MetaData = metadata match {
    case UnprefixedAttribute(k, v, n)  => new UnprefixedAttribute(k, v, removeNamespacesFromAttributes(n))
    case PrefixedAttribute(_, k, v, n) => new UnprefixedAttribute(k, v, removeNamespacesFromAttributes(n))
    case _                             => metadata
  }

}

trait NamespaceStrippingXmlParser extends XmlHelper { self: BaseControllerHelpers =>

  def namespaceStrippingXmlParser(implicit ec: ExecutionContext): BodyParser[NodeSeq] = parse.xml.map(stripNamespaceFromRoot)
}