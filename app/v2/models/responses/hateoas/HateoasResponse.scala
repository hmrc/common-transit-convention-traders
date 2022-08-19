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

package v2.models.responses.hateoas

import config.Constants
import v2.models.DepartureId
import v2.models.MessageId
import v2.models.responses.hateoas.HateoasResponse.prefix

import java.time.OffsetDateTime

object HateoasResponse {

  lazy val prefix =
    if (routing.routes.DeparturesRouter.submitDeclaration().url.startsWith(Constants.Context)) ""
    else Constants.Context

}

trait HateoasResponse {

  def messageUri(departureId: DepartureId, messageId: MessageId) =
    prefix + routing.routes.DeparturesRouter.getMessage(departureId.value, messageId.value).url

  def messageIdsUri(departureId: DepartureId, receivedSince: Option[OffsetDateTime]) =
    prefix + routing.routes.DeparturesRouter
      .getMessageIds(
        departureId.value,
        receivedSince
      )
      .url

  def departureUri(departureId: DepartureId) =
    prefix + routing.routes.DeparturesRouter.getDeparture(departureId.value).url

}
