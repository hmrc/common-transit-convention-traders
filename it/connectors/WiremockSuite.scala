package connectors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.kenshoo.play.metrics.Metrics
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import play.api.Application
import play.api.inject.{bind, Injector}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceableModule}
import utils.TestMetrics

trait WiremockSuite extends BeforeAndAfterAll with BeforeAndAfterEach {
  this: Suite =>
  protected val server: WireMockServer = new WireMockServer(wireMockConfig().dynamicPort())

  protected def portConfigKey: String

  protected lazy val app: Application =
    new GuiceApplicationBuilder()
      .configure(
        portConfigKey -> server.port().toString
      )
      .overrides(bindings: _*)
      .build()

  protected lazy val injector: Injector = app.injector

  protected def bindings: Seq[GuiceableModule] = Seq(
    bind[Metrics].toInstance(new TestMetrics)
  )

  override def beforeAll(): Unit = {
    server.start()
    app
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    server.resetAll()
    app
    super.beforeEach()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }
}