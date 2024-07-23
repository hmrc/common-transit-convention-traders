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

package utils.analysis

import scala.xml.NodeSeq
import java.nio.charset.StandardCharsets
import com.codahale.metrics.Histogram
import com.codahale.metrics.MetricRegistry
import metrics.MetricsKeys.Messages._

import javax.inject.Inject
import javax.inject.Singleton
import metrics.HasMetrics
import models.MessageType
import models.MessageType._
import utils.Constants._

@Singleton
class MessageAnalyser @Inject() (val metrics: MetricRegistry) extends HasMetrics {
  lazy val messageSize: Histogram             = histo(MessageSize)
  lazy val numberOfGoods: Histogram           = histo(NumberOfGoods)
  lazy val numberOfDocuments: Histogram       = histo(NumberOfDocuments)
  lazy val numberOfSpecialMentions: Histogram = histo(NumberOfSpecialMentions)
  lazy val numberOfSeals: Histogram           = histo(NumberOfSeals)

  private def trackMessageSize(xml: NodeSeq): Unit = {
    val size = xml.toString.getBytes(StandardCharsets.UTF_8).length
    messageSize.update(size)
  }

  private def trackNumberOfGoods(xml: NodeSeq): Unit = {
    val count = (xml \ GOOITEGDS).length
    numberOfGoods.update(count)
  }

  private def trackNumberOfDocuments(xml: NodeSeq): Unit =
    (xml \ GOOITEGDS)
      .foreach {
        node =>
          val count = (node \ PRODOCDC2).length
          numberOfDocuments.update(count)
      }

  private def trackNumberOfSpecialMentions(xml: NodeSeq): Unit =
    (xml \ GOOITEGDS)
      .foreach {
        node =>
          val count = (node \ SPEMENMT2).length
          numberOfSpecialMentions.update(count)
      }

  private def trackNumberOfSeals(xml: NodeSeq): Unit = {
    val count = (xml \ SEAINFSLI \ SEAIDSID).length
    numberOfSeals.update(count)
  }

  def trackMessageStats(xml: NodeSeq): Unit =
    MessageType.getMessageType(xml).foreach {
      case DepartureDeclaration | UnloadingRemarks =>
        trackMessageSize(xml)
        trackNumberOfGoods(xml)
        trackNumberOfDocuments(xml)
        trackNumberOfSpecialMentions(xml)
        trackNumberOfSeals(xml)
      case _ => trackMessageSize(xml)
    }
}
