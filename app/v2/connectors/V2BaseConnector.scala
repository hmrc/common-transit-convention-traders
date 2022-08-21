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

package v2.connectors

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.lemonlabs.uri.UrlPath
import play.api.http.Status.ACCEPTED
import play.api.http.Status.OK
import play.api.libs.json.JsResult
import play.api.libs.json.Reads
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.RequestBuilder
import v2.models.AuditType
import v2.models.DepartureId
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.request.MessageType

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

trait V2BaseConnector extends HttpErrorFunctions {

  def validationRoute(messageType: MessageType): UrlPath =
    UrlPath.parse(s"/transit-movements-validator/messages/${messageType.code}/validation")

  def movementsBaseRoute: String = "/transit-movements"

  def movementsPostDepartureDeclaration(eoriNumber: EORINumber): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/departures/")

  def movementsGetDepartureMessage(eoriNumber: EORINumber, departureId: DepartureId, messageId: MessageId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/departures/${departureId.value}/messages/${messageId.value}/")

  def movementsGetDepartureMessageIds(eoriNumber: EORINumber, departureId: DepartureId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/departures/${departureId.value}/messages/")

  def movementsGetDeparture(eoriNumber: EORINumber, departureId: DepartureId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/departures/${departureId.value}/")

  def movementsGetAllDepartures(eoriNumber: EORINumber): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/")

  def routerBaseRoute: String = "/transit-movements-router"

  def routerRoute(eoriNumber: EORINumber, messageType: MessageType, movementId: DepartureId, messageId: MessageId): UrlPath =
    UrlPath.parse(s"$routerBaseRoute/traders/${eoriNumber.value}/movements/${messageType.movementType}/${movementId.value}/messages/${messageId.value}/")

  def auditingBaseRoute: String = "/transit-movements-auditing"

  def auditingRoute(auditType: AuditType): UrlPath =
    UrlPath.parse(s"$auditingBaseRoute/audit/${auditType.name}")

  def conversionRoute(messageType: MessageType): UrlPath =
    UrlPath.parse(s"/transit-movements-converter/messages/${messageType.code}")

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

    def executeAccepted(implicit ec: ExecutionContext): Future[Unit] =
      requestBuilder
        .execute[HttpResponse]
        .flatMap {
          response =>
            response.status match {
              case ACCEPTED => Future.successful(())
              case _        => response.error
            }
        }

    def executeAsStream(implicit m: Materializer, ec: ExecutionContext): Future[Source[ByteString, _]] =
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
