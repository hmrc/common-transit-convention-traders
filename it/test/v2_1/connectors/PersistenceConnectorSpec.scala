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

package v2_1.connectors

import com.codahale.metrics.MetricRegistry
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import com.fasterxml.jackson.core.JsonParseException
import com.github.tomakehurst.wiremock.client.WireMock._
import config.AppConfig
import config.Constants
import models.common.MessageId
import models.common.MovementId
import models.common.MovementType
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.exceptions.TestFailedException
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.ContentTypes
import play.api.http.HeaderNames
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.NOT_FOUND
import play.api.http.Status.OK
import play.api.libs.json.JsResult
import play.api.libs.json.JsSuccess
import play.api.libs.json.Json
import play.mvc.Http.MimeTypes
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.http.test.HttpClientV2Support
import utils.GuiceWiremockSuite
import models.common.EORINumber
import models.common.ItemCount
import models.common.LocalReferenceNumber
import v2_1.models.MessageStatus
import models.common.MovementReferenceNumber
import models.common.PageNumber
import v2_1.models.TotalCount
import models.common.errors.ErrorCode
import models.common.errors.PresentationError
import models.common.errors.StandardError
import v2_1.models.request.MessageType
import v2_1.models.request.MessageUpdate
import v2_1.models.responses.MessageSummary
import v2_1.models.responses.MovementResponse
import v2_1.models.responses.PaginationMessageSummary
import v2_1.models.responses.PaginationMovementSummary
import v2_1.models.responses.UpdateMovementResponse
import v2_1.utils.CommonGenerators

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import play.api.http.Status.CREATED
import v2_1.base.TestActorSystem

