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

package v2.models.responses.hateoas

import org.scalacheck.Arbitrary.arbitrary
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.Json
import v2.base.CommonGenerators
import v2.models.BoxId
import v2.models.MovementId
import v2.models.MovementType
import v2.models.responses.BoxResponse
import v2.models.responses.MovementResponse
import v2.models.responses.UpscanFormTemplate
import v2.models.responses.UpscanInitiateResponse
import v2.models.responses.UpscanReference

class HateoasNewMovementResponseSpec extends AnyFreeSpec with Matchers with ScalaCheckDrivenPropertyChecks with OptionValues with CommonGenerators {

  for (movementType <- MovementType.values)
    s"${movementType.movementType} create a valid HateoasNewMovementResponse" - {

      "when the movement response does not contain message Id or box Id" in forAll(
        arbitraryMovementResponse(false, false).arbitrary
      ) {
        movementResponse =>
          val actual = HateoasNewMovementResponse(movementResponse, None, movementType)

          val expected = Json.obj(
            "_links" -> Json.obj(
              "self" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}"
              ),
              "messages" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}/messages"
              )
            )
          )

          actual mustBe expected
      }

      "when the movement response contains BoxId" in forAll(
        arbitraryMovementResponse(false, true).arbitrary
      ) {
        movementResponse =>
          val actual = HateoasNewMovementResponse(movementResponse, None, movementType)

          val expected = Json.obj(
            "_links" -> Json.obj(
              "self" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}"
              ),
              "messages" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}/messages"
              )
            ),
            "boxId" -> s"${movementResponse.boxResponse.get.boxId.value}"
          )

          actual mustBe expected
      }

      "with a movement response that contains box ID and an Upload response" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary,
        arbitraryMovementResponse(false, true).arbitrary
      ) {
        (upscanResponse, movementResponse) =>
          val actual = HateoasNewMovementResponse(movementResponse, Some(upscanResponse), movementType)

          val expected = Json.obj(
            "_links" -> Json.obj(
              "self" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}"
              ),
              "messages" -> Json.obj(
                "href" -> s"/customs/transits/movements/${movementType.urlFragment}/${movementResponse.movementId.value}/messages"
              )
            ),
            "boxId" -> s"${movementResponse.boxResponse.get.boxId.value}",
            "uploadRequest" -> Json.obj(
              "href"   -> upscanResponse.uploadRequest.href,
              "fields" -> upscanResponse.uploadRequest.fields
            )
          )

          actual mustBe expected
      }
    }

  private def upscanResponse =
    UpscanInitiateResponse(
      UpscanReference("b72d9aea-fdb9-40f1-800c-3612154baf07"),
      UpscanFormTemplate(
        "http://localhost:9570/upscan/upload-proxy",
        Map(
          "x-amz-meta-callback-url"             -> "https://myservice.com/callback",
          "x-amz-date"                          -> "20230118T135545Z",
          "success_action_redirect"             -> "https://myservice.com/nextPage?key=b72d9aea-fdb9-40f1-800c-3612154baf07",
          "x-amz-credential"                    -> "ASIAxxxxxxxxx/20180202/eu-west-2/s3/aws4_request",
          "x-amz-meta-upscan-initiate-response" -> "2023-01-18T13:55:45.715Z",
          "x-amz-meta-upscan-initiate-received" -> "2023-01-18T13:55:45.715Z",
          "x-amz-meta-request-id"               -> "7075a21c-c8f0-402e-9c9c-1eea546c6fbf",
          "x-amz-meta-original-filename"        -> "${filename}",
          "x-amz-algorithm"                     -> "AWS4-HMAC-SHA256",
          "key"                                 -> "b72d9aea-fdb9-40f1-800c-3612154baf07",
          "acl"                                 -> "private",
          "x-amz-signature"                     -> "xxxx",
          "error_action_redirect"               -> "https://myservice.com/errorPage",
          "x-amz-meta-session-id"               -> "3506d041-ba59-41ee-bb2c-bf0363163be3",
          "x-amz-meta-consuming-service"        -> "PostmanRuntime/7.29.2",
          "policy"                              -> "eyJjb25kaXRpb25zIjpbWyJjb250ZW50LWxlbmd0aC1yYW5nZSIsMCwxMDI0XV19"
        )
      )
    )

}
