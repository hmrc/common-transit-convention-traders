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

  val commmonTransitConventionTradersUrl: Url = Url.parse(servicesConfig.baseUrl("common-transit-convention-traders"))
  val validatorUrl: Url                       = Url.parse(servicesConfig.baseUrl("transit-movements-validator"))
  val converterUrl: Url                       = Url.parse(servicesConfig.baseUrl("transit-movements-converter"))
  val movementsUrl: Url                       = Url.parse(servicesConfig.baseUrl("transit-movements"))
  val routerUrl: Url                          = Url.parse(servicesConfig.baseUrl("transit-movements-router"))
  val auditingUrl: Url                        = Url.parse(servicesConfig.baseUrl("transit-movements-auditing"))
  val pushNotificationsUrl: Url               = Url.parse(servicesConfig.baseUrl("transit-movements-push-notifications"))
  val upscanInitiateUrl: Url                  = Url.parse(servicesConfig.baseUrl("upscan-initiate"))
  val upscanMaximumFileSize: Long             = servicesConfig.config("upscan-initiate").get[Long]("maximumFileSize")
  val forwardClientIdToUpscan: Boolean        = servicesConfig.config("upscan-initiate").get[Boolean]("send-client-id")

  val version21BetaEnabled: Boolean = config.get[Boolean]("version21BetaEnabled")

  val smallMessageSizeLimit: Int = config.get[Int]("smallMessageSizeLimit")

  val defaultItemsPerPage: Int = config.get[Int]("defaultItemsPerPage")

  val maxItemsPerPage: Int = config.get[Int]("maxItemsPerPage")

  val internalAuthToken: String = config.get[String]("internal-auth.token")

  val phase5TransitionalEnabled: Boolean = config.get[Boolean]("enable-phase-5")

  val phase5FinalEnabled: Boolean = config.get[Boolean]("phase-5-final-enabled")

}