class PersistenceConnectorSpec
    extends AnyFreeSpec
    with HttpClientV2Support
    with Matchers
    with GuiceOneAppPerSuite
    with GuiceWiremockSuite
    with ScalaFutures
    with IntegrationPatience
    with ScalaCheckDrivenPropertyChecks
    with TestActorSystem
    with CommonGenerators {

  private val token: String = Gen.alphaNumStr.sample.get

  override val configurationOverride: Seq[(String, String)] =
    Seq(
      "internal-auth.token" -> token
    )

  implicit lazy val appConfig: AppConfig                  = app.injector.instanceOf[AppConfig]
  lazy val messageType                                    = MessageType.DeclarationAmendment
  lazy val persistenceConnector: PersistenceConnectorImpl = new PersistenceConnectorImpl(httpClientV2, new MetricRegistry)
  implicit lazy val ec: ExecutionContext                  = app.materializer.executionContext

  val defaultFilterParams = "?page=1&count=25"

  "POST /traders/:eori/movements/departures" - {

    def targetUrl(eoriNumber: EORINumber) = s"/transit-movements/traders/${eoriNumber.value}/movements/departures"

    "On successful creation of an element, must return OK" in forAll(
      arbitrary[EORINumber],
      arbitraryMovementResponse().arbitrary,
      Gen.stringOfN(15, Gen.alphaNumChar)
    ) {
      (eoriNumber, movementResponse, clientId) =>
        server.resetAll()

        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(movementResponse)))
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))

        whenReady(persistenceConnector.postMovement(eoriNumber, MovementType.Departure, Some(source))) {
          result =>
            result mustBe movementResponse
            server.verify(
              1,
              postRequestedFor(urlEqualTo(targetUrl(eoriNumber)))
                .withHeader("Content-Type", equalTo(MimeTypes.XML))
                .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            );
        }
    }

    "On an upstream internal server error, get a UpstreamErrorResponse" in forAll(arbitrary[EORINumber], Gen.stringOfN(15, Gen.alphaNumChar)) {
      (eoriNumber, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.internalServiceError()))
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8))

        val future = persistenceConnector.postMovement(eoriNumber, MovementType.Departure, Some(source)).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          case Left(UpstreamErrorResponse(message, INTERNAL_SERVER_ERROR, _, _)) =>
            Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
          case other => fail(s"Expected internal server error, got $other")
        }
    }

    "On an upstream bad request, get an UpstreamErrorResponse" in forAll(arbitrary[EORINumber], Gen.stringOfN(15, Gen.alphaNumChar)) {
      (eoriNumber, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.badRequestError("Bad request")))
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8))

        val future = persistenceConnector.postMovement(eoriNumber, MovementType.Departure, Some(source)).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          case Left(UpstreamErrorResponse(message, BAD_REQUEST, _, _)) =>
            Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Bad request", ErrorCode.BadRequest))
          case other => fail(s"Expected bad request error, got $other")
        }
    }

    "On an incorrect Json fragment, must return a JsResult.Exception" in forAll(arbitrary[EORINumber], Gen.stringOfN(15, Gen.alphaNumChar)) {
      (eoriNumber, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  "{ hello"
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))

        val future = persistenceConnector.postMovement(eoriNumber, MovementType.Departure, Some(source)).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.toOption.get mustBe a[JsonParseException]
        }
    }
  }

  "POST /traders/:eori/movements/departures for Large Messages" - {

    lazy val okResultGen =
      for {
        movementId <- arbitrary[MovementId]
        messageId  <- arbitrary[MessageId]
      } yield MovementResponse(movementId, messageId)

    def targetUrl(eoriNumber: EORINumber) = s"/transit-movements/traders/${eoriNumber.value}/movements/departures"

    "On successful creation of an element, must return OK" in forAll(arbitrary[EORINumber], okResultGen, Gen.stringOfN(15, Gen.alphaNumChar)) {
      (eoriNumber, okResult, clientId) =>
        server.resetAll()

        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(okResult)))
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        whenReady(persistenceConnector.postMovement(eoriNumber, MovementType.Departure, None)) {
          result =>
            result mustBe okResult
            server.verify(
              0,
              postRequestedFor(urlEqualTo(targetUrl(eoriNumber)))
                .withHeader("Content-Type", equalTo(MimeTypes.XML))
            );
        }
    }

    "On an upstream internal server error, get a UpstreamErrorResponse" in forAll(arbitrary[EORINumber], Gen.stringOfN(15, Gen.alphaNumChar)) {
      (eoriNumber, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.internalServiceError()))
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val future = persistenceConnector.postMovement(eoriNumber, MovementType.Departure, None).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          case Left(UpstreamErrorResponse(message, INTERNAL_SERVER_ERROR, _, _)) =>
            Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
          case other => fail(s"Expected internal server error, got $other")
        }
    }

    "On an upstream bad request, get an UpstreamErrorResponse" in forAll(arbitrary[EORINumber], Gen.stringOfN(15, Gen.alphaNumChar)) {
      (eoriNumber, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.badRequestError("Bad request")))
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val future = persistenceConnector.postMovement(eoriNumber, MovementType.Departure, None).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          case Left(UpstreamErrorResponse(message, BAD_REQUEST, _, _)) =>
            Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Bad request", ErrorCode.BadRequest))
          case other => fail(s"Expected bad request error, got $other")
        }
    }

    "On an incorrect Json fragment, must return a JsResult.Exception" in forAll(arbitrary[EORINumber], Gen.stringOfN(15, Gen.alphaNumChar)) {
      (eoriNumber, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  "{ hello"
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val future = persistenceConnector.postMovement(eoriNumber, MovementType.Departure, None).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.toOption.get mustBe a[JsonParseException]
        }
    }
  }

  "GET /traders/:eori/movements/departure/:departureid/messages/:message" - {

    def targetUrl(eoriNumber: EORINumber, departureId: MovementId, messageId: MessageId) =
      s"/transit-movements/traders/${eoriNumber.value}/movements/departures/${departureId.value}/messages/${messageId.value}"

    "on successful message, return a success" in {
      val eori           = arbitrary[EORINumber].sample.get
      val departureId    = arbitrary[MovementId].sample.get
      val messageSummary = arbitraryMessageSummary.arbitrary.sample.get

      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori, departureId, messageSummary.id))
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json.stringify(Json.toJson(messageSummary))
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val result      = persistenceConnector.getMessage(eori, MovementType.Departure, departureId, messageSummary.id)
      whenReady(result) {
        _ mustBe messageSummary
      }
    }

    "on incorrect Json, return an error" in {

      val eori        = arbitrary[EORINumber].sample.get
      val departureId = arbitrary[MovementId].sample.get
      val messageId   = arbitrary[MessageId].sample.get
      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori, departureId, messageId))
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                "{ \"test\": \"fail\" }"
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val r = persistenceConnector
        .getMessage(eori, MovementType.Departure, departureId, messageId)
        .map(
          _ => fail("This should have failed with a JsResult.Exception, but it succeeded")
        )
        .recover {
          case JsResult.Exception(_)  => ()
          case t: TestFailedException => t
          case thr                    => fail(s"Expected a JsResult.Exception, got $thr")
        }

      whenReady(r) {
        _ =>
      }

    }

    "on not found, return an UpstreamServerError" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      arbitrary[MessageId]
    ) {
      (eori, departureId, messageId) =>
        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori, departureId, messageId))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withBody(
                  Json.stringify(
                    Json.obj(
                      "code"    -> "NOT_FOUND",
                      "message" -> "not found"
                    )
                  )
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val r = persistenceConnector
          .getMessage(eori, MovementType.Departure, departureId, messageId)
          .map(
            _ => fail("This should have failed with an UpstreamErrorResponse, but it succeeded")
          )
          .recover {
            case UpstreamErrorResponse(_, NOT_FOUND, _, _) => ()
            case thr                                       => fail(s"Expected an UpstreamErrorResponse with a 404, got $thr")
          }

        whenReady(r) {
          _ =>
        }
    }

    "on an internal error, return an UpstreamServerError" in {
      val eori        = arbitrary[EORINumber].sample.get
      val departureId = arbitrary[MovementId].sample.get
      val messageId   = arbitrary[MessageId].sample.get

      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori, departureId, messageId))
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withBody(
                Json.stringify(
                  Json.obj(
                    "code"    -> "INTERNAL_SERVER_ERROR",
                    "message" -> "Internal server error"
                  )
                )
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val r = persistenceConnector
        .getMessage(eori, MovementType.Departure, departureId, messageId)
        .map(
          _ => fail("This should have failed with an UpstreamErrorResponse, but it succeeded")
        )
        .recover {
          case UpstreamErrorResponse(_, INTERNAL_SERVER_ERROR, _, _) => ()
          case thr                                                   => fail(s"Expected an UpstreamErrorResponse with a 500, got $thr")
        }

      whenReady(r) {
        _ =>
      }
    }

  }

  "GET /traders/:eori/movements/departure/:departureid/messages" - {
    lazy val messageIdList = Gen.listOfN(3, arbitrary[MessageId])

    def targetUrl(eoriNumber: EORINumber, departureId: MovementId) =
      s"/transit-movements/traders/${eoriNumber.value}/movements/departures/${departureId.value}/messages"

    def targetUrlWithTime(eoriNumber: EORINumber, departureId: MovementId, receivedSince: OffsetDateTime) =
      s"/transit-movements/traders/${eoriNumber.value}/movements/departures/${departureId.value}/messages?receivedSince=${DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .format(receivedSince)}"

    "on successful return of message IDs when no filtering is applied, return a success" in {
      val eori        = arbitrary[EORINumber].sample.get
      val departureId = arbitrary[MovementId].sample.get
      val messageSummaryList = messageIdList.sample.get.map(
        id => arbitraryMessageSummary.arbitrary.sample.get.copy(id = id)
      )

      val paginationMessageSummary = PaginationMessageSummary(TotalCount(messageSummaryList.length.longValue), messageSummaryList)

      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori, departureId) + defaultFilterParams)
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json.toJson(paginationMessageSummary).toString()
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val result      = persistenceConnector.getMessages(eori, MovementType.Departure, departureId, None, None, ItemCount(25), None)
      whenReady(result) {
        _ mustBe paginationMessageSummary
      }
    }

    "on successful return of message IDs when filtering by received date is applied, return a success" in {
      val eori        = arbitrary[EORINumber].sample.get
      val departureId = arbitrary[MovementId].sample.get
      val time        = OffsetDateTime.now(ZoneOffset.UTC)
      val messageSummaryList = messageIdList.sample.get.map(
        id => arbitraryMessageSummary.arbitrary.sample.get.copy(id = id)
      )

      val paginationMessageSummary = PaginationMessageSummary(TotalCount(messageSummaryList.length.longValue), messageSummaryList)

      server.stubFor(
        get(
          urlEqualTo(targetUrlWithTime(eori, departureId, time) + "&page=1&count=25")
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json.toJson(paginationMessageSummary).toString()
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val result      = persistenceConnector.getMessages(eori, MovementType.Departure, departureId, Some(time), None, ItemCount(25), None)
      whenReady(result) {
        _ mustBe paginationMessageSummary
      }
    }

    "on incorrect Json, return an error" in {

      val eori        = arbitrary[EORINumber].sample.get
      val departureId = arbitrary[MovementId].sample.get
      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori, departureId) + defaultFilterParams)
        )
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                "{ \"test\": \"fail\" }"
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val r = persistenceConnector
        .getMessages(eori, MovementType.Departure, departureId, None, None, ItemCount(25), None)
        .map(
          _ => fail("This should have failed with a JsResult.Exception, but it succeeded")
        )
        .recover {
          case JsResult.Exception(_)  => ()
          case t: TestFailedException => t
          case thr                    => fail(s"Expected a JsResult.Exception, got $thr")
        }

      whenReady(r) {
        _ =>
      }

    }

    "on not found, return an UpstreamServerError" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId]
    ) {
      (eori, departureId) =>
        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori, departureId) + defaultFilterParams)
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withBody(
                  Json.stringify(
                    Json.obj(
                      "code"    -> "NOT_FOUND",
                      "message" -> "not found"
                    )
                  )
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val r = persistenceConnector
          .getMessages(eori, MovementType.Departure, departureId, None, None, ItemCount(25), None)
          .map(
            _ => fail("This should have failed with an UpstreamErrorResponse, but it succeeded")
          )
          .recover {
            case UpstreamErrorResponse(_, NOT_FOUND, _, _) => ()
            case thr                                       => fail(s"Expected an UpstreamErrorResponse with a 404, got $thr")
          }

        whenReady(r) {
          _ =>
        }
    }

    "on an internal error, return an UpstreamServerError" in {
      val eori        = arbitrary[EORINumber].sample.get
      val departureId = arbitrary[MovementId].sample.get

      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori, departureId) + defaultFilterParams)
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withBody(
                Json.stringify(
                  Json.obj(
                    "code"    -> "INTERNAL_SERVER_ERROR",
                    "message" -> "Internal server error"
                  )
                )
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val r = persistenceConnector
        .getMessages(eori, MovementType.Departure, departureId, None, None, ItemCount(25), None)
        .map(
          _ => fail("This should have failed with an UpstreamErrorResponse, but it succeeded")
        )
        .recover {
          case UpstreamErrorResponse(_, INTERNAL_SERVER_ERROR, _, _) => ()
          case thr                                                   => fail(s"Expected an UpstreamErrorResponse with a 500, got $thr")
        }

      whenReady(r) {
        _ =>
      }
    }

  }

  "GET /traders/movements/departures/:departureId" - {

    implicit val hc          = HeaderCarrier()
    lazy val movementSummary = arbitraryMovementSummary.arbitrary.sample.get

    lazy val targetUrl = s"/transit-movements/traders/${movementSummary.enrollmentEORINumber.value}/movements/departures/${movementSummary._id.value}"

    "on success, return a MovementResponse" in {

      server.stubFor(
        get(
          urlEqualTo(targetUrl)
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json.toJson(movementSummary).toString()
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val result      = persistenceConnector.getMovement(movementSummary.enrollmentEORINumber, MovementType.Departure, movementSummary._id)
      whenReady(result) {
        _ mustBe movementSummary
      }
    }

    "on incorrect Json, return an error" in {
      server.stubFor(
        get(
          urlEqualTo(targetUrl)
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                "{ \"test\": \"fail\" }"
              )
          )
      )

      val result = persistenceConnector
        .getMovement(movementSummary.enrollmentEORINumber, MovementType.Departure, movementSummary._id)
        .map(
          _ => fail("This should have failed with a JsResult.Exception, but it succeeded")
        )
        .recover {
          case JsResult.Exception(_) => ()
          case thr                   => fail(s"Expected a JsResult.Exception, got $thr")
        }

      whenReady(result) {
        _ mustBe ()
      }
    }

    "on an internal error, return an UpstreamServerError" in {
      server.stubFor(
        get(urlEqualTo(targetUrl))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withBody(
                Json.stringify(
                  Json.obj(
                    "code"    -> "INTERNAL_SERVER_ERROR",
                    "message" -> "Internal server error"
                  )
                )
              )
          )
      )

      val result = persistenceConnector
        .getMovement(movementSummary.enrollmentEORINumber, MovementType.Departure, movementSummary._id)
        .map(
          _ => fail("This should have failed with an UpstreamErrorResponse, but it succeeded")
        )
        .recover {
          case UpstreamErrorResponse(_, status, _, _) => status
          case thr                                    => fail(s"Expected an UpstreamErrorResponse with a 500, got $thr")
        }

      whenReady(result) {
        _ mustBe INTERNAL_SERVER_ERROR
      }
    }

  }

  "GET /traders/movements/departures" - {

    implicit val hc = HeaderCarrier()
    val eori        = arbitrary[EORINumber].sample.get

    def targetUrl(eoriNumber: EORINumber) = s"/transit-movements/traders/${eoriNumber.value}/movements/departures"

    "on success with no date time filter, return a list of departure IDs" in {
      lazy val movementSummaryList = Gen.listOfN(3, arbitraryMovementSummary.arbitrary).sample.get

      val paginationMovementSummary = PaginationMovementSummary(TotalCount(movementSummaryList.length.longValue), movementSummaryList)

      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori) + defaultFilterParams)
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json.toJson(paginationMovementSummary).toString()
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val result      = persistenceConnector.getMovements(eori, MovementType.Departure, None, None, None, None, ItemCount(25), None, None)
      whenReady(result) {
        _ mustBe paginationMovementSummary
      }
    }

    "on success with a date time and movementEORI filter, return a list of departure IDs" in forAll(
      Gen.listOfN(3, arbitraryMovementSummary.arbitrary),
      arbitrary[OffsetDateTime],
      arbitrary[EORINumber],
      arbitrary[MovementReferenceNumber],
      arbitrary[PageNumber],
      arbitrary[ItemCount]
    ) {
      (movementSummaryList, updatedSince, movementEORI, movementReferenceNumber, pageNumber, itemCount) =>
        val paginationMovementSummary = PaginationMovementSummary(TotalCount(movementSummaryList.length.longValue), movementSummaryList)
        server.stubFor(
          get(
            urlPathEqualTo(targetUrl(eori))
          )
            .withQueryParam("updatedSince", equalTo(DateTimeFormatter.ISO_DATE_TIME.format(updatedSince)))
            .withQueryParam("movementEORI", equalTo(movementEORI.value))
            .withQueryParam("page", equalTo(pageNumber.value.toString))
            .withQueryParam("count", equalTo(itemCount.value.toString))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.toJson(paginationMovementSummary).toString()
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val result = persistenceConnector.getMovements(
          eori,
          MovementType.Departure,
          Some(updatedSince),
          Some(movementEORI),
          Some(movementReferenceNumber),
          Some(pageNumber),
          itemCount,
          Some(updatedSince),
          None
        )
        whenReady(result) {
          _ mustBe paginationMovementSummary
        }
    }

    "on success with a date time filter, return a list of departure IDs" in forAll(
      Gen.listOfN(3, arbitraryMovementSummary.arbitrary),
      arbitrary[OffsetDateTime],
      None,
      None,
      None
    ) {
      (movementSummaryList, updatedSince, movementEORI, movementReferenceNumber, pageNumber) =>
        val paginationMovementSummary = PaginationMovementSummary(TotalCount(movementSummaryList.length.longValue), movementSummaryList)

        server.stubFor(
          get(
            urlPathEqualTo(targetUrl(eori))
          )
            .withQueryParam("updatedSince", equalTo(DateTimeFormatter.ISO_DATE_TIME.format(updatedSince)))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.toJson(paginationMovementSummary).toString()
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val result = persistenceConnector.getMovements(
          eori,
          MovementType.Departure,
          Some(updatedSince),
          movementEORI,
          movementReferenceNumber,
          pageNumber,
          ItemCount(25),
          None,
          None
        )
        whenReady(result) {
          _ mustBe paginationMovementSummary
        }
    }

    "on success with movementEORI filter, return a list of departure IDs" in forAll(
      Gen.listOfN(3, arbitraryMovementSummary.arbitrary),
      None,
      arbitrary[EORINumber],
      arbitrary[MovementReferenceNumber],
      None
    ) {
      (movementSummaryList, updatedSince, movementEORI, movementReferenceNumber, pageNumber) =>
        val paginationMovementSummary = PaginationMovementSummary(TotalCount(movementSummaryList.length.longValue), movementSummaryList)

        server.stubFor(
          get(
            urlEqualTo(
              targetUrl(eori) + s"?movementEORI=${movementEORI.value}&movementReferenceNumber=${movementReferenceNumber.value}&page=1&count=25"
            )
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.toJson(paginationMovementSummary).toString()
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val result = persistenceConnector.getMovements(
          eori,
          MovementType.Departure,
          updatedSince,
          Some(movementEORI),
          Some(movementReferenceNumber),
          pageNumber,
          ItemCount(25),
          None,
          None
        )
        whenReady(result) {
          _ mustBe paginationMovementSummary
        }
    }

    "on success with localReferenceNumber filter, return a list of departure IDs" in forAll(
      Gen.listOfN(3, arbitraryMovementSummary.arbitrary),
      None,
      None,
      None,
      arbitrary[LocalReferenceNumber]
    ) {
      (movementSummaryList, updatedSince, movementEORI, movementReferenceNumber, localReferenceNumber) =>
        val paginationMovementSummary = PaginationMovementSummary(TotalCount(movementSummaryList.length.longValue), movementSummaryList)

        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori) + s"?localReferenceNumber=${localReferenceNumber.value}&page=1&count=25")
          )
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.toJson(paginationMovementSummary).toString()
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val result =
          persistenceConnector.getMovements(
            eori,
            MovementType.Departure,
            updatedSince,
            movementEORI,
            movementReferenceNumber,
            None,
            ItemCount(25),
            None,
            Some(localReferenceNumber)
          )
        whenReady(result) {
          _ mustBe paginationMovementSummary
        }
    }

    "on success with all the filters, return a list of departure IDs" in forAll(
      Gen.listOfN(3, arbitraryMovementSummary.arbitrary),
      arbitrary[OffsetDateTime],
      arbitrary[EORINumber],
      arbitrary[MovementReferenceNumber],
      arbitrary[LocalReferenceNumber]
    ) {
      (movementSummaryList, updatedSince, movementEORI, movementReferenceNumber, localReferenceNumber) =>
        val paginationMovementSummary = PaginationMovementSummary(TotalCount(movementSummaryList.length.longValue), movementSummaryList)

        server.stubFor(
          get(
            urlPathEqualTo(targetUrl(eori))
          )
            .withQueryParam("updatedSince", equalTo(DateTimeFormatter.ISO_DATE_TIME.format(updatedSince)))
            .withQueryParam("movementEORI", equalTo(movementEORI.value))
            .withQueryParam("movementReferenceNumber", equalTo(movementReferenceNumber.value))
            .withQueryParam("localReferenceNumber", equalTo(localReferenceNumber.value))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.toJson(paginationMovementSummary).toString()
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val result =
          persistenceConnector.getMovements(
            eori,
            MovementType.Departure,
            Some(updatedSince),
            Some(movementEORI),
            Some(movementReferenceNumber),
            None,
            ItemCount(25),
            None,
            Some(localReferenceNumber)
          )
        whenReady(result) {
          _ mustBe paginationMovementSummary
        }
    }

    "on incorrect Json, return an error" in {
      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori) + defaultFilterParams)
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                "{ \"test\": \"fail\" }"
              )
          )
      )

      val result = persistenceConnector
        .getMovements(eori, MovementType.Departure, None, None, None, None, ItemCount(25), None, None)
        .map(
          _ => fail("This should have failed with a JsResult.Exception, but it succeeded")
        )
        .recover {
          case JsResult.Exception(_)  => ()
          case t: TestFailedException => t
          case thr                    => fail(s"Expected a JsResult.Exception, got $thr")
        }

      whenReady(result) {
        _ mustBe ()
      }
    }

    "on an internal error, return an UpstreamServerError" in {
      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori) + defaultFilterParams)
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withBody(
                Json.stringify(
                  Json.obj(
                    "code"    -> "INTERNAL_SERVER_ERROR",
                    "message" -> "Internal server error"
                  )
                )
              )
          )
      )

      val result = persistenceConnector
        .getMovements(eori, MovementType.Departure, None, None, None, None, ItemCount(25), None, None)
        .map(
          _ => fail("This should have failed with an UpstreamErrorResponse, but it succeeded")
        )
        .recover {
          case UpstreamErrorResponse(_, status, _, _) => status
          case thr                                    => fail(s"Expected an UpstreamErrorResponse with a 500, got $thr")
        }

      whenReady(result) {
        _ mustBe INTERNAL_SERVER_ERROR
      }
    }

  }

  "POST /traders/movements/:movementId/messages" - {

    lazy val okResultGen =
      for {
        messageId <- arbitrary[MessageId]
      } yield UpdateMovementResponse(messageId)

    def targetUrl(movementId: MovementId) = s"/transit-movements/traders/movements/${movementId.value}/messages"

    "when posting with body and message type" - {
      "On successful update of an element, must return ACCEPTED" in forAll(arbitrary[MovementId], messageType, okResultGen) {
        (departureId, messageType, resultRes) =>
          server.stubFor(
            post(
              urlEqualTo(targetUrl(departureId))
            )
              .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
              .withHeader("X-Message-Type", equalTo(messageType.code))
              .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
              .willReturn(
                aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(resultRes)))
              )
          )

          implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

          val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))
          whenReady(persistenceConnector.postMessage(departureId, Some(messageType), Some(source))) {
            result =>
              result mustBe resultRes
          }
      }

      "On an upstream internal server error, get a UpstreamErrorResponse" in forAll(arbitrary[MovementId], messageType) {
        (departureId, messageType) =>
          server.stubFor(
            post(
              urlEqualTo(targetUrl(departureId))
            )
              .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
              .withHeader("X-Message-Type", equalTo(messageType.code))
              .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
              .willReturn(
                aResponse()
                  .withStatus(INTERNAL_SERVER_ERROR)
                  .withBody(
                    Json.stringify(Json.toJson(PresentationError.internalServiceError()))
                  )
              )
          )

          implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

          val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8))

          val future = persistenceConnector.postMessage(departureId, Some(messageType), Some(source)).map(Right(_)).recover {
            case NonFatal(e) => Left(e)
          }

          whenReady(future) {
            case Left(UpstreamErrorResponse(message, INTERNAL_SERVER_ERROR, _, _)) =>
              Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
            case other => fail(s"Expected internal server error, got $other")
          }
      }

      "On an upstream bad request, get an UpstreamErrorResponse" in forAll(arbitrary[MovementId], messageType) {
        (departureId, messageType) =>
          server.stubFor(
            post(
              urlEqualTo(targetUrl(departureId))
            )
              .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
              .withHeader("X-Message-Type", equalTo(messageType.code))
              .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
              .willReturn(
                aResponse()
                  .withStatus(BAD_REQUEST)
                  .withBody(
                    Json.stringify(Json.toJson(PresentationError.badRequestError("Bad request")))
                  )
              )
          )

          implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

          val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8))

          val future = persistenceConnector.postMessage(departureId, Some(messageType), Some(source)).map(Right(_)).recover {
            case NonFatal(e) => Left(e)
          }

          whenReady(future) {
            case Left(UpstreamErrorResponse(message, BAD_REQUEST, _, _)) =>
              Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Bad request", ErrorCode.BadRequest))
            case other => fail(s"Expected bad request error, got $other")
          }
      }

      "On an incorrect Json fragment, must return a JsResult.Exception" in forAll(arbitrary[MovementId], messageType) {
        (departureId, messageType) =>
          server.stubFor(
            post(
              urlEqualTo(targetUrl(departureId))
            )
              .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
              .withHeader("X-Message-Type", equalTo(messageType.code))
              .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
              .willReturn(
                aResponse()
                  .withStatus(OK)
                  .withBody(
                    "{ hello"
                  )
              )
          )

          implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

          val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))

          val future = persistenceConnector.postMessage(departureId, Some(messageType), Some(source)).map(Right(_)).recover {
            case NonFatal(e) => Left(e)
          }

          whenReady(future) {
            result =>
              result.left.toOption.get mustBe a[JsonParseException]
          }
      }
    }

    "when posting without body and message type (creating an empty message)" - {
      "On successful update of an element, must return ACCEPTED" in forAll(arbitrary[MovementId], okResultGen) {
        (departureId, resultRes) =>
          server.stubFor(
            post(
              urlEqualTo(targetUrl(departureId))
            )
              .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
              .willReturn(
                aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(resultRes)))
              )
          )

          implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

          whenReady(persistenceConnector.postMessage(departureId, None, None)) {
            result =>
              result mustBe resultRes
          }
      }

      "On an upstream internal server error, get a UpstreamErrorResponse" in forAll(arbitrary[MovementId]) {
        departureId =>
          server.stubFor(
            post(
              urlEqualTo(targetUrl(departureId))
            )
              .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
              .willReturn(
                aResponse()
                  .withStatus(INTERNAL_SERVER_ERROR)
                  .withBody(
                    Json.stringify(Json.toJson(PresentationError.internalServiceError()))
                  )
              )
          )

          implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

          val future = persistenceConnector.postMessage(departureId, None, None).map(Right(_)).recover {
            case NonFatal(e) => Left(e)
          }

          whenReady(future) {
            case Left(UpstreamErrorResponse(message, INTERNAL_SERVER_ERROR, _, _)) =>
              Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
            case other => fail(s"Expected internal server error, got $other")
          }
      }

      "On an upstream bad request, get an UpstreamErrorResponse" in forAll(arbitrary[MovementId]) {
        departureId =>
          server.stubFor(
            post(
              urlEqualTo(targetUrl(departureId))
            )
              .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
              .willReturn(
                aResponse()
                  .withStatus(BAD_REQUEST)
                  .withBody(
                    Json.stringify(Json.toJson(PresentationError.badRequestError("Bad request")))
                  )
              )
          )

          implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

          val future = persistenceConnector.postMessage(departureId, None, None).map(Right(_)).recover {
            case NonFatal(e) => Left(e)
          }

          whenReady(future) {
            case Left(UpstreamErrorResponse(message, BAD_REQUEST, _, _)) =>
              Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Bad request", ErrorCode.BadRequest))
            case other => fail(s"Expected bad request error, got $other")
          }
      }
    }
  }

  "POST /traders/:eori/movements/arrivals" - {

    lazy val okResultGen =
      for {
        movementId <- arbitrary[MovementId]
        messageId  <- arbitrary[MessageId]
      } yield MovementResponse(movementId, messageId)

    def targetUrl(eoriNumber: EORINumber) = s"/transit-movements/traders/${eoriNumber.value}/movements/arrivals"

    "On successful creation of an element, must return OK" in forAll(arbitrary[EORINumber], okResultGen, Gen.stringOfN(15, Gen.alphaNumChar)) {
      (eoriNumber, okResult, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(okResult)))
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))

        whenReady(persistenceConnector.postMovement(eoriNumber, MovementType.Arrival, Some(source))) {
          result =>
            result mustBe okResult
        }
    }

    "On an upstream internal server error, get a UpstreamErrorResponse" in forAll(arbitrary[EORINumber], Gen.stringOfN(15, Gen.alphaNumChar)) {
      (eoriNumber, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.internalServiceError()))
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8))

        val future = persistenceConnector.postMovement(eoriNumber, MovementType.Arrival, Some(source)).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          case Left(UpstreamErrorResponse(message, INTERNAL_SERVER_ERROR, _, _)) =>
            Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
          case other => fail(s"Expected internal server error, got $other")
        }
    }

    "On an upstream bad request, get an UpstreamErrorResponse" in forAll(arbitrary[EORINumber], Gen.stringOfN(15, Gen.alphaNumChar)) {
      (eoriNumber, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.badRequestError("Bad request")))
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8))

        val future = persistenceConnector.postMovement(eoriNumber, MovementType.Arrival, Some(source)).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          case Left(UpstreamErrorResponse(message, BAD_REQUEST, _, _)) =>
            Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Bad request", ErrorCode.BadRequest))
          case other => fail(s"Expected bad request error, got $other")
        }
    }

    "On an incorrect Json fragment from transit-movements, must return a JsonParseException" in forAll(
      arbitrary[EORINumber],
      Gen.stringOfN(15, Gen.alphaNumChar)
    ) {
      (eoriNumber, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  "{ hello"
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))

        val future = persistenceConnector.postMovement(eoriNumber, MovementType.Arrival, Some(source)).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.toOption.get mustBe a[JsonParseException]
        }
    }
  }

  "POST /traders/:eori/movements/arrivals for Large Messages" - {

    lazy val okResultGen =
      for {
        movementId <- arbitrary[MovementId]
        messageId  <- arbitrary[MessageId]
      } yield MovementResponse(movementId, messageId)

    def targetUrl(eoriNumber: EORINumber) = s"/transit-movements/traders/${eoriNumber.value}/movements/arrivals"

    "On successful creation of an element, must return OK" in forAll(arbitrary[EORINumber], okResultGen, Gen.stringOfN(15, Gen.alphaNumChar)) {
      (eoriNumber, okResult, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(okResult)))
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        whenReady(persistenceConnector.postMovement(eoriNumber, MovementType.Arrival, None)) {
          result =>
            result mustBe okResult
        }
    }

    "On an upstream internal server error, get a UpstreamErrorResponse" in forAll(arbitrary[EORINumber], Gen.stringOfN(15, Gen.alphaNumChar)) {
      (eoriNumber, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.internalServiceError()))
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val future = persistenceConnector.postMovement(eoriNumber, MovementType.Arrival, None).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          case Left(UpstreamErrorResponse(message, INTERNAL_SERVER_ERROR, _, _)) =>
            Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
          case other => fail(s"Expected internal server error, got $other")
        }
    }

    "On an upstream bad request, get an UpstreamErrorResponse" in forAll(arbitrary[EORINumber], Gen.stringOfN(15, Gen.alphaNumChar)) {
      (eoriNumber, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.badRequestError("Bad request")))
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val future = persistenceConnector.postMovement(eoriNumber, MovementType.Arrival, None).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          case Left(UpstreamErrorResponse(message, BAD_REQUEST, _, _)) =>
            Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Bad request", ErrorCode.BadRequest))
          case other => fail(s"Expected bad request error, got $other")
        }
    }

    "On an incorrect Json fragment from transit-movements, must return a JsonParseException" in forAll(
      arbitrary[EORINumber],
      Gen.stringOfN(15, Gen.alphaNumChar)
    ) {
      (eoriNumber, clientId) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .withHeader(Constants.XClientIdHeader, equalTo(clientId))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  "{ hello"
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(
          extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON),
          otherHeaders = Seq(
            Constants.XClientIdHeader ->
              clientId
          )
        )

        val future = persistenceConnector.postMovement(eoriNumber, MovementType.Arrival, None).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.toOption.get mustBe a[JsonParseException]
        }
    }
  }

  "PATCH traders/:eoriNumber/movements/:movementType/:movementId/messages/:messageId" - {

    def targetUrl(eoriNumber: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId) =
      s"/transit-movements/traders/${eoriNumber.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}"

    lazy val messageUpdate = MessageUpdate(MessageStatus.Processing, None, None)

    implicit val hc: HeaderCarrier = HeaderCarrier()

    "On successful update to the message in the movement, must return OK" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementType],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[MessageUpdate]
    ) {
      (eoriNumber, movementType, movementId, messageId, messageUpdate) =>
        server.stubFor(
          patch(
            urlEqualTo(targetUrl(eoriNumber, movementType, movementId, messageId))
          ).withRequestBody(equalToJson(Json.stringify(Json.toJson(messageUpdate))))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse().withStatus(OK)
            )
        )

        whenReady(persistenceConnector.patchMessage(eoriNumber, movementType, movementId, messageId, messageUpdate)) {
          result =>
            result mustBe ()
        }
    }

    "On an upstream internal server error, get a UpstreamErrorResponse" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementType],
      arbitrary[MovementId],
      arbitrary[MessageId]
    ) {
      (eoriNumber, movementType, movementId, messageId) =>
        server.stubFor(
          patch(
            urlEqualTo(targetUrl(eoriNumber, movementType, movementId, messageId))
          ).withRequestBody(equalToJson(Json.stringify(Json.toJson(messageUpdate))))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.internalServiceError()))
                )
            )
        )

        val future = persistenceConnector.patchMessage(eoriNumber, movementType, movementId, messageId, messageUpdate).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          case Left(UpstreamErrorResponse(message, INTERNAL_SERVER_ERROR, _, _)) =>
            Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
          case other => fail(s"Expected internal server error, got $other")
        }
    }

    "On an upstream bad request, get an UpstreamErrorResponse" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementType],
      arbitrary[MovementId],
      arbitrary[MessageId]
    ) {
      (eoriNumber, movementType, movementId, messageId) =>
        server.stubFor(
          patch(
            urlEqualTo(targetUrl(eoriNumber, movementType, movementId, messageId))
          ).withRequestBody(equalToJson(Json.stringify(Json.toJson(messageUpdate))))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(BAD_REQUEST)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.badRequestError("Bad request")))
                )
            )
        )

        val future = persistenceConnector.patchMessage(eoriNumber, movementType, movementId, messageId, messageUpdate).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          case Left(UpstreamErrorResponse(message, BAD_REQUEST, _, _)) =>
            Json.parse(message).validate[StandardError] mustBe JsSuccess(StandardError("Bad request", ErrorCode.BadRequest))
          case other => fail(s"Expected bad request error, got $other")
        }
    }
  }

  "GET /traders/:eori/movements/arrivals/:arrivalId/messages" - {

    implicit val hc = HeaderCarrier()

    def targetUrl(eoriNumber: EORINumber, arrivalId: MovementId) =
      s"/transit-movements/traders/${eoriNumber.value}/movements/arrivals/${arrivalId.value}/messages"

    "on successful return of message IDs when no filtering is applied, return a success" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      Gen.nonEmptyListOf(arbitrary[MessageSummary])
    ) {
      (eori, arrivalId, messageSummary) =>
        val paginationMessageSummary = PaginationMessageSummary(TotalCount(messageSummary.length.longValue), messageSummary)
        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori, arrivalId) + defaultFilterParams)
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.toJson(paginationMessageSummary).toString()
                )
            )
        )
        val result = persistenceConnector.getMessages(eori, MovementType.Arrival, arrivalId, None, None, ItemCount(25), None)
        whenReady(result) {
          _ mustBe paginationMessageSummary
        }
    }

    "on successful return of arrival message IDs when filtering by received date is applied, return a success" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      arbitrary[OffsetDateTime],
      Gen.nonEmptyListOf(arbitrary[MessageSummary])
    ) {
      (eori, arrivalId, time, messageSummary) =>
        val paginationMessageSummary = PaginationMessageSummary(TotalCount(messageSummary.length.longValue), messageSummary)

        server.stubFor(
          get(
            urlPathEqualTo(targetUrl(eori, arrivalId))
          ).withQueryParam("receivedSince", equalTo(DateTimeFormatter.ISO_DATE_TIME.format(time)))
            .withQueryParam("page", equalTo("1"))
            .withQueryParam("count", equalTo("25"))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.toJson(paginationMessageSummary).toString()
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val result      = persistenceConnector.getMessages(eori, MovementType.Arrival, arrivalId, Some(time), None, ItemCount(25), None)
        whenReady(result) {
          _ mustBe paginationMessageSummary
        }
    }

    "on incorrect Json, return an error" in forAll(arbitrary[EORINumber], arbitrary[MovementId]) {
      (eori, arrivalId) =>
        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori, arrivalId) + defaultFilterParams)
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  "{ \"test\": \"fail\" }"
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val r = persistenceConnector
          .getMessages(eori, MovementType.Arrival, arrivalId, None, None, ItemCount(25), None)
          .map(
            _ => fail("This should have failed with a JsResult.Exception, but it succeeded")
          )
          .recover {
            case JsResult.Exception(_)  => ()
            case t: TestFailedException => t
            case thr                    => fail(s"Expected a JsResult.Exception, got $thr")
          }

        whenReady(r) {
          _ =>
        }

    }

    "on not found, return an UpstreamServerError" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId]
    ) {
      (eori, arrivalId) =>
        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori, arrivalId) + defaultFilterParams)
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withBody(
                  Json.stringify(
                    Json.obj(
                      "code"    -> "NOT_FOUND",
                      "message" -> "not found"
                    )
                  )
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val r = persistenceConnector
          .getMessages(eori, MovementType.Arrival, arrivalId, None, None, ItemCount(25), None)
          .map(
            _ => fail("This should have failed with an UpstreamErrorResponse, but it succeeded")
          )
          .recover {
            case UpstreamErrorResponse(_, NOT_FOUND, _, _) => ()
            case thr                                       => fail(s"Expected an UpstreamErrorResponse with a 404, got $thr")
          }

        whenReady(r) {
          _ =>
        }
    }

    "on an internal error, return an UpstreamServerError" in forAll(arbitrary[EORINumber], arbitrary[MovementId]) {
      (eori, arrivalId) =>
        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori, arrivalId) + defaultFilterParams)
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(
                  Json.stringify(
                    Json.obj(
                      "code"    -> "INTERNAL_SERVER_ERROR",
                      "message" -> "Internal server error"
                    )
                  )
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val r = persistenceConnector
          .getMessages(eori, MovementType.Arrival, arrivalId, None, None, ItemCount(25), None)
          .map(
            _ => fail("This should have failed with an UpstreamErrorResponse, but it succeeded")
          )
          .recover {
            case UpstreamErrorResponse(_, INTERNAL_SERVER_ERROR, _, _) => ()
            case thr                                                   => fail(s"Expected an UpstreamErrorResponse with a 500, got $thr")
          }

        whenReady(r) {
          _ =>
        }
    }
  }

  "GET /traders/:EORI/movements/arrivals" - {

    implicit val hc = HeaderCarrier()
    val eori        = arbitrary[EORINumber].sample.get

    def targetUrl(eoriNumber: EORINumber) = s"/transit-movements/traders/${eoriNumber.value}/movements/arrivals"

    "on success, return a list of arrivals" in {
      lazy val movementSummaryList  = Gen.listOfN(3, arbitraryMovementSummary.arbitrary).sample.get
      val paginationMovementSummary = PaginationMovementSummary(TotalCount(movementSummaryList.length.longValue), movementSummaryList)

      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori) + defaultFilterParams)
        ).willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(
              Json.toJson(paginationMovementSummary).toString()
            )
        )
      )

      implicit val hc = HeaderCarrier()
      val result      = persistenceConnector.getMovements(eori, MovementType.Arrival, None, None, None, None, ItemCount(25), None, None)
      whenReady(result) {
        _ mustBe paginationMovementSummary
      }
    }

    "on success with a date time and movementEORI filter, return a list of arrivals" in forAll(
      Gen.listOfN(3, arbitraryMovementSummary.arbitrary),
      arbitrary[OffsetDateTime],
      arbitrary[EORINumber],
      arbitrary[MovementReferenceNumber],
      arbitrary[PageNumber],
      arbitrary[ItemCount]
    ) {
      (movementSummaryList, updatedSince, movementEORI, movementReferenceNumber, pageNumber, itemCount) =>
        val paginationMovementSummary = PaginationMovementSummary(TotalCount(movementSummaryList.length.longValue), movementSummaryList)

        server.stubFor(
          get(
            urlPathEqualTo(targetUrl(eori))
          )
            .withQueryParam("updatedSince", equalTo(DateTimeFormatter.ISO_DATE_TIME.format(updatedSince)))
            .withQueryParam("movementEORI", equalTo(movementEORI.value))
            .withQueryParam("page", equalTo(pageNumber.value.toString))
            .withQueryParam("count", equalTo(itemCount.value.toString))
            .withQueryParam("receivedUntil", equalTo(DateTimeFormatter.ISO_DATE_TIME.format(updatedSince)))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.toJson(paginationMovementSummary).toString()
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val result = persistenceConnector.getMovements(
          eori,
          MovementType.Arrival,
          Some(updatedSince),
          Some(movementEORI),
          Some(movementReferenceNumber),
          Some(pageNumber),
          itemCount,
          Some(updatedSince),
          None
        )
        whenReady(result) {
          _ mustBe paginationMovementSummary
        }
    }

    "on success with a date time filter, return a list of arrivals" in forAll(
      Gen.listOfN(3, arbitraryMovementSummary.arbitrary),
      arbitrary[OffsetDateTime],
      None,
      None,
      arbitrary[PageNumber],
      arbitrary[ItemCount]
    ) {
      (movementSummaryList, updatedSince, movementEORI, movementReferenceNumber, pageNumber, itemCount) =>
        val paginationMovementSummary = PaginationMovementSummary(TotalCount(movementSummaryList.length.longValue), movementSummaryList)

        server.stubFor(
          get(
            urlPathEqualTo(targetUrl(eori))
          )
            .withQueryParam("updatedSince", equalTo(DateTimeFormatter.ISO_DATE_TIME.format(updatedSince)))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.toJson(paginationMovementSummary).toString()
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val result = persistenceConnector.getMovements(
          eori,
          MovementType.Arrival,
          Some(updatedSince),
          movementEORI,
          movementReferenceNumber,
          Some(pageNumber),
          itemCount,
          Some(updatedSince),
          None
        )
        whenReady(result) {
          _ mustBe paginationMovementSummary
        }
    }

    "on success with movementEORI filter, return a list of arrivals" in forAll(
      Gen.listOfN(3, arbitraryMovementSummary.arbitrary),
      None,
      arbitrary[EORINumber],
      arbitrary[MovementReferenceNumber],
      None
    ) {
      (movementSummaryList, updatedSince, movementEORI, movementReferenceNumber, pageNumber) =>
        val paginationMovementSummary = PaginationMovementSummary(TotalCount(movementSummaryList.length.longValue), movementSummaryList)

        server.stubFor(
          get(
            urlEqualTo(
              targetUrl(eori) + s"?movementEORI=${movementEORI.value}&movementReferenceNumber=${movementReferenceNumber.value}&page=1&count=25"
            )
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.toJson(paginationMovementSummary).toString()
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val result = persistenceConnector.getMovements(
          eori,
          MovementType.Arrival,
          updatedSince,
          Some(movementEORI),
          Some(movementReferenceNumber),
          pageNumber,
          ItemCount(25),
          None,
          None
        )
        whenReady(result) {
          _ mustBe paginationMovementSummary
        }
    }

    "on success with PageNumber filter, return a list of arrivals" in forAll(
      Gen.listOfN(3, arbitraryMovementSummary.arbitrary),
      None,
      None,
      None,
      Gen.option(arbitrary[PageNumber]),
      None
    ) {
      (movementSummaryList, updatedSince, movementEORI, movementReferenceNumber, pageNumber, itemCount) =>
        val paginationMovementSummary = PaginationMovementSummary(TotalCount(movementSummaryList.length.longValue), movementSummaryList)

        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori) + s"?page=${pageNumber.getOrElse(PageNumber(1)).value}&count=${itemCount.getOrElse(ItemCount(25)).value}")
          )
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.toJson(paginationMovementSummary).toString()
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val result =
          persistenceConnector.getMovements(
            eori,
            MovementType.Arrival,
            updatedSince,
            movementEORI,
            movementReferenceNumber,
            pageNumber,
            ItemCount(25),
            None,
            None
          )
        whenReady(result) {
          _ mustBe paginationMovementSummary
        }
    }

    "on success with localReferenceNumber filter, return a list of arrivals" in forAll(
      Gen.listOfN(3, arbitraryMovementSummary.arbitrary),
      None,
      None,
      None,
      arbitrary[LocalReferenceNumber]
    ) {
      (movementSummaryList, updatedSince, movementEORI, movementReferenceNumber, localReferenceNumber) =>
        val paginationMovementSummary = PaginationMovementSummary(TotalCount(movementSummaryList.length.longValue), movementSummaryList)

        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori) + s"?localReferenceNumber=${localReferenceNumber.value}&page=1&count=25")
          )
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.toJson(paginationMovementSummary).toString()
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val result =
          persistenceConnector.getMovements(
            eori,
            MovementType.Arrival,
            updatedSince,
            movementEORI,
            movementReferenceNumber,
            None,
            ItemCount(25),
            None,
            Some(localReferenceNumber)
          )
        whenReady(result) {
          _ mustBe paginationMovementSummary
        }
    }

    "on success with all the filters, return a list of arrivals" in forAll(
      Gen.listOfN(3, arbitraryMovementSummary.arbitrary),
      arbitrary[OffsetDateTime],
      arbitrary[EORINumber],
      arbitrary[MovementReferenceNumber],
      arbitrary[LocalReferenceNumber]
    ) {
      (movementSummaryList, updatedSince, movementEORI, movementReferenceNumber, localReferenceNumber) =>
        val paginationMovementSummary = PaginationMovementSummary(TotalCount(movementSummaryList.length.longValue), movementSummaryList)

        server.stubFor(
          get(
            urlPathEqualTo(targetUrl(eori))
          )
            .withQueryParam("updatedSince", equalTo(DateTimeFormatter.ISO_DATE_TIME.format(updatedSince)))
            .withQueryParam("movementEORI", equalTo(movementEORI.value))
            .withQueryParam("movementReferenceNumber", equalTo(movementReferenceNumber.value))
            .withQueryParam("localReferenceNumber", equalTo(localReferenceNumber.value))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.toJson(paginationMovementSummary).toString()
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val result = persistenceConnector.getMovements(
          eori,
          MovementType.Arrival,
          Some(updatedSince),
          Some(movementEORI),
          Some(movementReferenceNumber),
          None,
          ItemCount(25),
          None,
          Some(localReferenceNumber)
        )
        whenReady(result) {
          _ mustBe paginationMovementSummary
        }
    }

    "on incorrect Json, return an error" in {
      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori) + defaultFilterParams)
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                "{ \"test\": \"fail\" }"
              )
          )
      )

      val result = persistenceConnector
        .getMovements(eori, MovementType.Arrival, None, None, None, None, ItemCount(25), None, None)
        .map(
          _ => fail("This should have failed with a JsResult.Exception, but it succeeded")
        )
        .recover {
          case JsResult.Exception(_)  => ()
          case t: TestFailedException => t
          case thr                    => fail(s"Expected a JsResult.Exception, got $thr")
        }

      whenReady(result) {
        _ mustBe ()
      }
    }

    "on an internal error, return an UpstreamServerError" in {
      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori) + defaultFilterParams)
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withBody(
                Json.stringify(
                  Json.obj(
                    "code"    -> "INTERNAL_SERVER_ERROR",
                    "message" -> "Internal server error"
                  )
                )
              )
          )
      )

      val result = persistenceConnector
        .getMovements(eori, MovementType.Arrival, None, None, None, None, ItemCount(25), None, None)
        .map(
          _ => fail("This should have failed with an UpstreamErrorResponse, but it succeeded")
        )
        .recover {
          case UpstreamErrorResponse(_, status, _, _) => status
          case thr                                    => fail(s"Expected an UpstreamErrorResponse with a 500, got $thr")
        }

      whenReady(result) {
        _ mustBe INTERNAL_SERVER_ERROR
      }
    }

  }

  "GET /traders/movements/arrivals/:arrivalId" - {

    implicit val hc          = HeaderCarrier()
    lazy val movementSummary = arbitraryMovementSummary.arbitrary.sample.get

    lazy val targetUrl = s"/transit-movements/traders/${movementSummary.enrollmentEORINumber.value}/movements/arrivals/${movementSummary._id.value}"

    "on success, return a MovementResponse" in {

      server.stubFor(
        get(
          urlEqualTo(targetUrl)
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json.toJson(movementSummary).toString()
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val result      = persistenceConnector.getMovement(movementSummary.enrollmentEORINumber, MovementType.Arrival, movementSummary._id)
      whenReady(result) {
        _ mustBe movementSummary
      }
    }

    "on incorrect Json, return an error" in {
      server.stubFor(
        get(
          urlEqualTo(targetUrl)
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                "{ \"test\": \"fail\" }"
              )
          )
      )

      val result = persistenceConnector
        .getMovement(movementSummary.enrollmentEORINumber, MovementType.Arrival, movementSummary._id)
        .map(
          _ => fail("This should have failed with a JsResult.Exception, but it succeeded")
        )
        .recover {
          case JsResult.Exception(_) => ()
          case thr                   => fail(s"Expected a JsResult.Exception, got $thr")
        }

      whenReady(result) {
        _ mustBe ()
      }
    }

    "on an internal error, return an UpstreamServerError" in {
      server.stubFor(
        get(
          urlEqualTo(targetUrl)
        )
          .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
          .willReturn(
            aResponse()
              .withStatus(INTERNAL_SERVER_ERROR)
              .withBody(
                Json.stringify(
                  Json.obj(
                    "code"    -> "INTERNAL_SERVER_ERROR",
                    "message" -> "Internal server error"
                  )
                )
              )
          )
      )

      val result = persistenceConnector
        .getMovement(movementSummary.enrollmentEORINumber, MovementType.Arrival, movementSummary._id)
        .map(
          _ => fail("This should have failed with an UpstreamErrorResponse, but it succeeded")
        )
        .recover {
          case UpstreamErrorResponse(_, status, _, _) => status
          case thr                                    => fail(s"Expected an UpstreamErrorResponse with a 500, got $thr")
        }

      whenReady(result) {
        _ mustBe INTERNAL_SERVER_ERROR
      }
    }

  }

  "GET /traders/:eori/movements/arrivals/:arrivalId/messages/:message" - {

    def targetUrl(eoriNumber: EORINumber, arrivalId: MovementId, messageId: MessageId) =
      s"/transit-movements/traders/${eoriNumber.value}/movements/arrivals/${arrivalId.value}/messages/${messageId.value}"

    "on successful message, return a success" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[MessageSummary]
    ) {
      (eori, arrivalId, messageId, movementSummary) =>
        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori, arrivalId, messageId))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  Json.stringify(Json.toJson(movementSummary))
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val result      = persistenceConnector.getMessage(eori, MovementType.Arrival, arrivalId, messageId)
        whenReady(result) {
          _ mustBe movementSummary
        }
    }

    "on incorrect Json, return an error" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      arbitrary[MessageId]
    ) {
      (eori, arrivalId, messageId) =>
        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori, arrivalId, messageId))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(OK)
                .withBody(
                  "{ \"test\": \"fail\" }"
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val r = persistenceConnector
          .getMessage(eori, MovementType.Arrival, arrivalId, messageId)
          .map(
            _ => fail("This should have failed with a JsResult.Exception, but it succeeded")
          )
          .recover {
            case JsResult.Exception(_)  => ()
            case t: TestFailedException => t
            case thr                    => fail(s"Expected a JsResult.Exception, got $thr")
          }

        whenReady(r) {
          _ =>
        }

    }

    "on not found, return an UpstreamServerError" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      arbitrary[MessageId]
    ) {
      (eori, arrivalId, messageId) =>
        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori, arrivalId, messageId))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withBody(
                  Json.stringify(
                    Json.obj(
                      "code"    -> "NOT_FOUND",
                      "message" -> "not found"
                    )
                  )
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val r = persistenceConnector
          .getMessage(eori, MovementType.Arrival, arrivalId, messageId)
          .map(
            _ => fail("This should have failed with an UpstreamErrorResponse, but it succeeded")
          )
          .recover {
            case UpstreamErrorResponse(_, NOT_FOUND, _, _) => ()
            case thr                                       => fail(s"Expected an UpstreamErrorResponse with a 404, got $thr")
          }

        whenReady(r) {
          _ =>
        }
    }

    "on an internal error, return an UpstreamServerError" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      arbitrary[MessageId]
    ) {
      (eori, arrivalId, messageId) =>
        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori, arrivalId, messageId))
          )
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(
                  Json.stringify(
                    Json.obj(
                      "code"    -> "INTERNAL_SERVER_ERROR",
                      "message" -> "Internal server error"
                    )
                  )
                )
            )
        )

        implicit val hc = HeaderCarrier()
        val r = persistenceConnector
          .getMessage(eori, MovementType.Arrival, arrivalId, messageId)
          .map(
            _ => fail("This should have failed with an UpstreamErrorResponse, but it succeeded")
          )
          .recover {
            case UpstreamErrorResponse(_, INTERNAL_SERVER_ERROR, _, _) => ()
            case thr                                                   => fail(s"Expected an UpstreamErrorResponse with a 500, got $thr")
          }

        whenReady(r) {
          _ =>
        }
    }

  }

  "POST /traders/:EORI/movements/:movementType/:movementId/messages/:messageId/body" - {

    def targetUrl(eori: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId) =
      s"/transit-movements/traders/${eori.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}/body"

    "On successful update of an element, must return ACCEPTED" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[MovementType],
      messageType
    ) {
      (eori, movementId, messageId, movementType, messageType) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eori, movementType, movementId, messageId))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse().withStatus(CREATED)
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier()

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))

        whenReady(persistenceConnector.updateMessageBody(messageType, eori, movementType, movementId, messageId, source)) {
          result =>
            result mustBe ()
        }
    }

    "On an upstream internal server error, get a UpstreamErrorResponse" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[MovementType],
      messageType
    ) {
      (eori, movementId, messageId, movementType, messageType) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eori, movementType, movementId, messageId))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.internalServiceError()))
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier()

        val source = Source.single(ByteString("<test></test>", StandardCharsets.UTF_8))

        val future = persistenceConnector.updateMessageBody(messageType, eori, movementType, movementId, messageId, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.toOption.get mustBe a[UpstreamErrorResponse]
            val response = result.left.toOption.get.asInstanceOf[UpstreamErrorResponse]
            response.statusCode mustBe INTERNAL_SERVER_ERROR
            Json.parse(response.message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
        }
    }
  }

  "GET /traders/:EORI/movements/:movementType/:movementId/messages/:messageId/body" - {

    def targetUrl(eori: EORINumber, movementType: MovementType, movementId: MovementId, messageId: MessageId) =
      s"/transit-movements/traders/${eori.value}/movements/${movementType.urlFragment}/${movementId.value}/messages/${messageId.value}/body"

    "On successful retrieval of an element, must return ACCEPTED" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[MovementType]
    ) {
      (eori, movementId, messageId, movementType) =>
        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori, movementType, movementId, messageId))
          )
            .withHeader(HeaderNames.ACCEPT, equalTo(MimeTypes.XML))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse().withStatus(OK).withBody("<test></test>")
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier()

        val result = persistenceConnector
          .getMessageBody(eori, movementType, movementId, messageId)
          .flatMap(_.reduce(_ ++ _).map(_.utf8String).runWith(Sink.head))

        whenReady(result) {
          _ mustBe "<test></test>"
        }
    }

    "If the message is not found, return as such" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[MovementType]
    ) {
      (eori, movementId, messageId, movementType) =>
        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori, movementType, movementId, messageId))
          )
            .withHeader(HeaderNames.ACCEPT, equalTo(MimeTypes.XML))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(NOT_FOUND)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.notFoundError("no")))
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier()

        val future = persistenceConnector.getMessageBody(eori, movementType, movementId, messageId).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.toOption.get mustBe a[UpstreamErrorResponse]
            val response = result.left.toOption.get.asInstanceOf[UpstreamErrorResponse]
            response.statusCode mustBe NOT_FOUND
        }
    }

    "On an upstream internal server error, get a UpstreamErrorResponse" in forAll(
      arbitrary[EORINumber],
      arbitrary[MovementId],
      arbitrary[MessageId],
      arbitrary[MovementType]
    ) {
      (eori, movementId, messageId, movementType) =>
        server.stubFor(
          get(
            urlEqualTo(targetUrl(eori, movementType, movementId, messageId))
          )
            .withHeader(HeaderNames.ACCEPT, equalTo(MimeTypes.XML))
            .withHeader(HeaderNames.AUTHORIZATION, equalTo(token))
            .willReturn(
              aResponse()
                .withStatus(INTERNAL_SERVER_ERROR)
                .withBody(
                  Json.stringify(Json.toJson(PresentationError.internalServiceError()))
                )
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier()

        val future = persistenceConnector.getMessageBody(eori, movementType, movementId, messageId).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.toOption.get mustBe a[UpstreamErrorResponse]
            val response = result.left.toOption.get.asInstanceOf[UpstreamErrorResponse]
            response.statusCode mustBe INTERNAL_SERVER_ERROR
            Json.parse(response.message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
        }
    }
  }

  override protected def portConfigKey: Seq[String] =
    Seq("microservice.services.transit-movements.port")
}
