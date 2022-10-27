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

package v2.controllers

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import cats.data.EitherT
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.http.HeaderCarrier
import v2.base.CommonGenerators
import v2.base.TestActorSystem
import v2.base.TestSourceProvider
import org.mockito.ArgumentMatchers.{eq => eqTo}
import v2.models.errors.ConversionError
import v2.models.errors.PresentationError
import v2.services.ConversionService

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class MessageFormatSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with CommonGenerators
    with ScalaCheckDrivenPropertyChecks
    with ScalaFutures
    with OptionValues
    with TestActorSystem
    with TestSourceProvider {

  "XmlMessage" - {

    "convertToJson" - {
      val messageType = arbitraryMessageType.arbitrary.sample.get

      implicit val hc = HeaderCarrier()
      implicit val ec = materializer.executionContext

      val mockConversionService = mock[ConversionService]

      "returns body in json if conversion from XML to JSON is successful" in {
        val xmlBody    = s"<${messageType.rootNode}>TEST</${messageType.rootNode}>"
        val jsonBody   = s"""{"${messageType.rootNode}":"TEST"">""""
        val jsonSource = singleUseStringSource(jsonBody)

        when(mockConversionService.xmlToJson(eqTo(messageType), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
          .thenAnswer(
            _ => EitherT.rightT[Future, ConversionError](jsonSource)
          )

        whenReady(XmlMessage.convertToJson(messageType, xmlBody, mockConversionService).value) {
          result =>
            result mustBe Right(jsonBody)
        }
      }

      "returns PresentationError if the conversion from XML to JSON fails" in {
        val invalidXmlBody = "<invalid/>"
        val exception      = Some(new Exception("Unexpected conversion error"))

        when(mockConversionService.xmlToJson(eqTo(messageType), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
          .thenAnswer(
            _ => EitherT.leftT[Future, Source[ByteString, _]](ConversionError.UnexpectedError(exception))
          )

        whenReady(XmlMessage.convertToJson(messageType, invalidXmlBody, mockConversionService).value) {
          result =>
            result mustBe Left(PresentationError.internalServiceError(cause = exception))
        }
      }
    }
  }

}
