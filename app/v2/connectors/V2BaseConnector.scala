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

package v2.connectors

import config.AppConfig
import config.Constants
import io.lemonlabs.uri.UrlPath
import models.common.EORINumber
import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.http.HeaderNames
import play.api.http.Status.OK
import play.api.libs.json.JsResult
import play.api.libs.json.Reads
import uk.gov.hmrc.http.client.RequestBuilder
import uk.gov.hmrc.http.client.readStreamHttpResponse
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.models.AuditType
import v2.models.request.MessageType

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait V2BaseConnector extends HttpErrorFunctions {

  def validationRoute(messageType: MessageType): UrlPath =
    UrlPath.parse(s"/transit-movements-validator/messages/${messageType.code}/validation")

  val movementsBaseRoute: String = "/transit-movements"

  def postMovementUrl(eoriNumber: EORINumber, movementType: MovementType): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}")

  def postMessageUrl(movementId: MovementId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/movements/${movementId.value}/messages")

  // GET and POST
  def messageBodyUrl(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}/body")

  def getMessageUrl(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}")

  def getMessagesUrl(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}/messages")

  def getMovementUrl(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}")

  def getAllMovementsUrl(eoriNumber: EORINumber, movementType: MovementType): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}")

  val routerBaseRoute: String = "/transit-movements-router"

  def routerRoute(eoriNumber: EORINumber, messageType: MessageType, movementId: MovementId, messageId: MessageId): UrlPath =
    UrlPath.parse(
      s"$routerBaseRoute/traders/${eoriNumber.value}/movements/${messageType.movementType.urlFragment}/${movementId.value}/messages/${messageId.value}"
    )

  val auditingBaseRoute: String = "/transit-movements-auditing"

  def auditingRoute(auditType: AuditType): UrlPath = UrlPath.parse(s"$auditingBaseRoute/audit/${auditType.name}")

  val conversionBaseRoute: String = "/transit-movements-converter"

  def conversionRoute(messageType: MessageType): UrlPath =
    UrlPath.parse(s"$conversionBaseRoute/messages/${messageType.code}")

  val pushNotificationsBaseRoute: String = "/transit-movements-push-notifications"

  def pushNotificationsRoute(movementId: MovementId): UrlPath =
    UrlPath.parse(s"$pushNotificationsBaseRoute/traders/movements/${movementId.value}")

  def pushNotificationsBoxRoute(movementId: MovementId): UrlPath =
    UrlPath.parse(s"$pushNotificationsBaseRoute/traders/movements/${movementId.value}/box")

  def pushPpnsNotifications(movementId: MovementId, messageId: MessageId): UrlPath =
    UrlPath.parse(s"$pushNotificationsBaseRoute/traders/movements/${movementId.value}/messages/${messageId.value}/submissionNotification")

  lazy val upscanInitiateRoute: UrlPath = UrlPath.parse("/upscan/v2/initiate")

  def attachLargeMessageRoute(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId): UrlPath =
    UrlPath.parse(s"/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}")

  def updateMessageRoute(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}")

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
              case _ =>
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
