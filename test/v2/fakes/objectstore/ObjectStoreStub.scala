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

package v2.fakes.objectstore

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.http.MimeTypes
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.objectstore.client.Md5Hash
import uk.gov.hmrc.objectstore.client.Object
import uk.gov.hmrc.objectstore.client.ObjectMetadata
import uk.gov.hmrc.objectstore.client.ObjectSummaryWithMd5
import uk.gov.hmrc.objectstore.client.Path
import uk.gov.hmrc.objectstore.client.RetentionPeriod
import uk.gov.hmrc.objectstore.client.config.ObjectStoreClientConfig
import uk.gov.hmrc.objectstore.client.http.ObjectStoreContentRead
import uk.gov.hmrc.objectstore.client.play.ResBody
import uk.gov.hmrc.objectstore.client.play.test.stub.StubPlayObjectStoreClient
import v2.base.TestCommonGenerators

import java.time.Instant
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ObjectStoreStub[F[_]](config: ObjectStoreClientConfig)(implicit
  m: Materializer,
  ec: ExecutionContext
) extends StubPlayObjectStoreClient(config)
    with TestCommonGenerators {

  private type FilePath = String
  private val objectStore = mutable.Map.empty[FilePath, ObjectSummaryWithMd5]

  override def uploadFromUrl(
    from: java.net.URL,
    to: Path.File,
    retentionPeriod: RetentionPeriod = config.defaultRetentionPeriod,
    contentType: Option[String] = None,
    contentMd5: Option[Md5Hash] = None,
    owner: String = config.owner
  )(implicit hc: HeaderCarrier): Future[ObjectSummaryWithMd5] = {
    val objectSummaryWithMd5 = arbitraryObjectSummaryWithMd5.arbitrary.sample.get
    //remove date from uri as it can't be guessed, and therefore used to retrieve the file later on
    val splitFileName = to.fileName.split("-").dropRight(1)
    val newFileName   = s"${splitFileName(0)}-${splitFileName(1)}.xml"
    val newToPath     = Path.Directory(s"movements/${splitFileName.head}").file(newFileName)

    objectStore += (s"$owner/${newToPath.asUri}" -> objectSummaryWithMd5)
    Future.successful(objectSummaryWithMd5)
  }

  override def getObject[CONTENT](path: Path.File, owner: String = config.owner)(implicit
    cr: ObjectStoreContentRead[Future, ResBody, CONTENT],
    hc: HeaderCarrier
  ): Future[Option[Object[CONTENT]]] =
    objectStore
      .get(s"$owner/${path.asUri}")
      .map {
        o =>
          val source: ResBody = Source.single(ByteString("stream"))

          cr.readContent(source)
            .flatMap {
              content =>
                Future.successful(
                  Option(
                    Object(
                      path,
                      content,
                      ObjectMetadata(
                        MimeTypes.XML,
                        o.contentLength,
                        o.contentMd5,
                        Instant.now(),
                        Map.empty
                      )
                    )
                  )
                )
            }
      }
      .getOrElse(Future.successful(None))

}
