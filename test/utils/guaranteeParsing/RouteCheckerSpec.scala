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

package utils.guaranteeParsing

import cats.data.ReaderT
import models.{DepartureOffice, DestinationOffice, ParseHandling}
import models.ParseError.{DepartureEmpty, DestinationEmpty, InappropriateDepartureOffice}
import org.mockito.Mockito.reset
import org.scalatest.BeforeAndAfterEach
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import org.mockito.Mockito._
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.mockito.ArgumentMatchers.any

import scala.xml.NodeSeq

class RouteCheckerSpec  extends AnyFreeSpec with ParseHandling with MockitoSugar with BeforeAndAfterEach with Matchers with ScalaCheckPropertyChecks{

  val mockXmlReaders = mock[GuaranteeXmlReaders]

  override def beforeEach = {
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
        bind[GuaranteeXmlReaders].toInstance(mockXmlReaders)
      )
      .build()

    application.injector.instanceOf[RouteChecker]
  }

  "gbOnlyCheck" - {
    "returns parseError if officeOfDeparture fails to parse" in {
      when(mockXmlReaders.officeOfDeparture)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, DepartureOffice](_ => Left(DepartureEmpty("test"))))

      sut.gbOnlyCheck(<example></example>) mustBe a[Left[DepartureEmpty, _]]
    }

    "returns parseError if officeOfDestination fails to parse" in {
      when(mockXmlReaders.officeOfDeparture)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, DepartureOffice](_ => Right(DepartureOffice("test"))))

      when(mockXmlReaders.officeOfDestination)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, DestinationOffice](_ => Left(DestinationEmpty("test"))))

      sut.gbOnlyCheck(<example></example>) mustBe a[Left[DestinationEmpty, _]]
    }

    "returns InappropriateDepartureOffice if we DepartureOffice doesn't start with GB or XI" in {
      when(mockXmlReaders.officeOfDeparture)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, DepartureOffice](_ => Right(DepartureOffice("UKabc"))))
      when(mockXmlReaders.officeOfDestination)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, DestinationOffice](_ => Right(DestinationOffice("ITabc"))))

      sut.gbOnlyCheck(<example></example>) mustBe a[Left[InappropriateDepartureOffice, _]]
    }

    "returns Right(false) if DepartureOffice starts with GB and DestinationOffice doesn't start with GB" in {
      when(mockXmlReaders.officeOfDeparture)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, DepartureOffice](_ => Right(DepartureOffice("GBabc"))))
      when(mockXmlReaders.officeOfDestination)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, DestinationOffice](_ => Right(DestinationOffice("ITabc"))))

      val result = sut.gbOnlyCheck(<example></example>)
      result mustBe a[Right[_ ,Boolean]]
      result.right.get mustBe false
    }

    "returns Right(true) if DepartureOffice starts with GB and DestinationOffice starts with GB" in {
      when(mockXmlReaders.officeOfDeparture)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, DepartureOffice](_ => Right(DepartureOffice("GBabc"))))
      when(mockXmlReaders.officeOfDestination)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, DestinationOffice](_ => Right(DestinationOffice("GBabc"))))

      val result = sut.gbOnlyCheck(<example></example>)
      result mustBe a[Right[_ ,Boolean]]
      result.right.get mustBe true
    }

    "returns Right(false) if DepartureOffice starts with XI" in {
      when(mockXmlReaders.officeOfDeparture)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, DepartureOffice](_ => Right(DepartureOffice("XIabc"))))
      when(mockXmlReaders.officeOfDestination)
        .thenReturn(ReaderT[ParseHandler, NodeSeq, DestinationOffice](_ => Right(DestinationOffice("ITabc"))))

      val result = sut.gbOnlyCheck(<example></example>)
      result mustBe a[Right[_ ,Boolean]]
      result.right.get mustBe false

    }
  }



}
