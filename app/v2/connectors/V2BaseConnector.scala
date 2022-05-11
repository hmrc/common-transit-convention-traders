package v2.connectors

import io.lemonlabs.uri.Url
import io.lemonlabs.uri.UrlPath
import uk.gov.hmrc.http.HttpErrorFunctions

import java.net.URL

object V2BaseConnector {
  implicit class UrlHelper(val value: Url) extends AnyVal {
    def asJavaURL: URL = value.toJavaURI.toURL
  }
}
trait V2BaseConnector extends HttpErrorFunctions {

  protected def validationRoute(messageType: String): UrlPath =
    UrlPath.parse(s"/transit-movements-validator/message/$messageType/validate")

}
