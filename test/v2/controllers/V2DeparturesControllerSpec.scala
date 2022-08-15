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

import akka.stream.Materializer
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
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status.ACCEPTED
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.http.Status.NOT_FOUND
import play.api.http.Status.OK
import play.api.http.Status.UNSUPPORTED_MEDIA_TYPE
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Request
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.status
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import utils.TestMetrics
import v2.base.TestActorSystem
import v2.base.TestSourceProvider
import v2.fakes.controllers.actions.FakeAuthNewEnrolmentOnlyAction
import v2.fakes.controllers.actions.FakeMessageSizeActionProvider
import v2.models.AuditType
import v2.models.DepartureId
import v2.models.EORINumber
import v2.models.MovementReferenceNumber
import v2.models.MessageId
import v2.models.errors.ConversionError
import v2.models.errors.FailedToValidateError
import v2.models.errors.JsonValidationError
import v2.models.errors.PersistenceError
import v2.models.errors.RouterError
import v2.models.errors.XmlValidationError
import v2.models.request.MessageType
import v2.models.responses.DeclarationResponse
import v2.models.responses.DepartureResponse
import v2.models.responses.MessageResponse
import v2.models.responses.hateoas.HateoasDepartureMessageResponse
import v2.models.responses.hateoas.HateoasDepartureResponse
import v2.services.AuditingService
import v2.services.ConversionService
import v2.services.DeparturesService
import v2.services.RouterService
import v2.services.ValidationService

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Try
import scala.xml.NodeSeq
import scala.xml.XML

