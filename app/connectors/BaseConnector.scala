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

package connectors

import config.AppConfig
import config.Constants
import models.*
import models.common.EORINumber
import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import models.request.MessageType
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.http.HeaderNames
import play.api.http.Status.OK
import play.api.libs.json.JsResult
import play.api.libs.json.Reads
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.RequestBuilder
import uk.gov.hmrc.http.client.readStreamHttpResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait BaseConnector extends HttpErrorFunctions {

  implicit class HttpResponseHelpers(response: HttpResponse) {

    def as[A](implicit reads: Reads[A]): Future[A] =
      response.json
        .validate[A]
        .map(
          result => Future.successful(result)
        )
        .recoverTotal(
          error => Future.failed(JsResult.Exception(error))
        )

    def errorFromStream[A](implicit m: Materializer, ec: ExecutionContext): Future[A] =
      response.bodyAsSource
        .reduce(_ ++ _)
        .map(_.utf8String)
        .runWith(Sink.head)
        .flatMap(
          result => Future.failed(UpstreamErrorResponse(result, response.status))
        )

    def error[A]: Future[A] =
      Future.failed(UpstreamErrorResponse(response.body, response.status))

  }

  implicit class RequestBuilderHelpers(requestBuilder: RequestBuilder) {

    def withInternalAuthToken(implicit appConfig: AppConfig): RequestBuilder =
      requestBuilder.setHeader(HeaderNames.AUTHORIZATION -> appConfig.internalAuthToken)

    def withClientId(implicit hc: HeaderCarrier): RequestBuilder =
      hc.headers(Seq(Constants.XClientIdHeader)).headOption match {
        case Some(header) => requestBuilder.setHeader(header)
        case None         => requestBuilder
      }

    def withMovementId(movementId: Option[MovementId]): RequestBuilder =
      if (movementId.isDefined)
        requestBuilder.setHeader("X-Audit-Meta-Movement-Id" -> movementId.get.value)
      else requestBuilder

    def withEoriNumber(eoriNumber: Option[EORINumber]): RequestBuilder =
      if (eoriNumber.isDefined)
        requestBuilder.setHeader("X-Audit-Meta-EORI" -> eoriNumber.get.value)
      else requestBuilder

    def withMovementType(movementType: Option[MovementType]): RequestBuilder =
      if (movementType.isDefined)
        requestBuilder.setHeader("X-Audit-Meta-Movement-Type" -> movementType.get.movementType)
      else requestBuilder

    def withMessageType(messageType: Option[MessageType]): RequestBuilder =
      if (messageType.isDefined)
        requestBuilder.setHeader("X-Audit-Meta-Message-Type" -> messageType.get.code)
      else requestBuilder

    def withMessageId(messageId: Option[MessageId]): RequestBuilder =
      if (messageId.isDefined)
        requestBuilder.setHeader("X-Audit-Meta-Message-Id" -> messageId.get.value)
      else requestBuilder

    def executeAndDeserialise[T](implicit ec: ExecutionContext, reads: Reads[T]): Future[T] =
      requestBuilder
        .execute[HttpResponse]
        .flatMap {
          response =>
            response.status match {
              case OK => response.as[T]
              case _  =>
                response.error
            }
        }

    def executeAndExpect(expected: Int)(implicit ec: ExecutionContext): Future[Unit] =
      requestBuilder
        .execute[HttpResponse]
        .flatMap {
          response =>
            response.status match {
              case `expected` => Future.successful(())
              case _          => response.error
            }
        }

    def executeAsStream(implicit m: Materializer, ec: ExecutionContext): Future[Source[ByteString, ?]] =
      requestBuilder
        .stream[HttpResponse]
        .flatMap {
          response =>
            response.status match {
              case OK => Future.successful(response.bodyAsSource)
              case _  => response.errorFromStream
            }
        }
  }

}
