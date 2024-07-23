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

package utils.guaranteeParsing

import cats.data.ReaderT
import cats.implicits.catsSyntaxEitherId
import models.ParseError.DepartureEmpty
import models.ParseError.DestinationEmpty
import models.ParseError.InappropriateDepartureOffice
import models.DepartureOffice
import models.DestinationOffice
import models.ParseHandling
import org.mockito.Mockito.reset
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder

import java.time.Clock
import scala.xml.NodeSeq

class RouteCheckerSpec extends AnyFreeSpec with ParseHandling with MockitoSugar with BeforeAndAfterEach with Matchers with ScalaCheckPropertyChecks {

  val mockXmlReaders: GuaranteeXmlReaders = mock[GuaranteeXmlReaders]
  val mockClock: Clock                    = mock[Clock]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockXmlReaders)
  }

  protected def baseApplicationBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "metrics.jvm" -> false
      )

  def sut: RouteChecker = {
    val application = baseApplicationBuilder
      .overrides(
        bind[GuaranteeXmlReaders].toInstance(mockXmlReaders),
        bind[Clock].toInstance(mockClock)
      )
      .build()

    application.injector.instanceOf[RouteChecker]
  }

  "gbOnlyCheck" - {
    "returns parseError if officeOfDeparture fails to parse" in {
      when(mockXmlReaders.officeOfDeparture)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, DepartureOffice](
            _ => Left(DepartureEmpty("test"))
          )
        )

      sut.gbOnlyCheck(<example></example>) mustBe a[Left[DepartureEmpty, _]]
    }

    "returns parseError if officeOfDestination fails to parse" in {
      when(mockXmlReaders.officeOfDeparture)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, DepartureOffice](
            _ => Right(DepartureOffice("test"))
          )
        )

      when(mockXmlReaders.officeOfDestination)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, DestinationOffice](
            _ => Left(DestinationEmpty("test"))
          )
        )

      sut.gbOnlyCheck(<example></example>) mustBe a[Left[DestinationEmpty, _]]
    }

    "returns InappropriateDepartureOffice if we DepartureOffice doesn't start with GB or XI" in {
      when(mockXmlReaders.officeOfDeparture)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, DepartureOffice](
            _ => Right(DepartureOffice("UKabc"))
          )
        )
      when(mockXmlReaders.officeOfDestination)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, DestinationOffice](
            _ => Right(DestinationOffice("ITabc"))
          )
        )

      sut.gbOnlyCheck(<example></example>) mustBe a[Left[InappropriateDepartureOffice, _]]
    }

    "returns Right(false) if DepartureOffice starts with GB and DestinationOffice doesn't start with GB" in {
      when(mockXmlReaders.officeOfDeparture)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, DepartureOffice](
            _ => Right(DepartureOffice("GBabc"))
          )
        )
      when(mockXmlReaders.officeOfDestination)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, DestinationOffice](
            _ => Right(DestinationOffice("ITabc"))
          )
        )

      val result = sut.gbOnlyCheck(<example></example>)
      result mustBe false.asRight
    }

    "returns Right(true) if DepartureOffice starts with GB and DestinationOffice starts with GB" in {
      when(mockXmlReaders.officeOfDeparture)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, DepartureOffice](
            _ => Right(DepartureOffice("GBabc"))
          )
        )
      when(mockXmlReaders.officeOfDestination)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, DestinationOffice](
            _ => Right(DestinationOffice("GBabc"))
          )
        )

      val result = sut.gbOnlyCheck(<example></example>)
      result mustBe true.asRight
    }

    "returns Right(false) if DepartureOffice starts with XI" in {
      when(mockXmlReaders.officeOfDeparture)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, DepartureOffice](
            _ => Right(DepartureOffice("XIabc"))
          )
        )
      when(mockXmlReaders.officeOfDestination)
        .thenReturn(
          ReaderT[ParseHandler, NodeSeq, DestinationOffice](
            _ => Right(DestinationOffice("ITabc"))
          )
        )

      val result = sut.gbOnlyCheck(<example></example>)
      result mustBe a[Right[_, Boolean]]
      result mustBe false.asRight

    }
  }

}
