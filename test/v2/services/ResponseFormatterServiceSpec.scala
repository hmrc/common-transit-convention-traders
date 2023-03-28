/*
 * Copyright 2023 HM Revenue & Customs
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
import v2.base.TestCommonGenerators
import v2.base.TestActorSystem
import v2.base.TestSourceProvider.singleUseStringSource
import v2.models.JsonPayload
import v2.models.ObjectStoreResourceLocation
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
    with TestCommonGenerators
    with TestActorSystem {

  "formatMessageSummary" - {

    val mockConversionService    = mock[ConversionService]
    val mockObjectStoreService   = mock[ObjectStoreService]
    val responseFormatterService = new ResponseFormatterServiceImpl(mockConversionService, mockObjectStoreService)

    implicit val hc = HeaderCarrier()
    implicit val ec = materializer.executionContext

    val xmlString                       = "<test>example</test>"
    val jsonString                      = Json.stringify(Json.obj("test" -> "example"))
    val messageSummaryWithBody          = arbitraryMessageSummaryXml.arbitrary.sample.get.copy(body = Some(XmlPayload(xmlString)))
    val messageSummaryWithUri           = messageSummaryWithBody.copy(body = None)
    val messageSummaryWithoutBodyAndUri = messageSummaryWithBody.copy(body = None, uri = None)

    "when accept header equals application/vnd.hmrc.2.0+json" - {

      "when the body in messageSummary is defined, it returns a new instance of messageSummary with a json body" in {
        when(mockConversionService.xmlToJson(any[MessageType], any[Source[ByteString, _]])(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
          .thenAnswer(
            _ => EitherT.rightT[Future, ConversionError](singleUseStringSource(jsonString))
          )

        val expected = messageSummaryWithBody.copy(body = Some(JsonPayload(jsonString)))

        whenReady(
          responseFormatterService.formatMessageSummary(messageSummaryWithBody, VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON).value
        ) {
          result => result mustBe Right(expected)
        }
      }

      "when the uri in messageSummary is defined, it returns a new instance of messageSummary with a json body" in {
        when(
          mockObjectStoreService
            .getMessage(any[ObjectStoreResourceLocation])(any[ExecutionContext], any[HeaderCarrier])
        )
          .thenAnswer(
            _ => EitherT.rightT[Future, ConversionError](singleUseStringSource(xmlString))
          )

        when(mockConversionService.xmlToJson(any[MessageType], any[Source[ByteString, _]])(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
          .thenAnswer(
            _ => EitherT.rightT[Future, ConversionError](singleUseStringSource(jsonString))
          )

        val expected = messageSummaryWithBody.copy(body = Some(JsonPayload(jsonString)))

        whenReady(
          responseFormatterService.formatMessageSummary(messageSummaryWithUri, VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON).value
        ) {
          result => result mustBe Right(expected)
        }
      }

      "when the body and uri in messageSummary are empty, it returns the given messageSummary" in {
        whenReady(
          responseFormatterService.formatMessageSummary(messageSummaryWithoutBodyAndUri, VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON).value
        ) {
          result => result mustEqual Right(messageSummaryWithoutBodyAndUri)
        }
      }

      "when the conversion service fails for message summary with body, it returns a presentation error" in {
        when(mockConversionService.xmlToJson(any[MessageType], any[Source[ByteString, _]])(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
          .thenAnswer(
            _ => EitherT.leftT[Future, ConversionError](ConversionError.UnexpectedError(None))
          )

        whenReady(
          responseFormatterService.formatMessageSummary(messageSummaryWithBody, VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON).value
        ) {
          result => result mustEqual Left(PresentationError.internalServiceError())
        }
      }
    }

    "when the conversion service fails for message summary with uri, it returns a presentation error" in {
      when(
        mockObjectStoreService
          .getMessage(any[ObjectStoreResourceLocation])(any[ExecutionContext], any[HeaderCarrier])
      )
        .thenAnswer(
          _ => EitherT.rightT[Future, ConversionError](singleUseStringSource(xmlString))
        )

      when(mockConversionService.xmlToJson(any[MessageType], any[Source[ByteString, _]])(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
        .thenAnswer(
          _ => EitherT.leftT[Future, ConversionError](ConversionError.UnexpectedError(None))
        )

      whenReady(
        responseFormatterService.formatMessageSummary(messageSummaryWithUri, VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON).value
      ) {
        result => result mustEqual Left(PresentationError.internalServiceError())
      }
    }

    Seq(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML, VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN)
      .foreach {
        acceptHeader =>
          s"when accept header equals $acceptHeader" - {

            "when the body and uri in message summary are empty, it returns the given messageSummary" in {
              whenReady(
                responseFormatterService.formatMessageSummary(messageSummaryWithoutBodyAndUri, acceptHeader).value
              ) {
                result => result mustEqual Right(messageSummaryWithoutBodyAndUri)
              }
            }

            "when the body in message summary is defined, it returns the given messageSummary" in {
              whenReady(
                responseFormatterService.formatMessageSummary(messageSummaryWithBody, acceptHeader).value
              ) {
                result => result mustEqual Right(messageSummaryWithBody)
              }
            }
          }
      }

  }

}
