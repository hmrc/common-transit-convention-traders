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
}
