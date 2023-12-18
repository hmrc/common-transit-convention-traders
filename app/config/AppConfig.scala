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

package config

import io.lemonlabs.uri.Url
import play.api.Configuration

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppConfig @Inject() (config: Configuration, servicesConfig: CTCServicesConfig) {

  val authBaseUrl: String = servicesConfig.baseUrl("auth")

  val auditingEnabled: Boolean = config.get[Boolean]("auditing.enabled")
  val graphiteHost: String     = config.get[String]("microservice.metrics.graphite.host")

  val commmonTransitConventionTradersUrl = Url.parse(servicesConfig.baseUrl("common-transit-convention-traders"))
  val traderAtDestinationUrl             = Url.parse(servicesConfig.baseUrl("transit-movement-trader-at-destination"))
  val traderAtDeparturesUrl              = Url.parse(servicesConfig.baseUrl("transits-movements-trader-at-departure"))
  val validatorUrl                       = Url.parse(servicesConfig.baseUrl("transit-movements-validator"))
  val converterUrl                       = Url.parse(servicesConfig.baseUrl("transit-movements-converter"))
  val movementsUrl                       = Url.parse(servicesConfig.baseUrl("transit-movements"))
  val routerUrl                          = Url.parse(servicesConfig.baseUrl("transit-movements-router"))
  val auditingUrl                        = Url.parse(servicesConfig.baseUrl("transit-movements-auditing"))
  val pushNotificationsUrl               = Url.parse(servicesConfig.baseUrl("transit-movements-push-notifications"))
  val upscanInitiateUrl                  = Url.parse(servicesConfig.baseUrl("upscan-initiate"))
  val upscanMaximumFileSize              = servicesConfig.config("upscan-initiate").get[Long]("maximumFileSize")
  val forwardClientIdToUpscan            = servicesConfig.config("upscan-initiate").get[Boolean]("send-client-id")
  val pushNotificationsEnabled           = servicesConfig.config("transit-movements-push-notifications").get[Boolean]("enabled")

  lazy val enrolmentKey: String = config.get[String]("security.enrolmentKey")

  val messageTranslationFile: String = config.get[String]("message-translation-file")

  val pushPullUrl = Url.parse(servicesConfig.baseUrl("push-pull-notifications-api"))

  val blockUnknownNamespaces: Boolean = config.get[Boolean]("xml-validation.block-unknown-namespaces")

  val smallMessageSizeLimit: Int = config.get[Int]("smallMessageSizeLimit")

  val logInsufficientEnrolments: Boolean = config.get[Boolean]("logInsufficientEnrolments")

  val defaultItemsPerPage: Int = config.get[Int]("defaultItemsPerPage")

  val maxItemsPerPage: Int = config.get[Int]("maxItemsPerPage")

  val internalAuthToken: String = config.get[String]("internal-auth.token")

  val disablePhase4: Boolean = config.get[Boolean]("disable-phase-4")

  val enablePhase5: Boolean = config.get[Boolean]("enable-phase-5")

  val phase4EnrolmentHeader: Boolean = config.get[Boolean]("phase-4-enrolment-header")

}
