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

import models.SchemaValidationError
import org.xml.sax.SAXParseException

import javax.xml.parsers.SAXParser
import scala.collection.mutable
import scala.xml.Elem
import scala.xml.factory.XMLLoader
import scala.xml.parsing.NoBindingFactoryAdapter

class ErrorCapturingXmlLoader(
                               messageParser: SAXParser
                             ) extends XMLLoader[Elem] {

  private val errorBuffer: mutable.ListBuffer[SchemaValidationError] =
    new mutable.ListBuffer[SchemaValidationError]

  def errors: List[SchemaValidationError] =
    errorBuffer.toList

  override val parser: SAXParser =
    messageParser

  override def adapter = new NoBindingFactoryAdapter {
    override def warning(error: SAXParseException): Unit = ()

    override def error(error: SAXParseException): Unit =
      errorBuffer += SchemaValidationError.fromSaxParseException(error)
    override def fatalError(error: SAXParseException): Unit =
      errorBuffer += SchemaValidationError.fromSaxParseException(error)
  }
}