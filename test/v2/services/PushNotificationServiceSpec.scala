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

import config.AppConfig
import config.Constants
import models.common._
import models.common.errors.PushNotificationError
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status.NOT_FOUND
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.test.FakeHeaders
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import v2.base.TestCommonGenerators
import v2.connectors.PushNotificationsConnector
import v2.models.BoxId
import v2.models.request.PushNotificationsAssociation
import v2.models.responses.BoxResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PushNotificationServiceSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with TestCommonGenerators
    with ScalaCheckDrivenPropertyChecks
    with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockConnector: PushNotificationsConnector = mock[PushNotificationsConnector]
  val mockAppConfig: AppConfig                  = mock[AppConfig]

  val sut = new PushNotificationsServiceImpl(mockConnector, mockAppConfig)

  override protected def beforeEach(): Unit = {
    reset(mockConnector)
    reset(mockAppConfig)
  }

  val jsonPayloadGen: Gen[JsValue] = for {
    clientRefNum <- arbitrary[String]
    boxId        <- arbitrary[String]
    clientId     <- arbitrary[String]
    movementType <- Gen.oneOf(MovementType.Arrival, MovementType.Departure)
  } yield Json.obj(
    "clientRefNum" -> clientRefNum,
    "boxId"        -> boxId,
    "clientId"     -> clientId,
    "movementType" -> movementType.toString
  )

  "associate" - {

    "when there is no client ID, return a left with MissingClientId" in forAll(arbitrary[MovementId], arbitrary[MovementType], arbitrary[EORINumber]) {
      (movementId, movementType, eori) =>
        val headers = FakeHeaders()

        val result = sut.associate(movementId, movementType, headers, eori)

        whenReady(result.value) {
          r => r mustBe Left(PushNotificationError.MissingClientId)
        }
    }

    "when there is no box ID, assert that the association has the default box ID and return a right" in forAll(
      arbitrary[MovementId],
      arbitrary[MovementType],
      Gen.alphaNumStr,
      arbitrary[EORINumber]
    ) {
      (movementId, movementType, clientId, eori) =>
        val headers = FakeHeaders(Seq(Constants.XClientIdHeader -> clientId))

        val expectedAssociation = PushNotificationsAssociation(ClientId(clientId), movementType, None, eori)

        when(mockConnector.postAssociation(MovementId(anyString()), any())(any(), any()))
          .thenReturn(Future.failed(new Exception()))
        when(mockConnector.postAssociation(MovementId(anyString()), eqTo(expectedAssociation))(any(), any()))
          .thenReturn(Future.successful(BoxResponse(BoxId("test")))) // last wins

        val result = sut.associate(movementId, movementType, headers, eori)
        whenReady(result.value) {
          r => r mustBe Right(BoxResponse(BoxId("test")))
        }
    }

    "when there is a box ID, assert that the association has a box ID and return a right" in forAll(
      arbitrary[MovementId],
      arbitrary[MovementType],
      Gen.alphaNumStr,
      Gen.uuid.map(_.toString),
      arbitrary[EORINumber]
    ) {
      (movementId, movementType, clientId, boxId, eori) =>
        val headers = FakeHeaders(Seq(Constants.XClientIdHeader -> clientId, Constants.XCallbackBoxIdHeader -> boxId))

        val expectedAssociation = PushNotificationsAssociation(ClientId(clientId), movementType, Some(BoxId(boxId)), eori)

        when(mockConnector.postAssociation(MovementId(anyString()), any())(any(), any()))
          .thenReturn(Future.failed(new Exception()))
        when(mockConnector.postAssociation(MovementId(anyString()), eqTo(expectedAssociation))(any(), any()))
          .thenReturn(Future.successful(BoxResponse(BoxId("test")))) // last wins

        val result = sut.associate(movementId, movementType, headers, eori)
        whenReady(result.value) {
          r => r mustBe Right(BoxResponse(BoxId("test")))
        }
    }

    "when there is no box ID supplied and no default box ID is returned, return a Left of BoxNotFound" in forAll(
      arbitrary[MovementId],
      arbitrary[MovementType],
      Gen.alphaNumStr,
      arbitrary[EORINumber]
    ) {
      (movementId, movementType, clientId, eori) =>
        val headers = FakeHeaders(Seq(Constants.XClientIdHeader -> clientId))

        val expectedAssociation = PushNotificationsAssociation(ClientId(clientId), movementType, None, eori)

        when(mockConnector.postAssociation(MovementId(anyString()), any())(any(), any()))
          .thenReturn(Future.failed(new Exception()))
        when(mockConnector.postAssociation(MovementId(anyString()), eqTo(expectedAssociation))(any(), any()))
          .thenAnswer(
            _ => Future.failed(UpstreamErrorResponse("error", NOT_FOUND))
          ) // last wins

        val result = sut.associate(movementId, movementType, headers, eori)
        whenReady(result.value) {
          r => r mustBe Left(PushNotificationError.BoxNotFound)
        }
    }

    "when an error occurs, return a Left of Unexpected" in forAll(
      arbitrary[MovementId],
      arbitrary[MovementType],
      Gen.alphaNumStr,
      Gen.uuid.map(_.toString),
      arbitrary[EORINumber]
    ) {
      (movementId, movementType, clientId, boxId, eori) =>
        val headers = FakeHeaders(Seq(Constants.XClientIdHeader -> clientId, Constants.XCallbackBoxIdHeader -> boxId))

        val expectedException = new Exception()

        when(mockConnector.postAssociation(MovementId(anyString()), any())(any(), any())).thenReturn(Future.failed(expectedException))

        val result = sut.associate(movementId, movementType, headers, eori)
        whenReady(result.value) {
          r => r mustBe Left(PushNotificationError.UnexpectedError(thr = Some(expectedException)))
        }
    }
  }

  "update" - {

    "when the service is enabled and the update was successful, return a unit" in forAll(arbitrary[MovementId]) {
      movementId =>
        when(mockConnector.patchAssociation(MovementId(anyString()))(any(), any())).thenReturn(Future.successful(()))
        val result = sut.update(movementId)

        whenReady(result.value) {
          r => r mustBe Right(())
        }
    }

    "when a 404 is returned occurs, return a Left of AssociationNotFound" in forAll(arbitrary[MovementId]) {
      movementId =>
        when(mockConnector.patchAssociation(MovementId(anyString()))(any(), any())).thenReturn(Future.failed(UpstreamErrorResponse("NOT_FOUND", NOT_FOUND)))

        val result = sut.update(movementId)
        whenReady(result.value) {
          r => r mustBe Left(PushNotificationError.AssociationNotFound)
        }
    }

    "when an error occurs, return a Left of Unexpected" in forAll(arbitrary[MovementId]) {
      movementId =>
        val expectedException = new Exception()

        when(mockConnector.patchAssociation(MovementId(anyString()))(any(), any())).thenReturn(Future.failed(expectedException))

        val result = sut.update(movementId)
        whenReady(result.value) {
          r => r mustBe Left(PushNotificationError.UnexpectedError(thr = Some(expectedException)))
        }
    }
  }

  "postPpnsNotification" - {

    "should return Right(()) if the push notification was successfully sent" in forAll(arbitrary[MovementId], arbitrary[MessageId]) {
      (movementId, messageId) =>
        when(mockConnector.postPpnsSubmissionNotification(MovementId(anyString()), MessageId(anyString()), any())(any(), any()))
          .thenReturn(Future.successful(()))

        val jsonPayload = jsonPayloadGen.sample.get
        val result      = sut.postPpnsNotification(movementId, messageId, jsonPayload).value.futureValue

        result mustBe Right(())

    }

    "should return Left(BoxNotFound) if the box is not found" in forAll(arbitrary[MovementId], arbitrary[MessageId]) {
      (movementId, messageId) =>
        when(mockConnector.postPpnsSubmissionNotification(MovementId(anyString()), MessageId(anyString()), any())(any(), any()))
          .thenReturn(Future.failed(UpstreamErrorResponse("Box not found", NOT_FOUND)))
        val jsonPayload = jsonPayloadGen.sample.get

        val result = sut.postPpnsNotification(movementId, messageId, jsonPayload).value.futureValue

        result mustBe Left(PushNotificationError.BoxNotFound)

    }

    "should return Left(UnexpectedError) if an unexpected error occurs" in forAll(arbitrary[MovementId], arbitrary[MessageId]) {
      (movementId, messageId) =>
        when(mockConnector.postPpnsSubmissionNotification(MovementId(anyString()), MessageId(anyString()), any())(any(), any()))
          .thenReturn(Future.failed(new RuntimeException("Something went wrong")))
        val jsonPayload = jsonPayloadGen.sample.get

        whenReady(sut.postPpnsNotification(movementId, messageId, jsonPayload).value) {
          case Left(PushNotificationError.UnexpectedError(_)) => succeed
          case x                                              => fail(s"Expected Left(UnspectedError), got $x")
        }
    }

  }

}
