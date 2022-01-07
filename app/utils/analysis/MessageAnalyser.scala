/*
 * Copyright 2022 HM Revenue & Customs
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
import com.kenshoo.play.metrics.Metrics
import metrics.MetricsKeys.Messages._
import javax.inject.Inject
import javax.inject.Singleton
import metrics.HasMetrics
import models.MessageType
import models.MessageType._

@Singleton
class MessageAnalyser @Inject() (val metrics: Metrics) extends HasMetrics {
  lazy val messageSize             = histo(MessageSize)
  lazy val numberOfGoods           = histo(NumberOfGoods)
  lazy val numberOfDocuments       = histo(NumberOfDocuments)
  lazy val numberOfSpecialMentions = histo(NumberOfSpecialMentions)
  lazy val numberOfSeals           = histo(NumberOfSeals)

  private def trackMessageSize(xml: NodeSeq): Unit = {
    val size = xml.toString.getBytes(StandardCharsets.UTF_8).length
    messageSize.update(size)
  }

  private def trackNumberOfGoods(xml: NodeSeq): Unit = {
    val count = (xml \ "GOOITEGDS").length
    numberOfGoods.update(count)
  }

  private def trackNumberOfDocuments(xml: NodeSeq): Unit =
    (xml \ "GOOITEGDS")
      .foreach {
        node =>
          val count = (node \ "PRODOCDC2").length // TODO double check code
          numberOfDocuments.update(count)
      }

  private def trackNumberOfSpecialMentions(xml: NodeSeq): Unit =
    (xml \ "GOOITEGDS")
      .foreach {
        node =>
          val count = (node \ "SPEMENMT2").length
          numberOfSpecialMentions.update(count)
      }

  private def trackNumberOfSeals(xml: NodeSeq): Unit = {
    val count = (xml \ "SEAINFSLI" \ "SEAIDSID").length
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

// TODO put all names in constants file
