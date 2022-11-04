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

package v2.connectors

import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.fasterxml.jackson.core.JsonParseException
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import config.AppConfig
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
import utils.TestMetrics
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.errors.ErrorCode
import v2.models.errors.PresentationError
import v2.models.errors.StandardError
import v2.models.request.MessageType
import v2.models.request.MessageType.DeclarationData
import v2.models.responses.ArrivalResponse
import v2.models.responses.DeclarationResponse
import v2.models.responses.MovementResponse
import v2.models.responses.MessageResponse
import v2.models.responses.MessageSummary
import v2.models.responses.UpdateMovementResponse
import v2.utils.CommonGenerators

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class PersistenceConnectorSpec
    extends AnyFreeSpec
    with HttpClientV2Support
    with Matchers
    with GuiceOneAppPerSuite
    with GuiceWiremockSuite
    with ScalaFutures
    with IntegrationPatience
    with ScalaCheckDrivenPropertyChecks
    with CommonGenerators {

  lazy val appConfig: AppConfig                           = app.injector.instanceOf[AppConfig]
  lazy val messageType                                    = MessageType.DeclarationAmendment
  lazy val persistenceConnector: PersistenceConnectorImpl = new PersistenceConnectorImpl(httpClientV2, appConfig, new TestMetrics())
  implicit lazy val ec: ExecutionContext                  = app.materializer.executionContext

  "POST /traders/:eori/movements/departures" - {

    lazy val okResultGen =
      for {
        movementId <- arbitrary[MovementId]
        messageId  <- arbitrary[MessageId]
      } yield DeclarationResponse(movementId, messageId)

    def targetUrl(eoriNumber: EORINumber) = s"/transit-movements/traders/${eoriNumber.value}/movements/departures/"

    "On successful creation of an element, must return OK" in forAll(arbitrary[EORINumber], okResultGen) {
      (eoriNumber, okResult) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .willReturn(
              aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(okResult)))
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))

        whenReady(persistenceConnector.post(eoriNumber, source)) {
          result =>
            result mustBe okResult
        }
    }

    "On an upstream internal server error, get a UpstreamErrorResponse" in forAll(arbitrary[EORINumber]) {
      eoriNumber =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
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

        val future = persistenceConnector.post(eoriNumber, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.get mustBe a[UpstreamErrorResponse]
            val response = result.left.get.asInstanceOf[UpstreamErrorResponse]
            response.statusCode mustBe INTERNAL_SERVER_ERROR
            Json.parse(response.message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
        }
    }

    "On an upstream bad request, get an UpstreamErrorResponse" in forAll(arbitrary[EORINumber]) {
      eoriNumber =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
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

        val future = persistenceConnector.post(eoriNumber, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.get mustBe a[UpstreamErrorResponse]
            val response = result.left.get.asInstanceOf[UpstreamErrorResponse]
            response.statusCode mustBe BAD_REQUEST
            Json.parse(response.message).validate[StandardError] mustBe JsSuccess(StandardError("Bad request", ErrorCode.BadRequest))
        }
    }

    "On an incorrect Json fragment, must return a JsResult.Exception" in forAll(arbitrary[EORINumber]) {
      eoriNumber =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
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

        val future = persistenceConnector.post(eoriNumber, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.get mustBe a[JsonParseException]
        }
    }
  }

  "GET /traders/:eori/movements/departure/:departureid/messages/:message" - {

    val now = OffsetDateTime.now(ZoneOffset.UTC)

    lazy val okResult =
      for {
        messageId   <- arbitrary[MessageId]
        body        <- Gen.alphaNumStr
        messageType <- Gen.oneOf(MessageType.values)
      } yield MessageResponse(messageId, now, now, messageType, None, None, Some(s"<test>$body</test>"))

    def targetUrl(eoriNumber: EORINumber, departureId: MovementId, messageId: MessageId) =
      s"/transit-movements/traders/${eoriNumber.value}/movements/departures/${departureId.value}/messages/${messageId.value}/"

    "on successful message, return a success" in {
      val eori            = arbitrary[EORINumber].sample.get
      val departureId     = arbitrary[MovementId].sample.get
      val messageId       = arbitrary[MessageId].sample.get
      val messageResponse = generateResponseWithoutBody(messageId)

      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori, departureId, messageResponse.id))
        )
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json.stringify(Json.toJson(messageResponse))
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val result      = persistenceConnector.getDepartureMessage(eori, departureId, messageResponse.id)
      whenReady(result) {
        _ mustBe messageResponse
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
        .getDepartureMessage(eori, departureId, messageId)
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
          .getDepartureMessage(eori, departureId, messageId)
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
        .getDepartureMessage(eori, departureId, messageId)
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
      s"/transit-movements/traders/${eoriNumber.value}/movements/departures/${departureId.value}/messages/"

    def targetUrlWithTime(eoriNumber: EORINumber, departureId: MovementId, receivedSince: OffsetDateTime) =
      s"/transit-movements/traders/${eoriNumber.value}/movements/departures/${departureId.value}/messages/?receivedSince=${DateTimeFormatter.ISO_OFFSET_DATE_TIME
        .format(receivedSince)}"

    "on successful return of message IDs when no filtering is applied, return a success" in {
      val eori        = arbitrary[EORINumber].sample.get
      val departureId = arbitrary[MovementId].sample.get
      val messageResponse = messageIdList.sample.get.map(
        id => generateResponseWithoutBody(id)
      )

      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori, departureId))
        )
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json.toJson(messageResponse).toString()
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val result      = persistenceConnector.getDepartureMessageIds(eori, departureId, None)
      whenReady(result) {
        _ mustBe messageResponse
      }
    }

    "on successful return of message IDs when filtering by received date is applied, return a success" in {
      val eori        = arbitrary[EORINumber].sample.get
      val departureId = arbitrary[MovementId].sample.get
      val time        = OffsetDateTime.now(ZoneOffset.UTC)
      val messageResponse = messageIdList.sample.get.map(
        id => generateResponseWithoutBody(id)
      )

      server.stubFor(
        get(
          urlEqualTo(targetUrlWithTime(eori, departureId, time))
        )
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json.toJson(messageResponse).toString()
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val result      = persistenceConnector.getDepartureMessageIds(eori, departureId, Some(time))
      whenReady(result) {
        _ mustBe messageResponse
      }
    }

    "on incorrect Json, return an error" in {

      val eori        = arbitrary[EORINumber].sample.get
      val departureId = arbitrary[MovementId].sample.get
      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori, departureId))
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
        .getDepartureMessageIds(eori, departureId, None)
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
            urlEqualTo(targetUrl(eori, departureId))
          )
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
          .getDepartureMessageIds(eori, departureId, None)
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
          urlEqualTo(targetUrl(eori, departureId))
        )
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
        .getDepartureMessageIds(eori, departureId, None)
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

  "GET /traders/movements/departures" - {

    implicit val hc = HeaderCarrier()
    val eori        = arbitrary[EORINumber].sample.get

    def targetUrl(eoriNumber: EORINumber) = s"/transit-movements/traders/${eoriNumber.value}/movements/"

    "on success, return a list of departure IDs" in {
      lazy val departureIdList = Gen.listOfN(3, arbitrary[MovementId]).sample.get
      lazy val messageResponse = departureIdList.map(
        id => generateMovementResponse(id)
      )

      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori))
        )
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json.toJson(messageResponse).toString()
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val result      = persistenceConnector.getDeparturesForEori(eori)
      whenReady(result) {
        _ mustBe messageResponse
      }
    }

    "on incorrect Json, return an error" in {
      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori))
        )
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                "{ \"test\": \"fail\" }"
              )
          )
      )

      val result = persistenceConnector
        .getDeparturesForEori(eori)
        .map(
          _ => fail("This should have failed with a JsResult.Exception, but it succeeded")
        )
        .recover {
          case JsResult.Exception(_)  => ()
          case t: TestFailedException => t
          case thr                    => fail(s"Expected a JsResult.Exception, got $thr")
        }

      whenReady(result) {
        _ mustBe (())
      }
    }

    "on an internal error, return an UpstreamServerError" in {
      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori))
        )
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
        .getDeparturesForEori(eori)
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

  private def generateResponseWithoutBody(messageId: MessageId) =
    MessageSummary(
      messageId,
      arbitrary[OffsetDateTime].sample.get,
      DeclarationData,
      Some("<CC015C><test>testxml</test></CC015C>")
    )

  "POST /traders/movements/:movementId/messages" - {

    lazy val okResultGen =
      for {
        messageId <- arbitrary[MessageId]
      } yield UpdateMovementResponse(messageId)

    def targetUrl(departureId: MovementId) = s"/transit-movements/traders/movements/${departureId.value}/messages/"

    "On successful update of an element, must return ACCEPTED" in forAll(arbitrary[MovementId], messageType, okResultGen) {
      (departureId, messageType, resultRes) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(departureId))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .withHeader("X-Message-Type", equalTo(messageType.code))
            .willReturn(
              aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(resultRes)))
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))
        whenReady(persistenceConnector.post(departureId, messageType, source)) {
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

        val future = persistenceConnector.post(departureId, messageType, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.get mustBe a[UpstreamErrorResponse]
            val response = result.left.get.asInstanceOf[UpstreamErrorResponse]
            response.statusCode mustBe INTERNAL_SERVER_ERROR
            Json.parse(response.message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
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

        val future = persistenceConnector.post(departureId, messageType, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.get mustBe a[UpstreamErrorResponse]
            val response = result.left.get.asInstanceOf[UpstreamErrorResponse]
            response.statusCode mustBe BAD_REQUEST
            Json.parse(response.message).validate[StandardError] mustBe JsSuccess(StandardError("Bad request", ErrorCode.BadRequest))
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

        val future = persistenceConnector.post(departureId, messageType, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.get mustBe a[JsonParseException]
        }
    }
  }

  "POST /traders/:eori/movements/arrivals" - {

    lazy val okResultGen =
      for {
        movementId <- arbitrary[MovementId]
        messageId  <- arbitrary[MessageId]
      } yield ArrivalResponse(movementId, messageId)

    def targetUrl(eoriNumber: EORINumber) = s"/transit-movements/traders/${eoriNumber.value}/movements/arrivals/"

    "On successful creation of an element, must return OK" in forAll(arbitrary[EORINumber], okResultGen) {
      (eoriNumber, okResult) =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
            .willReturn(
              aResponse().withStatus(OK).withBody(Json.stringify(Json.toJson(okResult)))
            )
        )

        implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq(HeaderNames.ACCEPT -> ContentTypes.JSON))

        val source = Source.single(ByteString(<test></test>.mkString, StandardCharsets.UTF_8))

        whenReady(persistenceConnector.postArrival(eoriNumber, source)) {
          result =>
            result mustBe okResult
        }
    }

    "On an upstream internal server error, get a UpstreamErrorResponse" in forAll(arbitrary[EORINumber]) {
      eoriNumber =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
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

        val future = persistenceConnector.postArrival(eoriNumber, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.get mustBe a[UpstreamErrorResponse]
            val response = result.left.get.asInstanceOf[UpstreamErrorResponse]
            response.statusCode mustBe INTERNAL_SERVER_ERROR
            Json.parse(response.message).validate[StandardError] mustBe JsSuccess(StandardError("Internal server error", ErrorCode.InternalServerError))
        }
    }

    "On an upstream bad request, get an UpstreamErrorResponse" in forAll(arbitrary[EORINumber]) {
      eoriNumber =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
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

        val future = persistenceConnector.postArrival(eoriNumber, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.get mustBe a[UpstreamErrorResponse]
            val response = result.left.get.asInstanceOf[UpstreamErrorResponse]
            response.statusCode mustBe BAD_REQUEST
            Json.parse(response.message).validate[StandardError] mustBe JsSuccess(StandardError("Bad request", ErrorCode.BadRequest))
        }
    }

    "On an incorrect Json fragment from transit-movements, must return a JsonParseException" in forAll(arbitrary[EORINumber]) {
      eoriNumber =>
        server.stubFor(
          post(
            urlEqualTo(targetUrl(eoriNumber))
          )
            .withHeader(HeaderNames.CONTENT_TYPE, equalTo(MimeTypes.XML))
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

        val future = persistenceConnector.postArrival(eoriNumber, source).map(Right(_)).recover {
          case NonFatal(e) => Left(e)
        }

        whenReady(future) {
          result =>
            result.left.get mustBe a[JsonParseException]
        }
    }
  }

  "GET /traders/movements/arrivals" - {

    implicit val hc = HeaderCarrier()
    val eori        = arbitrary[EORINumber].sample.get

    def targetUrl(eoriNumber: EORINumber) = s"/transit-movements/traders/${eoriNumber.value}/movements/arrivals"

    "on success, return a list of arrival IDs" in {
      lazy val arrivalIdList = Gen.listOfN(3, arbitrary[MovementId]).sample.get
      lazy val messageResponse = arrivalIdList.map(
        id => generateMovementResponse(id)
      )

      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori))
        )
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json.toJson(messageResponse).toString()
              )
          )
      )

      implicit val hc = HeaderCarrier()
      val result      = persistenceConnector.getArrivalsForEori(eori)
      whenReady(result) {
        _ mustBe messageResponse
      }
    }

    "on incorrect Json, return an error" in {
      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori))
        )
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                "{ \"test\": \"fail\" }"
              )
          )
      )

      val result = persistenceConnector
        .getArrivalsForEori(eori)
        .map(
          _ => fail("This should have failed with a JsResult.Exception, but it succeeded")
        )
        .recover {
          case JsResult.Exception(_)  => ()
          case t: TestFailedException => t
          case thr                    => fail(s"Expected a JsResult.Exception, got $thr")
        }

      whenReady(result) {
        _ mustBe (())
      }
    }

    "on an internal error, return an UpstreamServerError" in {
      server.stubFor(
        get(
          urlEqualTo(targetUrl(eori))
        )
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
        .getArrivalsForEori(eori)
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

  private def generateMovementResponse(movementId: MovementId) =
    MovementResponse(
      _id = movementId,
      enrollmentEORINumber = arbitrary[EORINumber].sample.get,
      movementEORINumber = arbitrary[EORINumber].sample.get,
      movementReferenceNumber = None,
      created = arbitrary[OffsetDateTime].sample.get,
      updated = arbitrary[OffsetDateTime].sample.get
    )

  override protected def portConfigKey: Seq[String] =
    Seq("microservice.services.transit-movements.port")
}
