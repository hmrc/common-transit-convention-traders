package services

import connectors.WiremockSuite
import data.EnsureGuaranteeServiceTestData
import models.request.DepartureDeclarationXSD
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import data.{EnsureGuaranteeServiceTestData => TestData}

class EnsureGuaranteeServiceIntegrationSpec extends AnyFreeSpec with Matchers with WiremockSuite with ScalaFutures with IntegrationPatience with ScalaCheckPropertyChecks {

  "ensureGuarantee" - {



    "must default value correct" in {
      val service = app.injector.instanceOf[EnsureGuaranteeService]

      val result = service.ensureGuarantee(TestData.buildXml(TestData.standardInputXML))

      result.right.get.toString().filter(_ > ' ') mustEqual TestData.buildXml(TestData.standardExpectedXML).toString().filter(_ > ' ')

    }

    "result must pass standard validation" in {
      val service = app.injector.instanceOf[EnsureGuaranteeService]
      val validator = app.injector.instanceOf[XmlValidationService]

      val result = service.ensureGuarantee(TestData.buildXml(TestData.standardInputXML))

      validator.validate(result.right.get.toString().filter(_ > ' '), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
    }

    "result must not be changed if no standard guarantees" in {
      val service = app.injector.instanceOf[EnsureGuaranteeService]
      val result = service.ensureGuarantee(TestData.buildXml(TestData.otherInputXML))

      result.right.get.toString().filter(_ > ' ') mustEqual TestData.buildXml(TestData.otherInputXML).toString().filter(_ > ' ')
    }

    "must add default special mentions to first good if no special mention present for guarantees" in {
      val service = app.injector.instanceOf[EnsureGuaranteeService]
      val validator = app.injector.instanceOf[XmlValidationService]
      val result = service.ensureGuarantee(TestData.buildXml(TestData.extraGuaranteesInputXML))

      result.right.get.toString().filter(_ > ' ') mustEqual TestData.buildXml(TestData.extraGuaranteesExpectedXML).toString().filter(_ > ' ')
      validator.validate(result.right.get.toString().filter(_ > ' '), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
    }

    "must add default special mentions to first good if special mention isn't present for a guarantee" in {
      val service = app.injector.instanceOf[EnsureGuaranteeService]
      val validator = app.injector.instanceOf[XmlValidationService]
      val result = service.ensureGuarantee(TestData.buildXml(TestData.extraGuaranteesComboInputXML))

      result.right.get.toString().filter(_ > ' ') mustEqual TestData.buildXml(TestData.extraGuaranteesComboExpectedXML).toString().filter(_ > ' ')
      validator.validate(result.right.get.toString().filter(_ > ' '), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]
    }

    "must allow non-guarantee special mentions through without issue" in {
      val service = app.injector.instanceOf[EnsureGuaranteeService]
      val validator = app.injector.instanceOf[XmlValidationService]
      val result = service.ensureGuarantee(TestData.buildXml(TestData.oddSpecialMentionsInputXml))

      result.right.get.toString().filter(_ > ' ') mustEqual TestData.buildXml(TestData.oddSpecialMentionsOutputXml).toString().filter(_ > ' ')
      validator.validate(result.right.get.toString().filter(_ > ' '), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]

    }

    "must allow CAL special mentions with additional values through without removing fields" in {
      val service = app.injector.instanceOf[EnsureGuaranteeService]
      val validator = app.injector.instanceOf[XmlValidationService]
      val result = service.ensureGuarantee(TestData.buildXml(TestData.mixedSpecialMentionsInputXml))

      result.right.get.toString().filter(_ > ' ') mustEqual TestData.buildXml(TestData.mixedSpecialMentionsOutputXml).toString().filter(_ > ' ')
      validator.validate(result.right.get.toString().filter(_ > ' '), DepartureDeclarationXSD) mustBe a[Right[_, XmlValid]]

    }

  }


  override protected def portConfigKey: String = "microservice.services.transit-movement-trader-at-destination.port"

}
