/*
 * Copyright 2021 HM Revenue & Customs
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
    val Post = "arrival-backend-post"
    val Put = "arrival-backend-put"
    val GetById = "arrival-backend-get-by-id"
    val GetForEori = "arrival-backend-get-for-eori"
    val PostMessage = "arrival-backend-post-message"
    val GetMessageById = "arrival-backend-get-message-by-id"
    val GetMessagesForArrival = "arrival-backend-get-messages-for-arrival"
  }

  object DeparturesBackend {
    val Post = "departures-backend-post"
    val GetById = "departures-backend-get-by-id"
    val GetForEori = "departures-backend-get-for-eori"
    val PostMessage = "departures-backend-post-message"
    val GetMessageById = "departures-backend-get-message-by-id"
    val GetMessagesForDeparture = "departures-backend-get-messages-for-departure"
  }

  object Endpoints {
    def count(metricKey: String) = s"$metricKey-count"
    // Arrivals
    val GetArrival = "get-arrival"
    val GetArrivalsForEori = "get-arrivals-for-eori"
    val GetArrivalsForEoriCount = count(GetArrivalsForEori)
    val GetArrivalMessage ="get-arrival-message"
    val GetArrivalMessages = "get-arrival-messages"
    val GetArrivalMessagesCount = count(GetArrivalMessages)
    val SendArrivalMessage = "send-arrival-message"
    val CreateArrivalNotification = "create-arrival-notification"
    val ResubmitArrivalNotification = "resubmit-arrival-notification"
    // Departures
    val GetDeparture = "get-departure"
    val GetDeparturesForEori = "get-departures-for-eori"
    val GetDeparturesForEoriCount = count(GetDeparturesForEori)
    val GetDepartureMessage = "get-departure-message"
    val GetDepartureMessages = "get-departure-messages"
    val GetDepartureMessagesCount = count(GetDepartureMessages)
    val SendDepartureMessage = "send-departure-message"
    val SubmitDepartureDeclaration = "submit-departure-declaration"
  }
}