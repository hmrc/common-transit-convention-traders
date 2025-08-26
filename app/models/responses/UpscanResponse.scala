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

package models.responses

import play.api.libs.json._
import UpscanResponse.DownloadUrl
import UpscanResponse.FileStatus
import UpscanResponse.Reference

import java.time.Instant

final case class UploadDetails(fileName: String, fileMimeType: String, uploadTimestamp: Instant, checksum: String, size: Long)

object UploadDetails {
  implicit val format: OFormat[UploadDetails] = Json.format[UploadDetails]
}

final case class FailureDetails(failureReason: String, message: String)

object FailureDetails {
  implicit val format: OFormat[FailureDetails] = Json.format[FailureDetails]
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

    val values: Seq[FileStatus] = Seq(Ready, Failed)

    implicit val writes: Writes[FileStatus] = (status: FileStatus) => Json.toJson(status.toString)

    implicit val reads: Reads[FileStatus] = Reads {
      case JsString(x) if x.toLowerCase == "ready"  => JsSuccess(Ready)
      case JsString(x) if x.toLowerCase == "failed" => JsSuccess(Failed)
      case _                                        => JsError("Invalid file status")
    }
  }

  implicit val upscanSuccessResponseFormat: OFormat[UpscanSuccessResponse] = Json.format[UpscanSuccessResponse]
  implicit val upscanFailedResponseFormat: OFormat[UpscanFailedResponse]   = Json.format[UpscanFailedResponse]

  implicit val upscanResponseReads: Reads[UpscanResponse] = Reads[UpscanResponse] {
    jsValue =>
      jsValue \ "fileStatus" match {
        case JsDefined(JsString("READY"))  => upscanSuccessResponseFormat.reads(jsValue)
        case JsDefined(JsString("FAILED")) => upscanFailedResponseFormat.reads(jsValue)
        case _                             => JsError("Invalid")
      }
  }

  implicit val upscanResponsWrites: OWrites[UpscanResponse] = OWrites[UpscanResponse] {
    case x: UpscanSuccessResponse => upscanSuccessResponseFormat.writes(x) ++ Json.obj("fileStatus" -> "READY")
    case x: UpscanFailedResponse  => upscanFailedResponseFormat.writes(x) ++ Json.obj("fileStatus" -> "FAILED")
  }
}

sealed abstract class UpscanResponse {
  def reference: Reference
  def fileStatus: FileStatus
}

final case class UpscanSuccessResponse(reference: Reference, downloadUrl: DownloadUrl, uploadDetails: UploadDetails) extends UpscanResponse {
  override val fileStatus: FileStatus = FileStatus.Ready
}

final case class UpscanFailedResponse(reference: Reference, failureDetails: FailureDetails) extends UpscanResponse {
  override def fileStatus: FileStatus = FileStatus.Failed
}
