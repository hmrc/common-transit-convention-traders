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

package v2.services

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
import play.api.libs.json.Json
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import v2.base.CommonGenerators
import v2.base.TestActorSystem
import v2.base.TestSourceProvider.singleUseStringSource
import v2.models.JsonPayload
import v2.models.XmlPayload
import v2.models.errors.ConversionError
import v2.models.errors.PresentationError
import v2.models.request.MessageType

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ResponseFormatterServiceSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with ScalaCheckDrivenPropertyChecks
    with CommonGenerators
    with TestActorSystem {

  "formatMessageSummary" - {

    val mockConversionService    = mock[ConversionService]
    val responseFormatterService = new ResponseFormatterServiceImpl(mockConversionService)

    implicit val hc = HeaderCarrier()
    implicit val ec = materializer.executionContext

    val xmlString                 = "<test>example</test>"
    val jsonString                = Json.stringify(Json.obj("test" -> "example"))
    val sourceJson                = singleUseStringSource(jsonString)
    val messageSummary            = arbitraryMessageSummaryXml.arbitrary.sample.get.copy(body = Some(XmlPayload(xmlString)))
    val messageSummaryWithoutBody = messageSummary.copy(body = None)

    "when accept header equals application/vnd.hmrc.2.0+json" - {

      "when the body in messageSummary is defined, it returns a new instance of messageSummary with a json body" in {
        when(mockConversionService.xmlToJson(any[MessageType], any[Source[ByteString, _]])(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
          .thenAnswer(
            _ => EitherT.rightT[Future, ConversionError](sourceJson)
          )

        val expected = messageSummary.copy(body = Some(JsonPayload(jsonString)))

        whenReady(
          responseFormatterService.formatMessageSummary(messageSummary, VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON).value
        ) {
          result => result mustBe Right(expected)
        }
      }

      "when the body in messageSummary is empty, it returns the given messageSummary" in {
        whenReady(
          responseFormatterService.formatMessageSummary(messageSummaryWithoutBody, VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON).value
        ) {
          result => result mustEqual Right(messageSummaryWithoutBody)
        }
      }

      "when the conversion service fails, it returns a presentation error" in {
        when(mockConversionService.xmlToJson(any[MessageType], any[Source[ByteString, _]])(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
          .thenAnswer(
            _ => EitherT.leftT[Future, ConversionError](ConversionError.UnexpectedError(None))
          )

        whenReady(
          responseFormatterService.formatMessageSummary(messageSummary, VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON).value
        ) {
          result => result mustEqual Left(PresentationError.internalServiceError())
        }
      }
    }

    Seq(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML, VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN)
      .foreach {
        acceptHeader =>
          s"when accept header equals $acceptHeader" - {

            "when the body in message summary is empty, it returns the given messageSummary" in {
              whenReady(
                responseFormatterService.formatMessageSummary(messageSummaryWithoutBody, acceptHeader).value
              ) {
                result => result mustEqual Right(messageSummaryWithoutBody)
              }
            }

            "when the body in message summary is defined, it returns the given messageSummary" in {
              whenReady(
                responseFormatterService.formatMessageSummary(messageSummary, acceptHeader).value
              ) {
                result => result mustEqual Right(messageSummary)
              }
            }
          }
      }

  }

}
