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

package connectors

import config.Constants
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

class BaseConnectorSpec extends AnyFreeSpec with Matchers {

  class Harness extends BaseConnector {

    def enforceAuth(implicit headerCarrier: HeaderCarrier): Seq[(String, String)] =
      super.enforceAuthHeader

    def jsonHeaders(implicit headerCarrier: HeaderCarrier): Seq[(String, String)] =
      super.getJsonHeaders

    def xmlHeaders(implicit headerCarrier: HeaderCarrier): Seq[(String, String)] =
      super.postPutXmlHeaders
  }

  "BaseConnector" - {

    "enforceAuthHeader" - {

      "must not add auth header if auth header already supplied in the request" in {
        val harness = new Harness()

        implicit val rh = FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> "a5sesqerTyi135/")
        implicit val hc = HeaderCarrierConverter.fromRequest(rh)

        val headers = harness.enforceAuth

        headers must not contain (HeaderNames.AUTHORIZATION -> "a5sesqerTyi135/")
      }

      "must add empty auth header if no auth header supplied in request" in {
        val harness = new Harness()

        implicit val rh = FakeRequest()
        implicit val hc = HeaderCarrierConverter.fromRequest(rh)

        val headers = harness.enforceAuth

        headers must contain(HeaderNames.AUTHORIZATION -> "")
      }
    }

    "postPutXmlHeaders" - {
      "must add required headers for POST/PUT requests" - {
        "when client ID present" in {
          val harness = new Harness()

          implicit val rh = FakeRequest().withHeaders(Constants.ClientIdHeader -> "foo")
          implicit val hc = HeaderCarrierConverter.fromRequest(rh)

          val headers = harness.xmlHeaders

          headers must contain theSameElementsAs (
            Seq(
              HeaderNames.AUTHORIZATION -> "",
              HeaderNames.ACCEPT        -> ContentTypes.JSON,
              HeaderNames.CONTENT_TYPE  -> ContentTypes.XML,
              Constants.ChannelHeader   -> "api",
              Constants.ClientIdHeader  -> "foo"
            )
          )
        }

        "when client ID missing" in {
          val harness = new Harness()

          implicit val rh = FakeRequest()
          implicit val hc = HeaderCarrierConverter.fromRequest(rh)

          val headers = harness.xmlHeaders

          headers must contain theSameElementsAs (Seq(
            HeaderNames.AUTHORIZATION -> "",
            HeaderNames.ACCEPT        -> ContentTypes.JSON,
            HeaderNames.CONTENT_TYPE  -> ContentTypes.XML,
            Constants.ChannelHeader   -> "api"
          ))
        }

        "when auth header present" in {
          val harness = new Harness()

          implicit val rh = FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> "a5sesqerTyi135/")
          implicit val hc = HeaderCarrierConverter.fromRequest(rh)

          val headers = harness.xmlHeaders

          headers must contain theSameElementsAs (Seq(
            HeaderNames.ACCEPT       -> ContentTypes.JSON,
            HeaderNames.CONTENT_TYPE -> ContentTypes.XML,
            Constants.ChannelHeader  -> "api"
          ))
        }

        "when auth header missing" in {
          val harness = new Harness()

          implicit val rh = FakeRequest()
          implicit val hc = HeaderCarrierConverter.fromRequest(rh)

          val headers = harness.xmlHeaders

          headers must contain theSameElementsAs (Seq(
            HeaderNames.AUTHORIZATION -> "",
            HeaderNames.ACCEPT        -> ContentTypes.JSON,
            HeaderNames.CONTENT_TYPE  -> ContentTypes.XML,
            Constants.ChannelHeader   -> "api"
          ))
        }
      }
    }

    "getJsonHeaders" - {
      "must add required headers for GET requests" - {
        "when client ID present" in {
          val harness = new Harness()

          implicit val rh = FakeRequest().withHeaders(Constants.ClientIdHeader -> "foo")
          implicit val hc = HeaderCarrierConverter.fromRequest(rh)

          val headers = harness.jsonHeaders

          headers must contain theSameElementsAs (Seq(
            HeaderNames.AUTHORIZATION -> "",
            HeaderNames.ACCEPT        -> ContentTypes.JSON,
            Constants.ChannelHeader   -> "api",
            Constants.ClientIdHeader  -> "foo"
          ))
        }

        "when client ID missing" in {
          val harness = new Harness()

          implicit val rh = FakeRequest()
          implicit val hc = HeaderCarrierConverter.fromRequest(rh)

          val headers = harness.jsonHeaders

          headers must contain theSameElementsAs (Seq(
            HeaderNames.AUTHORIZATION -> "",
            HeaderNames.ACCEPT        -> ContentTypes.JSON,
            Constants.ChannelHeader   -> "api"
          ))
        }

        "when auth header present" in {
          val harness = new Harness()

          implicit val rh = FakeRequest().withHeaders(HeaderNames.AUTHORIZATION -> "a5sesqerTyi135/")
          implicit val hc = HeaderCarrierConverter.fromRequest(rh)

          val headers = harness.jsonHeaders

          headers must contain theSameElementsAs (Seq(
            HeaderNames.ACCEPT      -> ContentTypes.JSON,
            Constants.ChannelHeader -> "api"
          ))
        }

        "when auth header missing" in {
          val harness = new Harness()

          implicit val rh = FakeRequest()
          implicit val hc = HeaderCarrierConverter.fromRequest(rh)

          val headers = harness.jsonHeaders

          headers must contain theSameElementsAs (Seq(
            HeaderNames.AUTHORIZATION -> "",
            HeaderNames.ACCEPT        -> ContentTypes.JSON,
            Constants.ChannelHeader   -> "api"
          ))
        }
      }
    }
  }
}
