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

package services

import java.io._
import java.net.URL

import javax.xml.parsers.SAXParserFactory
import javax.xml.validation.Schema
import models.request.{ArrivalNotificationXSD, XSDFile}
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import play.api.Logger

import scala.xml.factory.XMLLoader
import scala.xml.{Elem, NodeSeq, SAXParseException, SAXParser}

class XmlValidationService {

  private val logger     = Logger(getClass)
  private val schemaLang = javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI

  private def saxParser(schema: Schema): SAXParser = {
    val saxParser: SAXParserFactory = javax.xml.parsers.SAXParserFactory.newInstance()
    saxParser.setNamespaceAware(true)
    saxParser.setSchema(schema)
    saxParser.newSAXParser()
  }

  private val xsdFiles = Map("CC007A" -> ArrivalNotificationXSD)

  def validate(xml: NodeSeq): Either[XmlError, XmlValid] = {
    val rootElementName = xml.head.label

    xsdFiles.get(rootElementName) match {
      case Some(xsd) => validate(xml.toString(), xsd)
      case None      => Left(FailedToValidateXml("Schema not found for message '%s'." format rootElementName))
    }
  }

  def validate(xml: String, xsd: XSDFile): Either[XmlError, XmlValid] =
    try {

      val url: URL = getClass.getResource(xsd.filePath)

      val schema: Schema = javax.xml.validation.SchemaFactory.newInstance(schemaLang).newSchema(url)

      class CustomParseHandler extends DefaultHandler {

        override def error(e: SAXParseException): Unit = {
          logger.warn(e.getMessage)
          throw new SAXParseException(e.getMessage, e.getPublicId, e.getSystemId, e.getLineNumber, e.getColumnNumber)
        }
      }

      val xmlResponse: XMLLoader[Elem] = new scala.xml.factory.XMLLoader[scala.xml.Elem] {
        override def parser: SAXParser = saxParser(schema)

        override def adapter =
          new scala.xml.parsing.NoBindingFactoryAdapter with scala.xml.parsing.ConsoleErrorHandler
      }

      xmlResponse.parser.parse(new InputSource(new StringReader(xml)), new CustomParseHandler)

      Right(XmlSuccessfullyValidated(xsd))

    } catch {
      case e: Throwable =>
        logger.warn(e.getMessage)
        Left(FailedToValidateXml(e.getMessage))
    }

}

sealed trait XmlValid

case class XmlSuccessfullyValidated(xsdFile: XSDFile) extends XmlValid

sealed trait XmlError

case class FailedToValidateXml(reason: String) extends XmlError
