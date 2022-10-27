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

package v2.controllers

import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.util.Timeout
import cats.data.EitherT
import cats.data.NonEmptyList
import cats.implicits.catsStdInstancesForFuture
import cats.implicits.toBifunctorOps
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status._
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.status
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import utils.TestMetrics
import v2.base.CommonGenerators
import v2.base.TestActorSystem
import v2.base.TestSourceProvider
import v2.fakes.controllers.actions.FakeAuthNewEnrolmentOnlyAction
import v2.fakes.controllers.actions.FakeMessageSizeActionProvider
import v2.models._
import v2.models.errors._
import v2.models.request.MessageType
import v2.models.responses.ArrivalResponse
import v2.models.responses.hateoas._
import v2.services._

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Try
import scala.xml.NodeSeq
import scala.xml.XML

class V2ArrivalsControllerSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with TestActorSystem
    with TestSourceProvider
    with BeforeAndAfterEach
    with ScalaCheckDrivenPropertyChecks
    with CommonGenerators {

  def CC007C: NodeSeq =
    <CC007C>
      <test>testxml</test>
    </CC007C>

  val mockValidationService          = mock[ValidationService]
  val mockArrivalsPersistenceService = mock[ArrivalsService]
  val mockRouterService              = mock[RouterService]
  val mockAuditService               = mock[AuditingService]
  implicit val temporaryFileCreator  = SingletonTemporaryFileCreator

  lazy val sut: V2ArrivalsController = new V2ArrivalsControllerImpl(
    Helpers.stubControllerComponents(),
    FakeAuthNewEnrolmentOnlyAction(),
    mockValidationService,
    mockArrivalsPersistenceService,
    mockRouterService,
    mockAuditService,
    FakeMessageSizeActionProvider,
    new TestMetrics()
  )

  implicit val timeout: Timeout = 5.seconds

  def fakeHeaders(contentType: String) = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> contentType))

  def fakeRequestArrivalNotification[A](
    method: String,
    headers: FakeHeaders,
    body: A
  ): Request[A] =
    FakeRequest(method = method, uri = routing.routes.ArrivalsRouter.createArrivalNotification().url, headers = headers, body = body)

  override def beforeEach(): Unit = {
    reset(mockValidationService)

    reset(mockArrivalsPersistenceService)
    when(
      mockArrivalsPersistenceService
        .createArrival(any[String].asInstanceOf[EORINumber], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
    )
      .thenAnswer {
        invocation: InvocationOnMock =>
          if (invocation.getArgument(0, classOf[String]) == "id") EitherT.rightT(ArrivalResponse(MovementId("123"), MessageId("456")))
          else EitherT.leftT(PersistenceError.UnexpectedError(None))
      }

    reset(mockRouterService)
    when(
      mockRouterService.send(
        any[String].asInstanceOf[MessageType],
        any[String].asInstanceOf[EORINumber],
        any[String].asInstanceOf[MovementId],
        any[String].asInstanceOf[MessageId],
        any[Source[ByteString, _]]
      )(any[ExecutionContext], any[HeaderCarrier])
    )
      .thenAnswer {
        invocation: InvocationOnMock =>
          if (invocation.getArgument(1, classOf[String]) == "id") EitherT.rightT(())
          else EitherT.leftT(RouterError.UnexpectedError(None))
      }

    reset(mockAuditService)

  }

  val testSinkXml: Sink[ByteString, Future[Either[FailedToValidateError, Unit]]] =
    Flow
      .fromFunction {
        input: ByteString =>
          Try(XML.loadString(input.decodeString(StandardCharsets.UTF_8))).toEither
            .leftMap(
              _ => FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList(XmlValidationError(42, 27, "invalid XML"), Nil))
            )
            .flatMap {
              element =>
                if (element.label.equalsIgnoreCase("CC007C")) Right(())
                else Left(FailedToValidateError.XmlSchemaFailedToValidateError(validationErrors = NonEmptyList(XmlValidationError(1, 1, "an error"), Nil)))
            }
      }
      .toMat(Sink.last)(Keep.right)

  val xmlValidationMockAnswer = (invocation: InvocationOnMock) =>
    EitherT(
      invocation
        .getArgument[Source[ByteString, _]](1)
        .fold(ByteString())(
          (current, next) => current ++ next
        )
        .runWith(testSinkXml)
    )

  "for a arrival notification with accept header set to application/vnd.hmrc.2.0+json (version two)" - {
    "with content type set to application/xml" - {

      // For the content length headers, we have to ensure that we send something
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
      )

      "must return Accepted when body length is within limits and is considered valid" in {
        when(mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )
        when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

        val request = fakeRequestArrivalNotification(method = "POST", body = singleUseStringSource(CC007C.mkString), headers = standardHeaders)
        val result  = sut.createArrivalNotification()(request)
        status(result) mustBe ACCEPTED

        contentAsJson(result) mustBe Json.toJson(HateoasArrivalNotificationResponse(MovementId("123")))

        verify(mockAuditService, times(1)).audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML))(any(), any())
        verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.ArrivalNotification), any())(any(), any())
        verify(mockArrivalsPersistenceService, times(1)).createArrival(EORINumber(any()), any())(any(), any())
        verify(mockRouterService, times(1)).send(eqTo(MessageType.ArrivalNotification), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
          any(),
          any()
        )
      }

      "must return Accepted when body length is within limits and is considered valid" - Seq(true, false).foreach {
        auditEnabled =>
          when(
            mockValidationService
              .validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            invocation =>
              xmlValidationMockAnswer(invocation)
          }
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

          val success = if (auditEnabled) "is successful" else "fails"
          s"when auditing $success" in {
            beforeEach()
            when(
              mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
            )
              .thenAnswer(
                _ => EitherT.rightT(())
              )

            if (!auditEnabled) {
              reset(mockAuditService)
              when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.failed(UpstreamErrorResponse("error", 500)))
            }
            val request = fakeRequestArrivalNotification(method = "POST", body = singleUseStringSource(CC007C.mkString), headers = standardHeaders)
            val result  = sut.createArrivalNotification()(request)
            status(result) mustBe ACCEPTED

            contentAsJson(result) mustBe Json.toJson(HateoasArrivalNotificationResponse(MovementId("123")))

            verify(mockAuditService, times(1)).audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML))(any(), any())
            verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.ArrivalNotification), any())(any(), any())
            verify(mockArrivalsPersistenceService, times(1)).createArrival(EORINumber(any()), any())(any(), any())
            verify(mockRouterService, times(1))
              .send(eqTo(MessageType.ArrivalNotification), EORINumber(any()), MovementId(any()), MessageId(any()), any())(any(), any())
          }
      }

      "must return Bad Request when body is not an XML document" in {
        when(mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList(XmlValidationError(42, 27, "invalid XML"), Nil)))
          )

        val request = fakeRequestArrivalNotification(method = "POST", body = singleUseStringSource("notxml"), headers = standardHeaders)
        val result  = sut.createArrivalNotification()(request)
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "SCHEMA_VALIDATION",
          "message" -> "Request failed schema validation",
          "validationErrors" -> Seq(
            Json.obj(
              "lineNumber"   -> 42,
              "columnNumber" -> 27,
              "message"      -> "invalid XML"
            )
          )
        )
      }

      "must return Bad Request when body is an XML document that would fail schema validation" in {
        when(mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList(XmlValidationError(1, 1, "an error"), Nil)))
          )

        val request =
          fakeRequestArrivalNotification(method = "POST", body = singleUseStringSource(<test></test>.mkString), headers = standardHeaders)
        val result = sut.createArrivalNotification()(request)
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "SCHEMA_VALIDATION",
          "message" -> "Request failed schema validation",
          "validationErrors" -> Seq(
            Json.obj(
              "lineNumber"   -> 1,
              "columnNumber" -> 1,
              "message"      -> "an error"
            )
          )
        )
      }

      "must return Internal Service Error if the persistence service reports an error" in {
        when(mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )

        val sut = new V2ArrivalsControllerImpl(
          Helpers.stubControllerComponents(),
          FakeAuthNewEnrolmentOnlyAction(EORINumber("nope")),
          mockValidationService,
          mockArrivalsPersistenceService,
          mockRouterService,
          mockAuditService,
          FakeMessageSizeActionProvider,
          new TestMetrics()
        )

        val request =
          fakeRequestArrivalNotification("POST", body = Source.single(ByteString(CC007C.mkString, StandardCharsets.UTF_8)), headers = standardHeaders)
        val response = sut.createArrivalNotification()(request)

        status(response) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(response) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )
      }

      "must return Internal Service Error if the router service reports an error" in {

        when(mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )

        val mockArrivalPersistenceService = mock[ArrivalsService]
        when(
          mockArrivalPersistenceService
            .createArrival(any[String].asInstanceOf[EORINumber], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, ArrivalResponse](ArrivalResponse(MovementId("123"), MessageId("456")))))

        val sut = new V2ArrivalsControllerImpl(
          Helpers.stubControllerComponents(),
          FakeAuthNewEnrolmentOnlyAction(EORINumber("nope")),
          mockValidationService,
          mockArrivalPersistenceService,
          mockRouterService,
          mockAuditService,
          FakeMessageSizeActionProvider,
          new TestMetrics()
        )

        val request =
          fakeRequestArrivalNotification("POST", body = Source.single(ByteString(CC007C.mkString, StandardCharsets.UTF_8)), headers = standardHeaders)
        val response = sut.createArrivalNotification()(request)

        status(response) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(response) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )
      }
    }

    "must return UNSUPPORTED_MEDIA_TYPE when the content type is invalid" in {
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "invalid", HeaderNames.CONTENT_LENGTH -> "1000")
      )

      val json    = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
      val request = fakeRequestArrivalNotification(method = "POST", body = json, headers = standardHeaders)
      val result  = sut.createArrivalNotification()(request)
      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "UNSUPPORTED_MEDIA_TYPE",
        "message" -> "Content-type header invalid is not supported!"
      )

    }

    "must return UNSUPPORTED_MEDIA_TYPE when the content type is not supplied" in {
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_LENGTH -> "1000")
      )

      // We emulate no ContentType by sending in a stream directly, without going through Play's request builder
      val json = Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC"))
      val request =
        fakeRequestArrivalNotification(method = "POST", body = Source.single(json), headers = standardHeaders)
      val result = sut.createArrivalNotification()(request)
      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "UNSUPPORTED_MEDIA_TYPE",
        "message" -> "A content-type header is required!"
      )
    }

    "must return Internal Service Error if the router service reports an error" in {
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
      )

      when(mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
        .thenAnswer(
          _ => EitherT.rightT(())
        )

      val mockArrivalPersistenceService = mock[ArrivalsService]
      when(
        mockArrivalPersistenceService
          .createArrival(any[String].asInstanceOf[EORINumber], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, ArrivalResponse](ArrivalResponse(MovementId("123"), MessageId("456")))))

      val sut = new V2ArrivalsControllerImpl(
        Helpers.stubControllerComponents(),
        FakeAuthNewEnrolmentOnlyAction(EORINumber("nope")),
        mockValidationService,
        mockArrivalPersistenceService,
        mockRouterService,
        mockAuditService,
        FakeMessageSizeActionProvider,
        new TestMetrics()
      )

      val request  = fakeRequestArrivalNotification("POST", body = singleUseStringSource(CC007C.mkString), headers = standardHeaders)
      val response = sut.createArrivalNotification()(request)
      status(response) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(response) mustBe Json.obj(
        "code"    -> "INTERNAL_SERVER_ERROR",
        "message" -> "Internal server error"
      )

    }
  }

}