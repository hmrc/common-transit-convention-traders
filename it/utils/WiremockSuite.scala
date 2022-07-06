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

package utils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.kenshoo.play.metrics.Metrics
import org.scalatest.BeforeAndAfterAll
import org.scalatest.BeforeAndAfterEach
import org.scalatest.Suite
import org.scalatestplus.play.guice.GuiceFakeApplicationFactory
import play.api.Application
import play.api.inject.Injector
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.guice.GuiceableModule
import com.github.tomakehurst.wiremock.common.ConsoleNotifier

trait WiremockSuite extends BeforeAndAfterAll with BeforeAndAfterEach {
  this: Suite =>

  protected val wiremockConfig = wireMockConfig().dynamicPort().notifier(new ConsoleNotifier(false))

  protected val server: WireMockServer = new WireMockServer(wiremockConfig)

  override def beforeAll(): Unit = {
    server.start()
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    server.resetAll()
    super.beforeEach()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }

}

trait GuiceWiremockSuite extends WiremockSuite with GuiceFakeApplicationFactory {
  this: Suite =>

  protected def portConfigKey: Seq[String]

  lazy val applicationBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        portConfigKey.map {
          key =>
            key -> server.port.toString()
        }: _*
      )
      .overrides(bindings: _*)

  override lazy val fakeApplication: Application =
    applicationBuilder.build()

  protected lazy val injector: Injector = fakeApplication.injector

  protected def bindings: Seq[GuiceableModule] = Seq(
    bind[Metrics].toInstance(new TestMetrics)
  )

  override def beforeAll(): Unit = {
    server.start()
    fakeApplication
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    server.resetAll()
    fakeApplication
    super.beforeEach()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    server.stop()
  }
}
