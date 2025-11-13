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

package services

import base.TestActorSystem
import base.TestCommonGenerators
import connectors.PersistenceConnector
import models.common.*
import models.common.errors.PersistenceError
import models.request.MessageType
import models.request.MessageUpdate
import models.responses.*
import models.*
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq as eqTo
import org.mockito.Mockito.reset
import org.mockito.Mockito.when
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import models.Version.V2_1
import models.Version.V3_0
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class PersistenceServiceSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with TestCommonGenerators
    with TestActorSystem
    with ScalaCheckDrivenPropertyChecks
    with BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockConnector: PersistenceConnector = mock[PersistenceConnector]
  val sut                                 = new PersistenceService(mockConnector)
  val version: Version                    = Gen.oneOf(V2_1, V3_0).sample.value

  override def beforeEach(): Unit =
    reset(mockConnector)

  "Submitting a Departure Declaration" - {

    val validRequest: Source[ByteString, NotUsed]   = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
    val invalidRequest: Source[ByteString, NotUsed] = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful submission, should return a Right" in {
      when(mockConnector.postMovement(EORINumber(any[String]), any(), eqTo(Some(validRequest)), eqTo(version))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(MovementResponse(MovementId("ABC"), MessageId("123"))))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Departure, Some(validRequest), version)
      val expected: Either[PersistenceError, MovementResponse] = Right(MovementResponse(MovementId("ABC"), MessageId("123")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      when(mockConnector.postMovement(EORINumber(any[String]), any(), eqTo(Some(invalidRequest)), eqTo(version))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Departure, Some(invalidRequest), version)
      val expected: Either[PersistenceError, MovementResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

  "Submitting a Departure Declaration for Large Messages" - {

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful submission, should return a Right" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      when(mockConnector.postMovement(EORINumber(any[String]), any(), any(), any())(eqTo(hc), any[ExecutionContext]))
        .thenReturn(Future.successful(MovementResponse(MovementId("ABC"), MessageId("1234567890abcdsd"))))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Departure, None, version)
      val expected: Either[PersistenceError, MovementResponse] = Right(MovementResponse(MovementId("ABC"), MessageId("1234567890abcdsd")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      when(mockConnector.postMovement(EORINumber(any[String]), any(), any(), any())(eqTo(hc), any[ExecutionContext]))
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Departure, None, version)
      val expected: Either[PersistenceError, MovementResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

  "Getting a list of Departure message IDs" - {

    val dateTime = Gen.option(arbitrary[OffsetDateTime])

    "when a departure is found, should return a Right of the sequence of message IDs" in forAll(
      Gen.listOfN(3, arbitrary[MessageSummary])
    ) {
      summaries =>
        val expected = PaginationMessageSummary(TotalCount(summaries.length.toLong), summaries)
        when(
          mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any(), eqTo(Some(PageNumber(2))), ItemCount(eqTo(30L)), any(), any())(
            any(),
            any()
          )
        )
          .thenReturn(Future.successful(expected))

        val result = sut.getMessages(
          EORINumber("1"),
          MovementType.Departure,
          MovementId("1234567890abcdef"),
          dateTime.sample.get,
          Some(PageNumber(2)),
          ItemCount(30),
          dateTime.sample.get,
          version
        )
        whenReady(result.value) {
          _ mustBe Right(expected)
        }
    }

    "when a given movement is not found, and the page is 1, should return a MovementNotFound" in {

      when(
        mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any(), eqTo(Some(PageNumber(1))), ItemCount(eqTo(35L)), any(), any())(
          any(),
          any()
        )
      )
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMessages(
        EORINumber("1"),
        MovementType.Departure,
        MovementId("1234567890abcdef"),
        dateTime.sample.get,
        Some(PageNumber(1)),
        ItemCount(35),
        dateTime.sample.get,
        version
      )
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MovementNotFound(MovementId("1234567890abcdef"), MovementType.Departure))
      }
    }

    "when a given movement is not found, and the page is greater than 1, should return a MovementNotFound" in {

      when(
        mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any(), eqTo(Some(PageNumber(9))), ItemCount(eqTo(35L)), any(), any())(
          any(),
          any()
        )
      )
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMessages(
        EORINumber("1"),
        MovementType.Departure,
        MovementId("1234567890abcdef"),
        dateTime.sample.get,
        Some(PageNumber(9)),
        ItemCount(35),
        dateTime.sample.get,
        version
      )
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MovementNotFound(MovementId("1234567890abcdef"), MovementType.Departure))
      }
    }

    "when a given movement is found and the page is greater than 1 and empty, then should return a Page Not Found" in {

      val expected = PaginationMessageSummary(TotalCount(0), Seq.empty[MessageSummary])

      when(
        mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any(), eqTo(Some(PageNumber(2))), ItemCount(eqTo(35L)), any(), any())(
          any(),
          any()
        )
      )
        .thenReturn(Future.successful(expected))

      val result = sut.getMessages(
        EORINumber("1"),
        MovementType.Departure,
        MovementId("1234567890abcdef"),
        dateTime.sample.get,
        Some(PageNumber(2)),
        ItemCount(35),
        dateTime.sample.get,
        version
      )
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.PageNotFound)
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(
        mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any(), eqTo(Some(PageNumber(4))), ItemCount(eqTo(40L)), any(), any())(
          any(),
          any()
        )
      )
        .thenReturn(Future.failed(error))

      val result = sut.getMessages(
        EORINumber("1"),
        MovementType.Departure,
        MovementId("1234567890abcdef"),
        dateTime.sample.get,
        Some(PageNumber(4)),
        ItemCount(40),
        dateTime.sample.get,
        version
      )
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
      }
    }

  }

  "Getting a Single Message" - {

    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "when a message is found, should return a Right" in {
      val successResponse = MessageSummary(
        MessageId("1234567890abcdef"),
        now,
        Some(MessageType.DeclarationData),
        Some(XmlPayload("<test></test>")),
        Some(MessageStatus.Success),
        None
      )

      when(mockConnector.getMessage(EORINumber(any()), any(), MovementId(any()), MessageId(any()), any())(any(), any()))
        .thenReturn(Future.successful(successResponse))

      val result = sut.getMessage(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"), MessageId("1234567890abcdef"), version)
      whenReady(result.value) {
        _ mustBe Right(successResponse)
      }
    }

    "when a message is not found, should return a Left with an MessageNotFound" in {
      when(mockConnector.getMessage(EORINumber(any()), any(), MovementId(any()), MessageId(any()), any())(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMessage(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"), MessageId("1234567890abcdef"), version)
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MessageNotFound(MovementId("1234567890abcdef"), MessageId("1234567890abcdef")))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getMessage(EORINumber(any()), any(), MovementId(any()), MessageId(any()), any())(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getMessage(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"), MessageId("1234567890abcdef"), version)
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
      }
    }

  }

  "Getting a Single Departure (Movement)" - {

    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "when a departure (movement) is found, should return a Right" in {
      val successResponse = MovementSummary(
        _id = MovementId("1234567890abcdef"),
        enrollmentEORINumber = EORINumber("GB123"),
        movementEORINumber = Some(EORINumber("GB456")),
        movementReferenceNumber = Some(MovementReferenceNumber("MRN001")),
        localReferenceNumber = Some(LocalReferenceNumber("LRN001")),
        created = now,
        updated = now,
        apiVersion = version
      )

      when(mockConnector.getMovement(EORINumber(any()), any(), MovementId(any()))(any(), any()))
        .thenReturn(Future.successful(successResponse))

      val result = sut.getMovement(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Right(successResponse)
      }
    }

    "when a departure is not found, should return DepartureNotFound" in {
      when(mockConnector.getMovement(EORINumber(any()), any(), MovementId(any()))(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMovement(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MovementNotFound(MovementId("1234567890abcdef"), MovementType.Departure))
      }
    }

    "on any other error, should return an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getMovement(EORINumber(any()), any(), MovementId(any()))(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getMovement(EORINumber("1"), MovementType.Departure, MovementId("1234567890abcdef"))
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
      }
    }

  }

  "Updating departure with departureId and messageType" - {

    val validRequest: Option[Source[ByteString, NotUsed]]   = Some(Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8)))
    val invalidRequest: Option[Source[ByteString, NotUsed]] = Some(Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8)))

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful submission, should return a Right" in {
      when(
        mockConnector.postMessage(MovementId(any[String]), any[Option[MessageType]], eqTo(validRequest), eqTo(version))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      )
        .thenReturn(Future.successful(UpdateMovementResponse(MessageId("123"))))
      val result = sut.addMessage(MovementId("abc"), MovementType.Departure, Some(MessageType.DeclarationInvalidationRequest), validRequest, version)
      val expected: Either[PersistenceError, UpdateMovementResponse] = Right(UpdateMovementResponse(MessageId("123")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a departure is not found, should return DepartureNotFound" in {
      when(
        mockConnector.postMessage(MovementId(any[String]), any[Option[MessageType]], eqTo(validRequest), eqTo(version))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result =
        sut.addMessage(MovementId("1234567890abcdef"), MovementType.Departure, Some(MessageType.DeclarationInvalidationRequest), validRequest, version)
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MovementNotFound(MovementId("1234567890abcdef"), MovementType.Departure))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      when(
        mockConnector.postMessage(MovementId(any[String]), any[Option[MessageType]], eqTo(invalidRequest), eqTo(version))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      )
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result = sut.addMessage(MovementId("abc"), MovementType.Departure, Some(MessageType.DeclarationInvalidationRequest), invalidRequest, version)
      val expected: Either[PersistenceError, UpdateMovementResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

  "Getting a list of Departures (Movement) by EORI" - {

    "when a departure (movement) is found and page is 1, then should return the movements" in forAll(
      Gen.listOfN(3, arbitrary[MovementSummary]),
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      arbitrary[EORINumber],
      (Gen.option(arbitrary[MovementReferenceNumber]), Gen.option(arbitrary[LocalReferenceNumber])),
      arbitrary[ItemCount]
    ) {
      (movementSummaries, updatedSinceMaybe, movementEORI, eori, referenceNumbers, count) =>
        val pageNumber = Some(PageNumber(1))
        val mrn        = referenceNumbers._1.sample.getOrElse(Some(MovementReferenceNumber("3CnsTh79I7vtOW1")))
        val lrn        = referenceNumbers._2.sample.getOrElse(Some(LocalReferenceNumber("3CnsTh79I7vtOW1")))

        val expected = PaginationMovementSummary(TotalCount(movementSummaries.length.toLong), movementSummaries)

        when(
          mockConnector.getMovements(
            eori,
            MovementType.Departure,
            updatedSinceMaybe,
            movementEORI,
            mrn,
            pageNumber,
            count,
            updatedSinceMaybe,
            lrn,
            version
          )
        )
          .thenReturn(Future.successful(expected))

        val result =
          sut.getMovements(
            eori,
            MovementType.Departure,
            updatedSinceMaybe,
            movementEORI,
            mrn,
            pageNumber,
            count,
            updatedSinceMaybe,
            lrn,
            version
          )
        whenReady(result.value) {
          _ mustBe Right(expected)
        }
    }

    "when a departure movement is not found for a given EORI and the page is greater than 1, should return PageNotFound" in forAll(
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      arbitrary[EORINumber],
      (Gen.option(arbitrary[MovementReferenceNumber]), Gen.option(arbitrary[LocalReferenceNumber])),
      arbitrary[ItemCount],
      Gen.option(arbitrary[OffsetDateTime])
    ) {
      (updatedSinceMaybe, movementEORI, eori, referenceNumbers, count, receivedUntil) =>
        // ensure page other than 1
        val pageNumber = Some(PageNumber(12))

        val mrn      = referenceNumbers._1.sample.getOrElse(Some(MovementReferenceNumber("3CnsTh79I7vtOW1")))
        val lrn      = referenceNumbers._2.sample.getOrElse(Some(LocalReferenceNumber("3CnsTh79I7vtOW1")))
        val expected = PaginationMovementSummary(TotalCount(0), List.empty[MovementSummary])

        when(
          mockConnector.getMovements(
            eori,
            MovementType.Departure,
            updatedSinceMaybe,
            movementEORI,
            mrn,
            pageNumber,
            count,
            receivedUntil,
            lrn,
            version
          )
        )
          .thenReturn(Future.successful(expected))

        val result =
          sut.getMovements(
            eori,
            MovementType.Departure,
            updatedSinceMaybe,
            movementEORI,
            mrn,
            pageNumber,
            count,
            receivedUntil,
            lrn,
            version
          )
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.PageNotFound)
        }
    }

    "when departure movements are not found for page 1 for a given EORI, should return an empty list" in {

      val movementEORI      = Some(EORINumber("GB1111111"))
      val eori              = EORINumber("GB2222222")
      val pageNumber        = Some(PageNumber(1))
      val itemCount         = ItemCount(15)
      val mrn               = Some(MovementReferenceNumber("3CnsTh79I7vtOW1"))
      val lrn               = Some(LocalReferenceNumber("2CnsTh79I7vtOW1"))
      val updatedSinceMaybe = Some(OffsetDateTime.now())
      val receivedUntil     = Some(OffsetDateTime.now())

      val expected = PaginationMovementSummary(TotalCount(0), List.empty[MovementSummary])

      when(
        mockConnector.getMovements(
          eori,
          MovementType.Departure,
          updatedSinceMaybe,
          movementEORI,
          mrn,
          pageNumber,
          itemCount,
          receivedUntil,
          lrn,
          version
        )
      )
        .thenReturn(Future.successful(expected))

      val result =
        sut.getMovements(
          eori,
          MovementType.Departure,
          updatedSinceMaybe,
          movementEORI,
          mrn,
          pageNumber,
          itemCount,
          receivedUntil,
          lrn,
          version
        )
      whenReady(result.value) {
        _ mustBe Right(expected)
      }
    }

    "when departure movements are not found for a page greater than 1 for given EORI, should return PageNotFound" in {

      val movementEORI      = Some(EORINumber("GB1111111"))
      val eori              = EORINumber("GB2222222")
      val pageNumber        = Some(PageNumber(2))
      val itemCount         = ItemCount(15)
      val mrn               = Some(MovementReferenceNumber("3CnsTh79I7vtOW1"))
      val lrn               = Some(LocalReferenceNumber("2CnsTh79I7vtOW1"))
      val updatedSinceMaybe = Some(OffsetDateTime.now())
      val receivedUntil     = Some(OffsetDateTime.now())

      val expected = PaginationMovementSummary(TotalCount(10), List.empty[MovementSummary])

      when(
        mockConnector.getMovements(
          eori,
          MovementType.Departure,
          updatedSinceMaybe,
          movementEORI,
          mrn,
          pageNumber,
          itemCount,
          receivedUntil,
          lrn,
          version
        )
      )
        .thenReturn(Future.successful(expected))

      val result =
        sut.getMovements(
          eori,
          MovementType.Departure,
          updatedSinceMaybe,
          movementEORI,
          mrn,
          pageNumber,
          itemCount,
          receivedUntil,
          lrn,
          version
        )
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.PageNotFound)
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in forAll(
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      arbitrary[EORINumber],
      (Gen.option(arbitrary[MovementReferenceNumber]), Gen.option(arbitrary[LocalReferenceNumber])),
      (Gen.option(arbitrary[PageNumber]), arbitrary[ItemCount]),
      Gen.option(arbitrary[OffsetDateTime])
    ) {
      (updatedSinceMaybe, movementEORI, eori, referenceNumbers, pagination, receivedUntil) =>
        val pageNumber = pagination._1.sample.getOrElse(Some(PageNumber(0)))
        val itemCount  = pagination._2.sample.value
        val error      = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
        val mrn        = referenceNumbers._1.sample.getOrElse(Some(MovementReferenceNumber("3CnsTh79I7vtOW1")))
        val lrn        = referenceNumbers._2.sample.getOrElse(Some(LocalReferenceNumber("3CnsTh79I7vtOW1")))
        when(
          mockConnector.getMovements(
            eori,
            MovementType.Departure,
            updatedSinceMaybe,
            movementEORI,
            mrn,
            pageNumber,
            itemCount,
            receivedUntil,
            lrn,
            version
          )
        )
          .thenReturn(Future.failed(error))

        val result =
          sut.getMovements(
            eori,
            MovementType.Departure,
            updatedSinceMaybe,
            movementEORI,
            mrn,
            pageNumber,
            itemCount,
            receivedUntil,
            lrn,
            version
          )
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
        }
    }
  }

  "Create Arrival notification" - {

    val validRequest: Source[ByteString, NotUsed]   = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
    val invalidRequest: Source[ByteString, NotUsed] = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful creation, should return a Right" in {
      when(mockConnector.postMovement(EORINumber(any[String]), any(), eqTo(Some(validRequest)), any())(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(MovementResponse(MovementId("ABC"), MessageId("123"))))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Arrival, Some(validRequest), version)
      val expected: Either[PersistenceError, MovementResponse] = Right(MovementResponse(MovementId("ABC"), MessageId("123")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed creation, should return a Left with an UnexpectedError" in {
      when(mockConnector.postMovement(EORINumber(any[String]), any(), eqTo(Some(invalidRequest)), any())(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Arrival, Some(invalidRequest), version)
      val expected: Either[PersistenceError, MovementResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

  "Create Arrival notification for Large Messages" - {

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful creation, should return a Right" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      when(mockConnector.postMovement(EORINumber(any[String]), any(), any(), any())(eqTo(hc), any[ExecutionContext]))
        .thenReturn(Future.successful(MovementResponse(MovementId("ABC"), MessageId("1234567890abcdsd"))))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Arrival, None, version)
      val expected: Either[PersistenceError, MovementResponse] = Right(MovementResponse(MovementId("ABC"), MessageId("1234567890abcdsd")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on a failed creation, should return a Left with an UnexpectedError" in {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      when(mockConnector.postMovement(EORINumber(any[String]), any(), any(), any())(eqTo(hc), any[ExecutionContext]))
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result                                               = sut.createMovement(EORINumber("1"), MovementType.Arrival, None, version)
      val expected: Either[PersistenceError, MovementResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

  "Getting a list of Arrival message IDs" - {

    "when an arrival is found, should return a Right of the sequence of message IDs for any page number" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.listOfN(3, arbitrary[MessageSummary]),
      Gen.option(arbitrary[PageNumber]),
      arbitrary[ItemCount]
    ) {
      (eori, arrivalId, receivedSince, summary, pageNumber, itemCount) =>
        val expected = PaginationMessageSummary(TotalCount(summary.length.toLong), summary)

        val page = Some(pageNumber.getOrElse(PageNumber(1)))

        when(
          mockConnector.getMessages(
            EORINumber(any()),
            any(),
            MovementId(any()),
            any(),
            eqTo(page),
            ItemCount(eqTo(itemCount.value)),
            eqTo(receivedSince),
            any()
          )(
            any(),
            any()
          )
        )
          .thenReturn(Future.successful(expected))

        val result = sut.getMessages(eori, MovementType.Arrival, arrivalId, receivedSince, page, itemCount, receivedSince, version)
        whenReady(result.value) {
          _ mustBe Right(expected)
        }
    }

    "when a given arrival is not found and the page is 1, should return a MovementNotFound" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      Gen.option(arbitrary[OffsetDateTime]),
      arbitrary[ItemCount]
    ) {
      (eori, arrivalId, receivedSince, itemCount) =>
        val pageNumber = Some(PageNumber(1))

        when(
          mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any(), eqTo(pageNumber), ItemCount(eqTo(itemCount.value)), any(), any())(
            any(),
            any()
          )
        )
          .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

        val result = sut.getMessages(eori, MovementType.Arrival, arrivalId, receivedSince, pageNumber, itemCount, receivedSince, version)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.MovementNotFound(arrivalId, MovementType.Arrival))
        }
    }

    "when a given arrival is not found and the page is greater than 1, should return a MovementNotFound" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      Gen.option(arbitrary[OffsetDateTime]),
      arbitrary[ItemCount]
    ) {
      (eori, arrivalId, receivedSince, itemCount) =>
        val pageNumber = Some(PageNumber(12))

        when(
          mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any(), eqTo(pageNumber), ItemCount(eqTo(itemCount.value)), any(), any())(
            any(),
            any()
          )
        )
          .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

        val result = sut.getMessages(eori, MovementType.Arrival, arrivalId, receivedSince, pageNumber, itemCount, receivedSince, version)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.MovementNotFound(arrivalId, MovementType.Arrival))
        }
    }

    "when a given arrival is found and the page is greater than 1 and empty, should return a PageNotFound" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      Gen.option(arbitrary[OffsetDateTime]),
      arbitrary[ItemCount]
    ) {
      (eori, arrivalId, receivedSince, itemCount) =>
        val pageNumber = Some(PageNumber(12))
        val summary    = PaginationMessageSummary(TotalCount(0), List.empty[MessageSummary])

        when(
          mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any(), eqTo(pageNumber), ItemCount(eqTo(itemCount.value)), any(), any())(
            any(),
            any()
          )
        )
          .thenReturn(Future.successful(summary))

        val result = sut.getMessages(eori, MovementType.Arrival, arrivalId, receivedSince, pageNumber, itemCount, receivedSince, version)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.PageNotFound)
        }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[PageNumber]),
      arbitrary[ItemCount]
    ) {
      (eori, arrivalId, receivedSince, pageNumber, itemCount) =>
        val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
        when(
          mockConnector.getMessages(EORINumber(any()), any(), MovementId(any()), any(), eqTo(pageNumber), ItemCount(eqTo(itemCount.value)), any(), any())(
            any(),
            any()
          )
        )
          .thenReturn(Future.failed(error))

        val result = sut.getMessages(eori, MovementType.Arrival, arrivalId, receivedSince, pageNumber, itemCount, receivedSince, version)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
        }
    }

  }

  "Getting a list of Arrivals (Movement) by EORI" - {

    "when an arrival (movement) is found, then should return the movements, regardless of the page selected" in forAll(
      Gen.listOfN(3, arbitrary[MovementSummary]),
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      arbitrary[EORINumber],
      (Gen.option(arbitrary[MovementReferenceNumber]), Gen.option(arbitrary[LocalReferenceNumber])),
      (Gen.option(arbitrary[PageNumber]), arbitrary[ItemCount])
    ) {
      (summaries, updatedSinceMaybe, movementEORI, eori, referenceNumbers, pagination) =>
        val pageNumber = pagination._1.sample.getOrElse(Some(PageNumber(1))) match {
          case None => Some(PageNumber(1))
          case page => page
        }
        val itemCount               = pagination._2.sample.value
        val movementReferenceNumber = referenceNumbers._1.sample.getOrElse(Some(MovementReferenceNumber("3CnsTh79I7vtOW1")))
        val localReferenceNumber    = referenceNumbers._2.sample.getOrElse(Some(LocalReferenceNumber("3CnsTh79I7vtOW1")))
        val expected                = PaginationMovementSummary(TotalCount(100), summaries)
        when(
          mockConnector.getMovements(
            eori,
            MovementType.Arrival,
            updatedSinceMaybe,
            movementEORI,
            movementReferenceNumber,
            pageNumber,
            itemCount,
            updatedSinceMaybe,
            localReferenceNumber,
            version
          )
        )
          .thenReturn(Future.successful(expected))

        val result =
          sut.getMovements(
            eori,
            MovementType.Arrival,
            updatedSinceMaybe,
            movementEORI,
            movementReferenceNumber,
            pageNumber,
            itemCount,
            updatedSinceMaybe,
            localReferenceNumber,
            version
          )
        whenReady(result.value) {
          _ mustBe Right(expected)
        }
    }

    "when an arrival is not found and page is 1, should return empty list" in forAll(
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      arbitrary[EORINumber],
      (Gen.option(arbitrary[MovementReferenceNumber]), Gen.option(arbitrary[LocalReferenceNumber])),
      arbitrary[ItemCount],
      Gen.option(arbitrary[OffsetDateTime])
    ) {
      (updatedSinceMaybe, movementEORI, eori, referenceNumbers, itemCount, receivedUntil) =>
        val pageNumber              = Some(PageNumber(1))
        val movementReferenceNumber = referenceNumbers._1.sample.getOrElse(Some(MovementReferenceNumber("3CnsTh79I7vtOW1")))
        val localReferenceNumber    = referenceNumbers._2.sample.getOrElse(Some(LocalReferenceNumber("3CnsTh79I7vtOW1")))

        val expected = PaginationMovementSummary(TotalCount(0), List.empty[MovementSummary])
        when(
          mockConnector.getMovements(
            eori,
            MovementType.Arrival,
            updatedSinceMaybe,
            movementEORI,
            movementReferenceNumber,
            pageNumber,
            itemCount,
            receivedUntil,
            localReferenceNumber,
            version
          )
        )
          .thenReturn(Future.successful(expected))

        val result =
          sut.getMovements(
            eori,
            MovementType.Arrival,
            updatedSinceMaybe,
            movementEORI,
            movementReferenceNumber,
            pageNumber,
            itemCount,
            receivedUntil,
            localReferenceNumber,
            version
          )
        whenReady(result.value) {
          _ mustBe Right(expected)
        }
    }

    "when an arrival is not found and page is None, should return empty list" in forAll(
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      arbitrary[EORINumber],
      (Gen.option(arbitrary[MovementReferenceNumber]), Gen.option(arbitrary[LocalReferenceNumber])),
      arbitrary[ItemCount],
      Gen.option(arbitrary[OffsetDateTime])
    ) {
      (updatedSinceMaybe, movementEORI, eori, referenceNumbers, itemCount, receivedUntil) =>
        val pageNumber              = None
        val movementReferenceNumber = referenceNumbers._1.sample.getOrElse(Some(MovementReferenceNumber("3CnsTh79I7vtOW1")))
        val localReferenceNumber    = referenceNumbers._2.sample.getOrElse(Some(LocalReferenceNumber("3CnsTh79I7vtOW1")))

        val expected = PaginationMovementSummary(TotalCount(0), List.empty[MovementSummary])
        when(
          mockConnector.getMovements(
            eori,
            MovementType.Arrival,
            updatedSinceMaybe,
            movementEORI,
            movementReferenceNumber,
            pageNumber,
            itemCount,
            receivedUntil,
            localReferenceNumber,
            version
          )
        )
          .thenReturn(Future.successful(expected))

        val result =
          sut.getMovements(
            eori,
            MovementType.Arrival,
            updatedSinceMaybe,
            movementEORI,
            movementReferenceNumber,
            pageNumber,
            itemCount,
            receivedUntil,
            localReferenceNumber,
            version
          )
        whenReady(result.value) {
          _ mustBe Right(expected)
        }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in forAll(
      Gen.option(arbitrary[OffsetDateTime]),
      Gen.option(arbitrary[EORINumber]),
      arbitrary[EORINumber],
      (Gen.option(arbitrary[MovementReferenceNumber]), Gen.option(arbitrary[LocalReferenceNumber])),
      (Gen.option(arbitrary[PageNumber]), arbitrary[ItemCount]),
      Gen.option(arbitrary[OffsetDateTime])
    ) {
      (updatedSinceMaybe, movementEORI, eori, referenceNumbers, pagination, receivedUntil) =>
        val pageNumber              = pagination._1.sample.getOrElse(Some(PageNumber(1)))
        val itemCount               = pagination._2.sample.value
        val error                   = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
        val movementReferenceNumber = referenceNumbers._1.sample.getOrElse(Some(MovementReferenceNumber("3CnsTh79I7vtOW1")))
        val localReferenceNumber    = referenceNumbers._2.sample.getOrElse(Some(LocalReferenceNumber("3CnsTh79I7vtOW1")))

        when(
          mockConnector.getMovements(
            eori,
            MovementType.Arrival,
            updatedSinceMaybe,
            movementEORI,
            movementReferenceNumber,
            pageNumber,
            itemCount,
            receivedUntil,
            localReferenceNumber,
            version
          )
        )
          .thenReturn(Future.failed(error))

        val result =
          sut.getMovements(
            eori,
            MovementType.Arrival,
            updatedSinceMaybe,
            movementEORI,
            movementReferenceNumber,
            pageNumber,
            itemCount,
            receivedUntil,
            localReferenceNumber,
            version
          )
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
        }
    }

  }

  "Getting a Single Arrival Message" - {

    val now = OffsetDateTime.now(ZoneOffset.UTC)

    "when a message is found, should return a Right" in {
      val successResponse = MessageSummary(
        MessageId("1234567890abcdef"),
        now,
        Some(MessageType.ArrivalNotification),
        Some(XmlPayload("<test></test>")),
        Some(MessageStatus.Success),
        None
      )

      when(mockConnector.getMessage(EORINumber(any()), any(), MovementId(any()), MessageId(any()), any())(any(), any()))
        .thenReturn(Future.successful(successResponse))

      val result = sut.getMessage(EORINumber("1"), MovementType.Arrival, MovementId("1234567890abcdef"), MessageId("1234567890abcdef"), version)
      whenReady(result.value) {
        _ mustBe Right(successResponse)
      }
    }

    "when a message is not found, should return a Left with an MessageNotFound" in {
      when(mockConnector.getMessage(EORINumber(any()), any(), MovementId(any()), MessageId(any()), any())(any(), any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.getMessage(EORINumber("1"), MovementType.Arrival, MovementId("1234567890abcdef"), MessageId("1234567890abcdef"), version)
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MessageNotFound(MovementId("1234567890abcdef"), MessageId("1234567890abcdef")))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
      when(mockConnector.getMessage(EORINumber(any()), any(), MovementId(any()), MessageId(any()), any())(any(), any()))
        .thenReturn(Future.failed(error))

      val result = sut.getMessage(EORINumber("1"), MovementType.Arrival, MovementId("1234567890abcdef"), MessageId("1234567890abcdef"), version)
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
      }
    }

  }

  "Updating arrival with arrivalId and messageType" - {

    val validRequest: Option[Source[ByteString, NotUsed]]   = Some(Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8)))
    val invalidRequest: Option[Source[ByteString, NotUsed]] = Some(Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8)))

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful submission, should return a Right" in {
      when(
        mockConnector.postMessage(MovementId(any[String]), any[Option[MessageType]], eqTo(validRequest), any[Version])(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      )
        .thenReturn(Future.successful(UpdateMovementResponse(MessageId("1234567890abcdsd"))))
      val result = sut.addMessage(MovementId("1234567890abcdef"), MovementType.Arrival, Some(MessageType.UnloadingRemarks), validRequest, version)
      val expected: Either[PersistenceError, UpdateMovementResponse] = Right(UpdateMovementResponse(MessageId("1234567890abcdsd")))
      whenReady(result.value) {
        _ mustBe expected
      }
    }

    "on an arrival is not found, should return ArrivalNotFound" in {
      when(
        mockConnector.postMessage(MovementId(any[String]), any[Option[MessageType]], eqTo(validRequest), any[Version])(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

      val result = sut.addMessage(MovementId("1234567890abcdef"), MovementType.Arrival, Some(MessageType.UnloadingRemarks), validRequest, version)
      whenReady(result.value) {
        _ mustBe Left(PersistenceError.MovementNotFound(MovementId("1234567890abcdef"), MovementType.Arrival))
      }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in {
      when(
        mockConnector.postMessage(MovementId(any[String]), any[Option[MessageType]], eqTo(invalidRequest), any[Version])(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      )
        .thenReturn(Future.failed(upstreamErrorResponse))
      val result = sut.addMessage(MovementId("1234567890abcdef"), MovementType.Arrival, Some(MessageType.UnloadingRemarks), invalidRequest, version)
      val expected: Either[PersistenceError, UpdateMovementResponse] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
      whenReady(result.value) {
        _ mustBe expected
      }
    }
  }

  "Updating message" - {

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)

    "on a successful update, should return a Right" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementType],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[MessageUpdate]
    ) {
      (eoriNumber, movementType, movementId, messageId, messageUpdate) =>
        when(
          mockConnector.patchMessage(
            any[String].asInstanceOf[EORINumber],
            any[String].asInstanceOf[MovementType],
            any[String].asInstanceOf[MovementId],
            any[String].asInstanceOf[MessageId],
            eqTo(messageUpdate),
            eqTo(version)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        )
          .thenReturn(Future.successful(()))
        val result = sut.updateMessage(eoriNumber, movementType, movementId, messageId, messageUpdate, version)

        val expected: Either[PersistenceError, Unit] = Right(())
        whenReady(result.value) {
          _ mustBe expected
        }
    }

    "on a message is not found, should return MessageNotFound" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementType],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[MessageUpdate]
    ) {
      (eoriNumber, movementType, movementId, messageId, messageUpdate) =>
        when(
          mockConnector.patchMessage(
            any[String].asInstanceOf[EORINumber],
            any[String].asInstanceOf[MovementType],
            any[String].asInstanceOf[MovementId],
            any[String].asInstanceOf[MessageId],
            eqTo(messageUpdate),
            eqTo(version)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        ).thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

        val result = sut.updateMessage(eoriNumber, movementType, movementId, messageId, messageUpdate, version)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.MessageNotFound(movementId, messageId))
        }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementType],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[MessageUpdate]
    ) {
      (eoriNumber, movementType, movementId, messageId, messageUpdate) =>
        when(
          mockConnector.patchMessage(
            any[String].asInstanceOf[EORINumber],
            any[String].asInstanceOf[MovementType],
            any[String].asInstanceOf[MovementId],
            any[String].asInstanceOf[MessageId],
            eqTo(messageUpdate),
            eqTo(version)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        )
          .thenReturn(Future.failed(upstreamErrorResponse))
        val result                                   = sut.updateMessage(eoriNumber, movementType, movementId, messageId, messageUpdate, version)
        val expected: Either[PersistenceError, Unit] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
        whenReady(result.value) {
          _ mustBe expected
        }
    }
  }

  "updateMessageBody" - {
    val validRequest: Source[ByteString, NotUsed]   = Source.single(ByteString(<schemaValid></schemaValid>.mkString, StandardCharsets.UTF_8))
    val invalidRequest: Source[ByteString, NotUsed] = Source.single(ByteString(<schemaInvalid></schemaInvalid>.mkString, StandardCharsets.UTF_8))

    val upstreamErrorResponse: Throwable = UpstreamErrorResponse("Internal service error", INTERNAL_SERVER_ERROR)
    "return a successful result when the persistence connector successfully updates the message body" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementType],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[MessageType]
    ) {
      (eoriNumber, movementType, movementId, messageId, messageType) =>
        when(
          mockConnector.updateMessageBody(
            any[String].asInstanceOf[MessageType],
            any[String].asInstanceOf[EORINumber],
            any[String].asInstanceOf[MovementType],
            any[String].asInstanceOf[MovementId],
            any[String].asInstanceOf[MessageId],
            eqTo(validRequest),
            eqTo(version)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        )
          .thenReturn(Future.successful(()))

        val result = sut.updateMessageBody(messageType, eoriNumber, movementType, movementId, messageId, validRequest, version)

        val expected: Either[PersistenceError, Unit] = Right(())
        whenReady(result.value) {
          _ mustBe expected
        }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementType],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[MessageType]
    ) {
      (eoriNumber, movementType, movementId, messageId, messageType) =>
        when(
          mockConnector.updateMessageBody(
            any[String].asInstanceOf[MessageType],
            any[String].asInstanceOf[EORINumber],
            any[String].asInstanceOf[MovementType],
            any[String].asInstanceOf[MovementId],
            any[String].asInstanceOf[MessageId],
            eqTo(invalidRequest),
            eqTo(version)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        )
          .thenReturn(Future.failed(upstreamErrorResponse))
        val result = sut.updateMessageBody(messageType, eoriNumber, movementType, movementId, messageId, invalidRequest, version)
        val expected: Either[PersistenceError, Unit] = Left(PersistenceError.UnexpectedError(Some(upstreamErrorResponse)))
        whenReady(result.value) {
          _ mustBe expected
        }
    }
  }

  "Getting a message body" - {

    "when a message is found, should return a Right" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementType],
      arbitrary[MovementId],
      arbitrary[MessageId]
    ) {
      (eori, movementType, movementId, messageId) =>
        val source = Source.single(ByteString("test"))

        when(
          mockConnector.getMessageBody(
            EORINumber(eqTo(eori.value)),
            eqTo(movementType),
            MovementId(eqTo(movementId.value)),
            MessageId(eqTo(messageId.value)),
            eqTo(version)
          )(
            any(),
            any(),
            any()
          )
        )
          .thenReturn(Future.successful(source))

        val result = sut.getMessageBody(eori, movementType, movementId, messageId, version)
        whenReady(result.value) {
          _ mustBe Right(source)
        }
    }

    "when a message is not found, should return a Left with an MessageNotFound" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementType],
      arbitrary[MovementId],
      arbitrary[MessageId]
    ) {
      (eori, movementType, movementId, messageId) =>
        when(
          mockConnector.getMessageBody(
            EORINumber(eqTo(eori.value)),
            eqTo(movementType),
            MovementId(eqTo(movementId.value)),
            MessageId(eqTo(messageId.value)),
            eqTo(version)
          )(
            any(),
            any(),
            any()
          )
        )
          .thenReturn(Future.failed(UpstreamErrorResponse("not found", NOT_FOUND)))

        val result = sut.getMessageBody(eori, movementType, movementId, messageId, version)
        whenReady(result.value) {
          _ mustBe Left(PersistenceError.MessageNotFound(movementId, messageId))
        }
    }

    "on a failed submission, should return a Left with an UnexpectedError" in
      forAll(
        arbitrary[EORINumber],
        arbitrary[MovementType],
        arbitrary[MovementId],
        arbitrary[MessageId]
      ) {
        (eori, movementType, movementId, messageId) =>
          val error = UpstreamErrorResponse("error", INTERNAL_SERVER_ERROR)
          when(
            mockConnector.getMessageBody(
              EORINumber(eqTo(eori.value)),
              eqTo(movementType),
              MovementId(eqTo(movementId.value)),
              MessageId(eqTo(messageId.value)),
              eqTo(version)
            )(any(), any(), any())
          )
            .thenReturn(Future.failed(error))

          val result = sut.getMessageBody(eori, movementType, movementId, messageId, version)
          whenReady(result.value) {
            _ mustBe Left(PersistenceError.UnexpectedError(thr = Some(error)))
          }
      }

  }
}
