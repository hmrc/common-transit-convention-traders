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

package metrics

object MetricsKeys {

  object ArrivalBackend {
    val Post: String                  = "arrival-backend-post"
    val Put: String                   = "arrival-backend-put"
    val GetById: String               = "arrival-backend-get-by-id"
    val GetForEori: String            = "arrival-backend-get-for-eori"
    val PostMessage: String           = "arrival-backend-post-message"
    val GetMessageById: String        = "arrival-backend-get-message-by-id"
    val GetMessagesForArrival: String = "arrival-backend-get-messages-for-arrival"
  }

  object DeparturesBackend {
    val Post: String                    = "departures-backend-post"
    val GetById: String                 = "departures-backend-get-by-id"
    val GetForEori: String              = "departures-backend-get-for-eori"
    val PostMessage: String             = "departures-backend-post-message"
    val GetMessageById: String          = "departures-backend-get-message-by-id"
    val GetMessagesForDeparture: String = "departures-backend-get-messages-for-departure"
  }

  object ValidatorBackend {
    val Post: String = "validator-backend-post"
  }

  object RouterBackend {
    val Post: String = "router-backend-post"
  }

  object AuditingBackend {
    val Post: String = "auditing-backend-post"
  }

  object PushNotificationsBackend {
    val Post: String   = "push-notifications-backend-post"
    val Update: String = "push-notifications-backend-update"
  }

  object UpscanInitiateBackend {
    val Post: String = "upscan-initiate-backend-post"
  }

  object Endpoints {
    def count(metricKey: String) = s"$metricKey-count"
    // Arrivals
    val GetArrival: String                  = "get-arrival"
    val GetArrivalsForEori: String          = "get-arrivals-for-eori"
    val GetArrivalsForEoriCount: String     = count(GetArrivalsForEori)
    val GetArrivalMessage: String           = "get-arrival-message"
    val GetArrivalMessages: String          = "get-arrival-messages"
    val GetArrivalMessagesCount: String     = count(GetArrivalMessages)
    val SendArrivalMessage: String          = "send-arrival-message"
    val CreateArrivalNotification: String   = "create-arrival-notification"
    val ResubmitArrivalNotification: String = "resubmit-arrival-notification"
    // Departures
    val GetDeparture: String               = "get-departure"
    val GetDeparturesForEori: String       = "get-departures-for-eori"
    val GetDeparturesForEoriCount: String  = count(GetDeparturesForEori)
    val GetDepartureMessage: String        = "get-departure-message"
    val GetDepartureMessages: String       = "get-departure-messages"
    val GetDepartureMessagesCount: String  = count(GetDepartureMessages)
    val SendDepartureMessage: String       = "send-departure-message"
    val SubmitDepartureDeclaration: String = "submit-departure-declaration"
  }

  object Messages {
    val MessageSize: String             = "message-size"
    val NumberOfGoods: String           = "number-of-goods"
    val NumberOfDocuments: String       = "number-of-documents"
    val NumberOfSpecialMentions: String = "number-of-special-mentions"
    val NumberOfSeals: String           = "number-of-seals"
  }

  object Guarantee {
    val EnsureGuarantee: String          = "ensure-guarantee"
    val EnsureGuaranteeParseXml: String  = "ensure-guarantee-parse-xml"
    val EnsureGuaranteeUpdateXml: String = "ensure-guarantee-update-xml"
  }
}
