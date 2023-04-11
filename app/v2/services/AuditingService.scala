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

package v2.services

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import v2.connectors.AuditingConnector
import v2.models.AuditType
import v2.models.ObjectStoreResourceLocation

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.NonFatal

@ImplementedBy(classOf[AuditingServiceImpl])
trait AuditingService {

  def audit(auditType: AuditType, source: Source[ByteString, _], contentType: String, contentLength: Long = 0)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit]

  def audit(auditType: AuditType, uri: ObjectStoreResourceLocation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]
}

class AuditingServiceImpl @Inject() (auditingConnector: AuditingConnector) extends AuditingService with Logging {

  override def audit(auditType: AuditType, source: Source[ByteString, _], contentType: String, contentLength: Long)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] =
    auditingConnector.post(auditType, source, contentType, contentLength).recover {
      case NonFatal(e) =>
        logger.warn("Unable to audit payload due to an exception", e)
    }

  override def audit(auditType: AuditType, uri: ObjectStoreResourceLocation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    auditingConnector.post(auditType, uri).recover {
      case NonFatal(e) =>
        logger.warn("Unable to audit payload from object store due to an exception", e)
    }
}
