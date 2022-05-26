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

package config

import io.lemonlabs.uri.Url
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppConfig @Inject() (config: Configuration, servicesConfig: ServicesConfig) {

  val authBaseUrl: String = servicesConfig.baseUrl("auth")

  val auditingEnabled: Boolean = config.get[Boolean]("auditing.enabled")
  val graphiteHost: String     = config.get[String]("microservice.metrics.graphite.host")

  val traderAtDestinationUrl = Url.parse(servicesConfig.baseUrl("transit-movement-trader-at-destination"))
  val traderAtDeparturesUrl  = Url.parse(servicesConfig.baseUrl("transits-movements-trader-at-departure"))
  val validatorUrl           = Url.parse(servicesConfig.baseUrl("transit-movements-validator"))
  val movementsUrl           = Url.parse(servicesConfig.baseUrl("transit-movements"))

  lazy val enrolmentKey: String = config.get[String]("security.enrolmentKey")

  val messageTranslationFile: String = config.get[String]("message-translation-file")

  val pushPullUrl = Url.parse(servicesConfig.baseUrl("push-pull-notifications-api"))

  val blockUnknownNamespaces: Boolean = config.get[Boolean]("xml-validation.block-unknown-namespaces")

  val messageSizeLimit: Int = config.get[Int]("messageSizeLimit")

}
