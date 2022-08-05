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

import io.lemonlabs.uri.UrlPath
import uk.gov.hmrc.http.HttpErrorFunctions
import v2.models.AuditType
import v2.models.EORINumber
import v2.models.DepartureId
import v2.models.MessageId
import v2.models.request.MessageType

trait V2BaseConnector extends HttpErrorFunctions {

  protected def validationRoute(messageType: MessageType): UrlPath =
    UrlPath.parse(s"/transit-movements-validator/messages/${messageType.code}/validation")

  protected def movementsBaseRoute: String = "/transit-movements"

  protected def movementsPostDeperatureDeclaration(eoriNumber: EORINumber): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/departures/")

  protected def movementsGetDepartureMessage(eoriNumber: EORINumber, departureId: DepartureId, messageId: MessageId): UrlPath =
    UrlPath.parse(s"$movementsBaseRoute/traders/${eoriNumber.value}/movements/departures/${departureId.value}/messages/${messageId.value}/")

  protected def routerBaseRoute: String = "/transit-movements-router"

  protected def routerRoute(eoriNumber: EORINumber, messageType: MessageType, movementId: DepartureId, messageId: MessageId): UrlPath =
    UrlPath.parse(s"$routerBaseRoute/traders/${eoriNumber.value}/movements/${messageType.movementType}/${movementId.value}/messages/${messageId.value}/")

  protected def auditingBaseRoute: String = "/transit-movements-auditing"

  protected def auditingRoute(auditType: AuditType): UrlPath =
    UrlPath.parse(s"$auditingBaseRoute/audit/${auditType.name}")

  protected def conversionRoute(messageType: MessageType): UrlPath =
    UrlPath.parse(s"/transit-movements-converter/messages/${messageType.code}")

}