class V2DeparturesControllerSpec
    extends AnyFreeSpec
    with Matchers
    with OptionValues
    with ScalaFutures
    with MockitoSugar
    with TestActorSystem
    with TestSourceProvider
    with BeforeAndAfterEach {

  def CC015C: NodeSeq =
    <CC015C>
      <test>testxml</test>
    </CC015C>

  val CC015Cjson = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))

  val mockValidationService: ValidationService = mock[ValidationService]
  val mockDeparturesPersistenceService         = mock[DeparturesService]
  val mockRouterService                        = mock[RouterService]
  val mockAuditService                         = mock[AuditingService]
  val mockConversionService                    = mock[ConversionService]

  lazy val sut: V2DeparturesController = new V2DeparturesControllerImpl(
    Helpers.stubControllerComponents(),
    SingletonTemporaryFileCreator,
    FakeAuthNewEnrolmentOnlyAction(),
    mockValidationService,
    mockConversionService,
    mockDeparturesPersistenceService,
    mockRouterService,
    mockAuditService,
    FakeMessageSizeActionProvider,
    new TestMetrics()
  )

  implicit val timeout: Timeout = 5.seconds

  def fakeHeaders(contentType: String) = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> contentType))

  def fakeRequestDepartures[A](
    method: String,
    headers: FakeHeaders,
    uri: String = routing.routes.DeparturesRouter.submitDeclaration().url,
    body: A
  ): Request[A] =
    FakeRequest(method = method, uri = uri, headers = headers, body = body)

  override def beforeEach(): Unit = {
    reset(mockValidationService)
    reset(mockConversionService)

    reset(mockDeparturesPersistenceService)
    when(
      mockDeparturesPersistenceService
        .saveDeclaration(any[String].asInstanceOf[EORINumber], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
    )
      .thenAnswer {
        invocation: InvocationOnMock =>
          // we're using Mockito, so don't use AnyVal class stuff
          if (invocation.getArgument(0, classOf[String]) == "id") EitherT.rightT(DeclarationResponse(DepartureId("123"), MessageId("456")))
          else EitherT.leftT(PersistenceError.UnexpectedError(None))
      }

    reset(mockRouterService)
    when(
      mockRouterService.send(
        eqTo(MessageType.DepartureDeclaration),
        any[String].asInstanceOf[EORINumber],
        any[String].asInstanceOf[DepartureId],
        any[String].asInstanceOf[MessageId],
        any[Source[ByteString, _]]
      )(any[ExecutionContext], any[HeaderCarrier])
    )
      .thenAnswer {
        invocation: InvocationOnMock =>
          if (invocation.getArgument(1, classOf[String]) == "id") EitherT.rightT(()) // we're using Mockito, so don't use AnyVal class stuff
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
                if (element.label.equalsIgnoreCase("CC015C")) Right(())
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

  val testSinkJson: Sink[ByteString, Future[Either[FailedToValidateError, Unit]]] =
    Flow
      .fromFunction {
        input: ByteString =>
          Try(Json.parse(input.utf8String)).toEither
            .leftMap(
              _ =>
                FailedToValidateError
                  .JsonSchemaFailedToValidateError(NonEmptyList(JsonValidationError("path", "Invalid JSON"), Nil))
            )
            .flatMap {
              jsVal =>
                if ((jsVal \ "CC015").isDefined) Right(())
                else
                  Left(
                    FailedToValidateError
                      .JsonSchemaFailedToValidateError(validationErrors = NonEmptyList(JsonValidationError("CC015", "CC015 expected but not present"), Nil))
                  )
            }
      }
      .toMat(Sink.last)(Keep.right)

  val jsonValidationMockAnswer = (invocation: InvocationOnMock) =>
    EitherT(
      invocation
        .getArgument[Source[ByteString, _]](1)
        .fold(ByteString())(
          (current, next) => current ++ next
        )
        .runWith(testSinkJson)
    )

  // Version 2
  "for a departure declaration with accept header set to application/vnd.hmrc.2.0+json (version two)" - {
    "with content type set to application/xml" - {

      // For the content length headers, we have to ensure that we send something
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
      )

      "must return Accepted when body length is within limits and is considered valid" in {
        when(mockValidationService.validateXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )
        when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

        val request = fakeRequestDepartures(method = "POST", body = singleUseStringSource(CC015C.mkString), headers = standardHeaders)
        val result  = sut.submitDeclaration()(request)
        status(result) mustBe ACCEPTED

        contentAsJson(result) mustBe Json.obj(
          "_links" -> Json.obj(
            "self" -> Json.obj(
              "href" -> "/customs/transits/movements/departures/123"
            )
          ),
          "id" -> "123",
          "_embedded" -> Json.obj(
            "messages" -> Json.obj(
              "_links" -> Json.obj(
                "href" -> "/customs/transits/movements/departures/123/messages"
              )
            )
          )
        )

        verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML))(any(), any())
        verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any())
        verify(mockDeparturesPersistenceService, times(1)).saveDeclaration(EORINumber(any()), any())(any(), any())
        verify(mockRouterService, times(1)).send(eqTo(MessageType.DepartureDeclaration), EORINumber(any()), DepartureId(any()), MessageId(any()), any())(
          any(),
          any()
        )
      }

      "must return Accepted when body length is within limits and is considered valid" - Seq(true, false).foreach {
        auditEnabled =>
          when(
            mockValidationService
              .validateXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            invocation =>
              xmlValidationMockAnswer(invocation)
          }
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

          val success = if (auditEnabled) "is successful" else "fails"
          s"when auditing $success" in {
            beforeEach()
            when(
              mockValidationService.validateXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
            )
              .thenAnswer(
                _ => EitherT.rightT(())
              )

            if (!auditEnabled) {
              reset(mockAuditService)
              when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.failed(UpstreamErrorResponse("error", 500)))
            }
            val request = fakeRequestDepartures(method = "POST", body = singleUseStringSource(CC015C.mkString), headers = standardHeaders)
            val result  = sut.submitDeclaration()(request)
            status(result) mustBe ACCEPTED

            contentAsJson(result) mustBe Json.obj(
              "_links" -> Json.obj(
                "self" -> Json.obj(
                  "href" -> "/customs/transits/movements/departures/123"
                )
              ),
              "id" -> "123",
              "_embedded" -> Json.obj(
                "messages" -> Json.obj(
                  "_links" -> Json.obj(
                    "href" -> "/customs/transits/movements/departures/123/messages"
                  )
                )
              )
            )

            verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML))(any(), any())
            verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any())
            verify(mockDeparturesPersistenceService, times(1)).saveDeclaration(EORINumber(any()), any())(any(), any())
            verify(mockRouterService, times(1))
              .send(eqTo(MessageType.DepartureDeclaration), EORINumber(any()), DepartureId(any()), MessageId(any()), any())(any(), any())
          }
      }

      "must return Bad Request when body is not an XML document" in {
        when(mockValidationService.validateXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList(XmlValidationError(42, 27, "invalid XML"), Nil)))
          )

        val request = fakeRequestDepartures(method = "POST", body = singleUseStringSource("notxml"), headers = standardHeaders)
        val result  = sut.submitDeclaration()(request)
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
        when(mockValidationService.validateXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList(XmlValidationError(1, 1, "an error"), Nil)))
          )

        val request =
          fakeRequestDepartures(method = "POST", body = singleUseStringSource(<test></test>.mkString), headers = standardHeaders)
        val result = sut.submitDeclaration()(request)
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
        when(mockValidationService.validateXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )

        val sut = new V2DeparturesControllerImpl(
          Helpers.stubControllerComponents(),
          SingletonTemporaryFileCreator,
          FakeAuthNewEnrolmentOnlyAction(EORINumber("nope")),
          mockValidationService,
          mockConversionService,
          mockDeparturesPersistenceService,
          mockRouterService,
          mockAuditService,
          FakeMessageSizeActionProvider,
          new TestMetrics()
        )

        val request  = fakeRequestDepartures("POST", body = Source.single(ByteString(CC015C.mkString, StandardCharsets.UTF_8)), headers = standardHeaders)
        val response = sut.submitDeclaration()(request)

        status(response) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(response) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )
      }

      "must return Internal Service Error if the router service reports an error" in {
        when(
          mockValidationService.validateJson(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenAnswer(
            _ => EitherT.rightT(())
          )
        when(mockValidationService.validateXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )
        // we're not testing what happens with the departures service here, so just pass through with a right.
        val mockDeparturesPersistenceService = mock[DeparturesService]
        when(
          mockDeparturesPersistenceService
            .saveDeclaration(any[String].asInstanceOf[EORINumber], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, DeclarationResponse](DeclarationResponse(DepartureId("123"), MessageId("456")))))

        val sut = new V2DeparturesControllerImpl(
          Helpers.stubControllerComponents(),
          SingletonTemporaryFileCreator,
          FakeAuthNewEnrolmentOnlyAction(EORINumber("nope")),
          mockValidationService,
          mockConversionService,
          mockDeparturesPersistenceService,
          mockRouterService,
          mockAuditService,
          FakeMessageSizeActionProvider,
          new TestMetrics()
        )

        val request  = fakeRequestDepartures("POST", body = Source.single(ByteString(CC015C.mkString, StandardCharsets.UTF_8)), headers = standardHeaders)
        val response = sut.submitDeclaration()(request)

        status(response) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(response) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )
      }
    }

    "with content type set to application/json" - {
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON, HeaderNames.CONTENT_LENGTH -> "1000")
      )

      "must return Accepted when body length is within limits and is considered valid" in {
        validateXmlOkStub()

        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(invocation)
        }
        when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON))(any(), any())).thenReturn(Future.successful(()))

        val jsonToXmlConversion = (invocation: InvocationOnMock) =>
          EitherT.rightT(
            invocation.getArgument[Source[ByteString, _]](1)
          )

        when(
          mockConversionService
            .jsonToXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(
              any[HeaderCarrier],
              any[ExecutionContext],
              any[Materializer]
            )
        ).thenAnswer {
          invocation =>
            jsonToXmlConversion(invocation)
        }

        val request = fakeRequestDepartures(method = "POST", body = singleUseStringSource(CC015Cjson), headers = standardHeaders)
        val result  = sut.submitDeclaration()(request)
        status(result) mustBe ACCEPTED

        verify(mockConversionService, times(1)).jsonToXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any(), any())
        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DepartureDeclaration), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any(), any())
        verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.JSON))(any(), any())
      }

      "must return Bad Request when body is not an JSON document" in {
        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(invocation)
        }

        val request = fakeRequestDepartures(method = "POST", body = singleUseStringSource("notjson"), headers = standardHeaders)
        val result  = sut.submitDeclaration()(request)
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "SCHEMA_VALIDATION",
          "message" -> "Request failed schema validation",
          "validationErrors" -> Seq(
            Json.obj(
              "schemaPath" -> "path",
              "message"    -> "Invalid JSON"
            )
          )
        )

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DepartureDeclaration), any())(any(), any())
      }

      "must return Bad Request when body is an JSON document that would fail schema validation" in {
        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(invocation)
        }

        val request =
          fakeRequestDepartures(method = "POST", body = singleUseStringSource("{}"), headers = standardHeaders)
        val result = sut.submitDeclaration()(request)
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "SCHEMA_VALIDATION",
          "message" -> "Request failed schema validation",
          "validationErrors" -> Seq(
            Json.obj(
              "schemaPath" -> "CC015",
              "message"    -> "CC015 expected but not present"
            )
          )
        )

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DepartureDeclaration), any())(any(), any())
      }

      "must return Internal Service Error if the JSON to XML conversion service reports an error" in {
        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(invocation)
        }
        val jsonToXmlConversionError = (_: InvocationOnMock) => EitherT.leftT(ConversionError.UnexpectedError(None))

        when(
          mockConversionService
            .jsonToXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(
              any[HeaderCarrier],
              any[ExecutionContext],
              any[Materializer]
            )
        ).thenAnswer {
          invocation =>
            jsonToXmlConversionError(invocation)
        }

        val request = fakeRequestDepartures(method = "POST", body = singleUseStringSource(CC015Cjson), headers = standardHeaders)
        val result  = sut.submitDeclaration()(request)
        status(result) mustBe INTERNAL_SERVER_ERROR

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DepartureDeclaration), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any(), any())
      }

      "must return Internal Service Error after JSON to XML conversion if the XML validation service reports an error" in {
        validateXmlNotOkStub()
        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(invocation)
        }

        when(
          mockConversionService
            .jsonToXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(
              any[HeaderCarrier],
              any[ExecutionContext],
              any[Materializer]
            )
        ).thenAnswer {
          invocation =>
            EitherT.rightT(
              invocation.getArgument[Source[ByteString, _]](1)
            )
        }

        val request = fakeRequestDepartures(method = "POST", body = singleUseStringSource(CC015Cjson), headers = standardHeaders)
        val result  = sut.submitDeclaration()(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DepartureDeclaration), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any(), any())
        verify(mockValidationService).validateXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any())
      }

      "must return Internal Service Error if the persistence service reports an error" in {
        validateXmlOkStub()

        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(invocation)
        }

        when(
          mockConversionService
            .jsonToXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(
              any[HeaderCarrier],
              any[ExecutionContext],
              any[Materializer]
            )
        ).thenAnswer {
          invocation =>
            EitherT.rightT(
              invocation.getArgument[Source[ByteString, _]](1)
            )
        }

        when(
          mockDeparturesPersistenceService
            .saveDeclaration(any[String].asInstanceOf[EORINumber], any[Source[ByteString, _]])(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenAnswer(
            _ => EitherT.leftT(PersistenceError.UnexpectedError(None))
          )

        val request = fakeRequestDepartures(method = "POST", body = singleUseStringSource(CC015Cjson), headers = standardHeaders)
        val result  = sut.submitDeclaration()(request)
        status(result) mustBe INTERNAL_SERVER_ERROR

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DepartureDeclaration), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any(), any())
        verify(mockDeparturesPersistenceService).saveDeclaration(any[String].asInstanceOf[EORINumber], any())(any(), any())
      }

      "must return Internal Service Error if the router service reports an error" in {
        validateXmlOkStub()

        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(invocation)
        }

        when(
          mockConversionService
            .jsonToXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(
              any[HeaderCarrier],
              any[ExecutionContext],
              any[Materializer]
            )
        ).thenAnswer {
          invocation =>
            EitherT.rightT(
              invocation.getArgument[Source[ByteString, _]](1)
            )
        }

        when(
          mockDeparturesPersistenceService
            .saveDeclaration(any[String].asInstanceOf[EORINumber], any[Source[ByteString, _]])(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenAnswer(
            _ =>
              EitherT.rightT(
                DeclarationResponse(DepartureId("123"), MessageId("456"))
              )
          )

        when(
          mockRouterService.send(
            eqTo(MessageType.DepartureDeclaration),
            any[String].asInstanceOf[EORINumber],
            any[String].asInstanceOf[DepartureId],
            any[String].asInstanceOf[MessageId],
            any[Source[ByteString, _]]
          )(any[ExecutionContext], any[HeaderCarrier])
        )
          .thenAnswer {
            _: InvocationOnMock =>
              EitherT.leftT(RouterError.UnexpectedError(None))
          }

        val request = fakeRequestDepartures(method = "POST", body = singleUseStringSource(CC015Cjson), headers = standardHeaders)
        val result  = sut.submitDeclaration()(request)
        status(result) mustBe INTERNAL_SERVER_ERROR

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DepartureDeclaration), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any(), any())
        verify(mockDeparturesPersistenceService).saveDeclaration(any[String].asInstanceOf[EORINumber], any())(any(), any())
        verify(mockRouterService).send(
          eqTo(MessageType.DepartureDeclaration),
          any[String].asInstanceOf[EORINumber],
          any[String].asInstanceOf[DepartureId],
          any[String].asInstanceOf[MessageId],
          any[Source[ByteString, _]]
        )(any[ExecutionContext], any[HeaderCarrier])
      }

    }

    "must return UNSUPPORTED_MEDIA_TYPE when the content type is invalid" in {
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "invalid", HeaderNames.CONTENT_LENGTH -> "1000")
      )

      val json    = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
      val request = fakeRequestDepartures(method = "POST", body = json, headers = standardHeaders)
      val result  = sut.submitDeclaration()(request)
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
        fakeRequestDepartures(method = "POST", body = Source.single(json), headers = standardHeaders)
      val result = sut.submitDeclaration()(request)
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

      when(mockValidationService.validateXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
        .thenAnswer(
          _ => EitherT.rightT(())
        )
      // we're not testing what happens with the departures service here, so just pass through with a right.
      val mockDeparturesPersistenceService = mock[DeparturesService]
      when(
        mockDeparturesPersistenceService
          .saveDeclaration(any[String].asInstanceOf[EORINumber], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, DeclarationResponse](DeclarationResponse(DepartureId("123"), MessageId("456")))))

      val sut = new V2DeparturesControllerImpl(
        Helpers.stubControllerComponents(),
        SingletonTemporaryFileCreator,
        FakeAuthNewEnrolmentOnlyAction(EORINumber("nope")),
        mockValidationService,
        mockConversionService,
        mockDeparturesPersistenceService,
        mockRouterService,
        mockAuditService,
        FakeMessageSizeActionProvider,
        new TestMetrics()
      )

      val request  = fakeRequestDepartures("POST", body = singleUseStringSource(CC015C.mkString), headers = standardHeaders)
      val response = sut.submitDeclaration()(request)
      status(response) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(response) mustBe Json.obj(
        "code"    -> "INTERNAL_SERVER_ERROR",
        "message" -> "Internal server error"
      )

    }
  }

  "for retrieving a single message" - {

    val dateTime = OffsetDateTime.of(2022, 8, 4, 11, 34, 42, 0, ZoneOffset.UTC)

    "when the message is found" in {
      val messageResponse = MessageResponse(
        MessageId("0123456789abcdef"),
        dateTime,
        dateTime,
        MessageType.DepartureDeclaration,
        None,
        None,
        Some("<test></test>")
      )
      when(mockDeparturesPersistenceService.getMessage(EORINumber(any()), DepartureId(any()), MessageId(any()))(any[HeaderCarrier], any[ExecutionContext]))
        .thenAnswer(
          _ => EitherT.rightT(messageResponse)
        )

      val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
      val result  = sut.getMessage(DepartureId("0123456789abcdef"), MessageId("0123456789abcdef"))(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(HateoasDepartureMessageResponse(DepartureId("0123456789abcdef"), MessageId("0123456789abcdef"), messageResponse))
    }

    "when no message is found" in {
      when(mockDeparturesPersistenceService.getMessage(EORINumber(any()), DepartureId(any()), MessageId(any()))(any[HeaderCarrier], any[ExecutionContext]))
        .thenAnswer(
          _ => EitherT.leftT(PersistenceError.MessageNotFound(DepartureId("0123456789abcdef"), MessageId("0123456789abcdef")))
        )

      val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
      val result  = sut.getMessage(DepartureId("0123456789abcdef"), MessageId("0123456789abcdef"))(request)

      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "NOT_FOUND",
        "message" -> "Message with ID 0123456789abcdef for movement 0123456789abcdef was not found"
      )
    }

    "when an unknown error occurs" in {
      when(mockDeparturesPersistenceService.getMessage(EORINumber(any()), DepartureId(any()), MessageId(any()))(any[HeaderCarrier], any[ExecutionContext]))
        .thenAnswer(
          _ => EitherT.leftT(PersistenceError.UnexpectedError(thr = None))
        )

      val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
      val result  = sut.getMessage(DepartureId("0123456789abcdef"), MessageId("0123456789abcdef"))(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "INTERNAL_SERVER_ERROR",
        "message" -> "Internal server error"
      )
    }

  }

  "retrieving a single departure/movement" - {
    "should return ok with json body of departure" in {
      val createdTime = OffsetDateTime.now()
      val departureResponse = DepartureResponse(
        DepartureId("0123456789abcdef"),
        EORINumber("GB123"),
        EORINumber("XI456"),
        Some(MovementReferenceNumber("312")),
        createdTime,
        createdTime
      )

      when(mockDeparturesPersistenceService.getDeparture(EORINumber(any()), DepartureId(any()))(any(), any()))
        .thenAnswer(
          _ => EitherT.rightT(departureResponse)
        )

      val request = FakeRequest(
        "GET",
        routing.routes.DeparturesRouter.getDeparture("123").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json")),
        AnyContentAsEmpty
      )
      val result = sut.getDeparture(DepartureId("0123456789abcdef"))(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(
        HateoasDepartureResponse(
          DepartureId("0123456789abcdef"),
          DepartureResponse(
            DepartureId("0123456789abcdef"),
            EORINumber("GB123"),
            EORINumber("XI456"),
            Some(MovementReferenceNumber("312")),
            createdTime,
            createdTime
          )
        )
      )
    }

    "should return departure not found if persistence service returns 404" in {
      when(mockDeparturesPersistenceService.getDeparture(EORINumber(any()), DepartureId(any()))(any(), any()))
        .thenAnswer {
          inv =>
            val d = DepartureId(inv.getArgument[String](1))
            EitherT.leftT(PersistenceError.DepartureNotFound(d))
        }

      val request = FakeRequest(
        "GET",
        routing.routes.DeparturesRouter.getDeparture("0123456789abcdef").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json")),
        AnyContentAsEmpty
      )
      val result = sut.getDeparture(DepartureId("0123456789abcdef"))(request)

      status(result) mustBe NOT_FOUND
    }

    "should return unexpected error for all other errors" in {
      when(mockDeparturesPersistenceService.getDeparture(EORINumber(any()), DepartureId(any()))(any(), any()))
        .thenAnswer {
          _ =>
            EitherT.leftT(PersistenceError.UnexpectedError(None))
        }

      val request = FakeRequest(
        "GET",
        routing.routes.DeparturesRouter.getDeparture("0123456789abcdef").url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json")),
        AnyContentAsEmpty
      )
      val result = sut.getDeparture(DepartureId("0123456789abcdef"))(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }

  def validateXmlOkStub(): OngoingStubbing[EitherT[Future, FailedToValidateError, Unit]] =
    when(
      mockValidationService
        .validateXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
    ).thenAnswer {
      _ =>
        EitherT.rightT(())
    }

  def validateXmlNotOkStub(): OngoingStubbing[EitherT[Future, FailedToValidateError, Unit]] =
    when(
      mockValidationService
        .validateXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
    ).thenAnswer {
      _ =>
        EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList(XmlValidationError(1, 1, "invalid XML"), Nil)))
    }
}
