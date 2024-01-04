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

import com.codahale.metrics.Histogram
import com.codahale.metrics.MetricRegistry
import data.TestXml
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar

class MessageAnalyserSpec extends AnyFreeSpec with Matchers with MockitoSugar with TestXml with BeforeAndAfterEach {

  private val analyser = new MessageAnalyser(new MetricRegistry) {
    override lazy val messageSize: Histogram             = mock[Histogram]
    override lazy val numberOfGoods: Histogram           = mock[Histogram]
    override lazy val numberOfDocuments: Histogram       = mock[Histogram]
    override lazy val numberOfSpecialMentions: Histogram = mock[Histogram]
    override lazy val numberOfSeals: Histogram           = mock[Histogram]
  }

  private val trackedXmlMovements =
    Seq(CC015B, CC015BRequiringDefaultGuarantee, CC015BWithMultipleGoodsItems, CC015BwithMesSenMES3, CC044A, CC044AWithMultipleGoodsItems, CC044AwithMesSenMES3)
  private val untrackedXmlMovements = Seq(CC007A, CC007AwithMesSenMES3, CC014A, CC014AwithMesSenMES3)

  def resetMocks()                          = reset(analyser.messageSize, analyser.numberOfGoods, analyser.numberOfDocuments, analyser.numberOfSpecialMentions, analyser.numberOfSeals)
  override protected def beforeEach(): Unit = resetMocks()

  "MessageAnalyser" - {

    "trackMessageStats" - {

      "must only return size if message is not a DepartureDeclaration or UnloadingRemarks" in {
        untrackedXmlMovements.foreach {
          xml =>
            resetMocks()
            analyser.trackMessageStats(xml)

            // messageSize should be called once
            verify(analyser.messageSize, times(1)).update(anyInt())

            // everything else shouldnt be called
            verifyNoInteractions(analyser.numberOfGoods)
            verifyNoInteractions(analyser.numberOfDocuments)
            verifyNoInteractions(analyser.numberOfSpecialMentions)
            verifyNoInteractions(analyser.numberOfSeals)
        }
      }

      "must be called the appropriate number of times for a DepartureDeclaration (CC015B)" in {
        analyser.trackMessageStats(CC015B)

        verify(analyser.messageSize, times(1)).update(anyInt())
        verify(analyser.numberOfGoods, times(1)).update(1)
        verify(analyser.numberOfDocuments, times(1)).update(1)
        verify(analyser.numberOfSpecialMentions, times(1)).update(0)
        verify(analyser.numberOfSeals, times(1)).update(1)

      }

      "must be called the appropriate number of times for a DepartureDeclaration with no documents (CC015BRequiringDefaultGuarantee)" in {
        analyser.trackMessageStats(CC015BRequiringDefaultGuarantee)

        verify(analyser.messageSize, times(1)).update(anyInt())
        verify(analyser.numberOfGoods, times(1)).update(1)
        verify(analyser.numberOfDocuments, times(1)).update(0)
        verify(analyser.numberOfSpecialMentions, times(1)).update(1)
        verify(analyser.numberOfSeals, times(1)).update(1)

      }

      "must be called the appropriate number of times for an UnloadingRemarks (CC044A)" in {
        analyser.trackMessageStats(CC044A)

        verify(analyser.messageSize, times(1)).update(anyInt())
        verify(analyser.numberOfGoods, times(1)).update(1)
        verify(analyser.numberOfDocuments, times(1)).update(1)
        verify(analyser.numberOfSpecialMentions, times(1)).update(0)
        verify(analyser.numberOfSeals, times(1)).update(1)

      }

      "must be called the appropriate number of times for an UnloadingRemarks (CC044AWithMultipleGoodsItems) with multiple goods items" in {
        analyser.trackMessageStats(CC044AWithMultipleGoodsItems)

        verify(analyser.messageSize, times(1)).update(anyInt())
        verify(analyser.numberOfGoods, times(1)).update(2)

        val numberOfDocuments = Mockito.inOrder(analyser.numberOfDocuments)
        numberOfDocuments.verify(analyser.numberOfDocuments).update(1)
        numberOfDocuments.verify(analyser.numberOfDocuments).update(2)

        verify(analyser.numberOfSpecialMentions, times(2)).update(0)
        verify(analyser.numberOfSeals, times(1)).update(1)

      }

      "must be called the appropriate number of times for a DepartureDeclaration with multiple goods items (CC015BWithMultipleGoodsItems)" in {
        analyser.trackMessageStats(CC015BWithMultipleGoodsItems)

        verify(analyser.messageSize, times(1)).update(anyInt())
        verify(analyser.numberOfGoods, times(1)).update(2)

        val numberOfDocuments = Mockito.inOrder(analyser.numberOfDocuments)
        numberOfDocuments.verify(analyser.numberOfDocuments).update(0)
        numberOfDocuments.verify(analyser.numberOfDocuments).update(1)

        val numberOfSpecialMentions = Mockito.inOrder(analyser.numberOfSpecialMentions)
        numberOfSpecialMentions.verify(analyser.numberOfSpecialMentions).update(1)
        numberOfSpecialMentions.verify(analyser.numberOfSpecialMentions).update(0)

        verify(analyser.numberOfSeals, times(1)).update(1)

      }

    }

  }
}
