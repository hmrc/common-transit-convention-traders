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

package v2.models.responses

import play.api.libs.json._
import v2.models.responses.UpscanResponse.DownloadUrl
import v2.models.responses.UpscanResponse.FileStatus
import v2.models.responses.UpscanResponse.Reference

import java.net.URL
import java.time.Instant

final case class UploadDetails(fileName: String, fileMimeType: String, uploadTimestamp: Instant, checksum: String, size: Long)

object UploadDetails {
  implicit val format = Json.format[UploadDetails]
}

final case class FailureDetails(failureReason: String, message: String)

object FailureDetails {
  implicit val format = Json.format[FailureDetails]
}

object UpscanResponse {

  case class Reference(value: String) extends AnyVal

  object Reference {
    implicit val format: Format[Reference] = Json.valueFormat[Reference]
  }

  case class DownloadUrl(value: String) extends AnyVal

  object DownloadUrl {
    implicit val format: Format[DownloadUrl] = Json.valueFormat[DownloadUrl]
  }

  sealed trait FileStatus

  object FileStatus {
    case object Ready extends FileStatus

    case object Failed extends FileStatus

    val values = Seq(Ready, Failed)

    implicit val writes = new Writes[FileStatus] {

      def writes(status: FileStatus) = Json.toJson(status.toString())
    }

    implicit val reads: Reads[FileStatus] = Reads {
      case JsString(x) if x.toLowerCase == "ready"  => JsSuccess(Ready)
      case JsString(x) if x.toLowerCase == "failed" => JsSuccess(Failed)
      case _                                        => JsError("Invalid file status")
    }
  }

  implicit val upscanResponseFormat = Json.format[UpscanResponse]
}

final case class UpscanResponse(
  reference: Reference,
  fileStatus: FileStatus,
  downloadUrl: Option[DownloadUrl],
  uploadDetails: Option[UploadDetails],
  failureDetails: Option[FailureDetails]
) {
  val isSuccess = uploadDetails.isDefined
}
