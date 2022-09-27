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

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import io.lemonlabs.uri.Url
import metrics.HasMetrics
import metrics.MetricsKeys
import play.api.Logging
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.OK
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.http.client.HttpClientV2
import v2.models.DepartureId
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.responses.DeclarationResponse
import v2.models.responses.DepartureResponse
import v2.models.responses.MessageResponse
import v2.models.responses.MessageSummary

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@ImplementedBy(classOf[PersistenceConnectorImpl])
trait PersistenceConnector {

  def post(eori: EORINumber, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[DeclarationResponse]

  def getDepartureMessage(eori: EORINumber, departureId: DepartureId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MessageSummary]

  def getDepartureMessageIds(eori: EORINumber, departureId: DepartureId, receivedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[MessageSummary]]

  def getDeparture(eori: EORINumber, departureId: DepartureId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[DepartureResponse]

  def getDeparturesForEori(eori: EORINumber)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[DepartureResponse]]

}

@Singleton
class PersistenceConnectorImpl @Inject() (httpClientV2: HttpClientV2, appConfig: AppConfig, val metrics: Metrics)
    extends PersistenceConnector
    with HasMetrics
    with V2BaseConnector
    with Logging {

  override def post(eori: EORINumber, source: Source[ByteString, _])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[DeclarationResponse] =
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      _ =>
        val url = appConfig.movementsUrl.withPath(movementsPostDepartureDeclaration(eori))

        httpClientV2
          .post(url"$url")
          .addHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
          .withBody(source)
          .execute[HttpResponse]
          .flatMap {
            response =>
              response.status match {
                case OK => response.as[DeclarationResponse]
                case _  => response.error
              }
          }
    }

  override def getDepartureMessage(eori: EORINumber, departureId: DepartureId, messageId: MessageId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[MessageSummary] = {
    val url = appConfig.movementsUrl.withPath(movementsGetDepartureMessage(eori, departureId, messageId))
    println(s"TRANSITMOVEMENTS:$url")
    val res = httpClientV2
      .get(url"$url")
      .addHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
      .execute[HttpResponse]
    scala.concurrent.Await.ready(res, scala.concurrent.duration.Duration.Inf)
    println(s"ZZZZZZZ:$res")
    res.flatMap {
      response =>
        response.status match {
          case OK => println(response); response.as[MessageSummary]
          case _  => response.error
        }
    }
  }

  override def getDepartureMessageIds(eori: EORINumber, departureId: DepartureId, receivedSince: Option[OffsetDateTime])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[MessageSummary]] = {
    val url =
      withReceivedSinceParameter(
        appConfig.movementsUrl.withPath(movementsGetDepartureMessageIds(eori, departureId)).toUrl,
        receivedSince
      )

    httpClientV2
      .get(url"$url")
      .addHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
      .execute[HttpResponse]
      .flatMap {
        response =>
          response.status match {
            case OK => response.as[Seq[MessageSummary]]
            case _  => response.error
          }
      }
  }

  override def getDeparture(eori: EORINumber, departureId: DepartureId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[DepartureResponse] = {
    val url = appConfig.movementsUrl.withPath(movementsGetDeparture(eori, departureId))

    httpClientV2
      .get(url"$url")
      .addHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
      .execute[HttpResponse]
      .flatMap {
        response =>
          response.status match {
            case OK => response.as[DepartureResponse]
            case _  => response.error
          }
      }
  }

  override def getDeparturesForEori(eori: EORINumber)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Seq[DepartureResponse]] = {
    val url = appConfig.movementsUrl.withPath(movementsGetAllDepartures(eori))

    httpClientV2
      .get(url"$url")
      .addHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
      .execute[HttpResponse]
      .flatMap {
        response =>
          response.status match {
            case OK => response.as[Seq[DepartureResponse]]
            case _  => response.error
          }
      }
  }

  private def withReceivedSinceParameter(urlPath: Url, dateTime: Option[OffsetDateTime]) =
    dateTime
      .map(
        time => urlPath.addParam("receivedSince", DateTimeFormatter.ISO_DATE_TIME.format(time))
      )
      .getOrElse(urlPath)

}
