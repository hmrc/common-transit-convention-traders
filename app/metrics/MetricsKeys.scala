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
    val Post                  = "arrival-backend-post"
    val Put                   = "arrival-backend-put"
    val GetById               = "arrival-backend-get-by-id"
    val GetForEori            = "arrival-backend-get-for-eori"
    val PostMessage           = "arrival-backend-post-message"
    val GetMessageById        = "arrival-backend-get-message-by-id"
    val GetMessagesForArrival = "arrival-backend-get-messages-for-arrival"
  }

  object DeparturesBackend {
    val Post                    = "departures-backend-post"
    val GetById                 = "departures-backend-get-by-id"
    val GetForEori              = "departures-backend-get-for-eori"
    val PostMessage             = "departures-backend-post-message"
    val GetMessageById          = "departures-backend-get-message-by-id"
    val GetMessagesForDeparture = "departures-backend-get-messages-for-departure"
  }

  object ValidatorBackend {
    val Post = "validator-backend-post"
  }

  object RouterBackend {
    val Post = "router-backend-post"
  }

  object AuditingBackend {
    val Post = "auditing-backend-post"
  }

  object PushNotificationsBacked {
    val Post   = "push-notifications-backend-post"
    val Update = "push-notifications-backend-update"
  }

  object UpscanInitiateBacked {
    val Post = "upscan-initiate-backend-post"
  }

  object Endpoints {
    def count(metricKey: String) = s"$metricKey-count"
    // Arrivals
    val GetArrival                  = "get-arrival"
    val GetArrivalsForEori          = "get-arrivals-for-eori"
    val GetArrivalsForEoriCount     = count(GetArrivalsForEori)
    val GetArrivalMessage           = "get-arrival-message"
    val GetArrivalMessages          = "get-arrival-messages"
    val GetArrivalMessagesCount     = count(GetArrivalMessages)
    val SendArrivalMessage          = "send-arrival-message"
    val CreateArrivalNotification   = "create-arrival-notification"
    val ResubmitArrivalNotification = "resubmit-arrival-notification"
    // Departures
    val GetDeparture               = "get-departure"
    val GetDeparturesForEori       = "get-departures-for-eori"
    val GetDeparturesForEoriCount  = count(GetDeparturesForEori)
    val GetDepartureMessage        = "get-departure-message"
    val GetDepartureMessages       = "get-departure-messages"
    val GetDepartureMessagesCount  = count(GetDepartureMessages)
    val SendDepartureMessage       = "send-departure-message"
    val SubmitDepartureDeclaration = "submit-departure-declaration"
  }

  object Messages {
    val MessageSize             = "message-size"
    val NumberOfGoods           = "number-of-goods"
    val NumberOfDocuments       = "number-of-documents"
    val NumberOfSpecialMentions = "number-of-special-mentions"
    val NumberOfSeals           = "number-of-seals"
  }

  object Guarantee {
    val EnsureGuarantee          = "ensure-guarantee"
    val EnsureGuaranteeParseXml  = "ensure-guarantee-parse-xml"
    val EnsureGuaranteeUpdateXml = "ensure-guarantee-update-xml"
  }
}
