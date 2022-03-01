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

package services

import java.net.URL

import cats.data.NonEmptyList
import javax.xml.parsers.SAXParserFactory
import javax.xml.validation.Schema
import models.SchemaValidationError
import models.request.XSDFile
import play.api.Logger
import cats.syntax.all._

import scala.xml.SAXParseException
import scala.xml.SAXParser

class XmlValidationService {

  private val schemaLang = javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI

  private def saxParser(schema: Schema): SAXParser = {
    val saxParser: SAXParserFactory = javax.xml.parsers.SAXParserFactory.newInstance()
    saxParser.setNamespaceAware(true)
    saxParser.setSchema(schema)
    saxParser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    saxParser.setFeature("http://xml.org/sax/features/external-general-entities", false)
    saxParser.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    saxParser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    saxParser.setXIncludeAware(false)
    saxParser.newSAXParser()
  }

  def validate(xml: String, xsdFile: XSDFile): Either[NonEmptyList[SchemaValidationError], XmlValid] = {
    val url: URL = getClass.getResource(xsdFile.FilePath)

    val schema: Schema = javax.xml.validation.SchemaFactory.newInstance(schemaLang).newSchema(url)

    val parser = saxParser(schema)

    val loader = new ErrorCapturingXmlLoader(parser)

    val parseElem = Either
      .catchOnly[SAXParseException] {
        loader.loadString(xml)
        XmlSuccessfullyValidated
      }
      .leftMap { exc =>
        NonEmptyList.of(SchemaValidationError.fromSaxParseException(exc))
      }

    NonEmptyList
      .fromList(loader.errors)
      .map(Either.left)
      .getOrElse(parseElem)
  }
}

sealed trait XmlValid

object XmlSuccessfullyValidated extends XmlValid

sealed trait XmlError {
  val reason: String
}

object XmlError {

  val FailedSchemaValidationMessage =
    "The request has failed schema validation. Please review the required message structure as specified by the XSD file '%s'. Detailed error below:\n%s"

  val RequestBodyEmptyMessage = "The request cannot be processed as it does not contain a request body."

  val RequestBodyInvalidTypeMessage = "The request cannot be processed as it does not contain an XML request body."
}