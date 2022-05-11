package v2.connectors

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import com.kenshoo.play.metrics.Metrics
import config.AppConfig
import metrics.HasMetrics
import metrics.MetricsKeys
import play.api.Logging
import play.api.http.Status.BAD_REQUEST
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.client.HttpClientV2
import v2.models.errors.BaseError
import v2.models.errors.InternalServiceError
import v2.models.responses.ValidationResponse

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[ValidationConnectorImpl])
trait ValidationConnector {

  def validate(messageType: String, xmlStream: Source[ByteString, _])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse]

}

@Singleton
class ValidationConnectorImpl @Inject() (httpClient: HttpClientV2, appConfig: AppConfig, val metrics: Metrics)
  extends ValidationConnector
    with HasMetrics
    with V2BaseConnector
    with Logging {

  override def validate(messageType: String, xmlStream: Source[ByteString, _])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    withMetricsTimerAsync(MetricsKeys.ValidatorBackend.Post) {
      metricsTimer =>
        val url = appConfig.validatorUrl.withPath(validationRoute(messageType))
        httpClient.post(url.asJavaURL).withBody(xmlStream).execute
    }
  }
}