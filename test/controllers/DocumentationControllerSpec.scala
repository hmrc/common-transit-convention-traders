package controllers

import config.AppConfig
import controllers.documentation.DocumentationController
import org.mockito.Mockito.when
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.should.Matchers.shouldBe
import org.scalatest.prop.TableFor3
import org.scalatest.prop.Tables.Table
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks.forAll
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.stubControllerComponents

class DocumentationControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite {

  "DocumentationController.determineDefinition" - {
    val permutations: TableFor3[Boolean, Boolean, String] = Table(
      ("alpha enabled", "beta enabled", "expected result"),
      (true, false, "v2_1-v3_0-alpha-definition.json"),
      (false, true, "v2_1-v3_0-beta-definition.json"),
      (true, true, "v2_1-v3_0-beta-definition.json"),
      (false, false, "v2_1-definition.json")
    )

    forAll(permutations) {
      (alphaEnabled: Boolean, betaEnabled: Boolean, expectedResult: String) =>
        s"should return $expectedResult if Alpha is $alphaEnabled and Beta is $betaEnabled" in new Setup {
          when(mockAppConfig.deployV3Alpha).thenReturn(alphaEnabled)
          when(mockAppConfig.deployV3Beta).thenReturn(betaEnabled)
          val result: String = controller.determineDefinition
          result shouldBe expectedResult
        }
    }
  }

  trait Setup {
    val mockAppConfig: AppConfig                       = mock[AppConfig]
    val mockControllerComponents: ControllerComponents = stubControllerComponents()
    val assets: Assets                                 = app.injector.instanceOf[Assets]

    val controller = new DocumentationController(assets, mockControllerComponents, mockAppConfig)
  }
}
