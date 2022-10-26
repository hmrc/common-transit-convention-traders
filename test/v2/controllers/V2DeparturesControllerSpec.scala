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
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.http.HttpVerbs.GET
import play.api.http.HeaderNames
import play.api.http.MimeTypes
import play.api.http.Status._
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Request
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.status
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.UpstreamErrorResponse
import utils.TestMetrics
import v2.base.CommonGenerators
import v2.base.TestActorSystem
import v2.base.TestSourceProvider
import v2.fakes.controllers.actions.FakeAuthNewEnrolmentOnlyAction
import v2.fakes.controllers.actions.FakeMessageSizeActionProvider
import v2.models._
import v2.models.errors.ExtractionError.MessageTypeNotFound
import v2.models.errors.FailedToValidateError.InvalidMessageTypeError
import v2.models.errors.FailedToValidateError.JsonSchemaFailedToValidateError
import v2.models.errors._
import v2.models.request.MessageType
import v2.models.responses.DeclarationResponse
import v2.models.responses.DepartureResponse
import v2.models.responses.MessageSummary
import v2.models.responses.UpdateMovementResponse
import v2.models.responses.hateoas._
import v2.services._

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.ExecutionContext
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
    with BeforeAndAfterEach
    with ScalaCheckDrivenPropertyChecks
    with CommonGenerators {

  def CC015C: NodeSeq =
    <CC015C>
      <test>testxml</test>
    </CC015C>

  def CC013C: NodeSeq =
    <CC013C>
      <test>testxml</test>
    </CC013C>

  val CC015Cjson = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
  val CC013Cjson = Json.stringify(Json.obj("CC013" -> Json.obj("field" -> "value")))

  val mockValidationService            = mock[ValidationService]
  val mockDeparturesPersistenceService = mock[DeparturesService]
  val mockRouterService                = mock[RouterService]
  val mockAuditService                 = mock[AuditingService]
  val mockConversionService            = mock[ConversionService]
  val mockXmlParsingService            = mock[XmlMessageParsingService]
  val mockJsonParsingService           = mock[JsonMessageParsingService]
  implicit val temporaryFileCreator    = SingletonTemporaryFileCreator

  lazy val messageType: MessageType = MessageType.DeclarationAmendment
  lazy val auditType: AuditType     = AuditType.DeclarationAmendment
  lazy val departureId              = MovementId("123")

  lazy val messageDataEither: EitherT[Future, ExtractionError, MessageType] =
    EitherT.rightT(messageType)

  lazy val auditTypeEither: EitherT[Future, FailedToValidateError, AuditType] =
    EitherT.rightT(auditType)

  lazy val sut: V2DeparturesController = new V2DeparturesControllerImpl(
    Helpers.stubControllerComponents(),
    FakeAuthNewEnrolmentOnlyAction(),
    mockValidationService,
    mockConversionService,
    mockDeparturesPersistenceService,
    mockRouterService,
    mockAuditService,
    FakeMessageSizeActionProvider,
    new TestMetrics(),
    mockXmlParsingService,
    mockJsonParsingService
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

  def fakeAttachDepartures[A](
    method: String,
    headers: FakeHeaders,
    body: A
  ): Request[A] =
    FakeRequest(method = method, uri = routing.routes.DeparturesRouter.attachMessage("123").url, headers = headers, body = body)

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
          if (invocation.getArgument(0, classOf[String]) == "id") EitherT.rightT(DeclarationResponse(MovementId("123"), MessageId("456")))
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
          if (invocation.getArgument(1, classOf[String]) == "id") EitherT.rightT(()) // we're using Mockito, so don't use AnyVal class stuff
          else EitherT.leftT(RouterError.UnexpectedError(None))
      }

    reset(mockAuditService)
    reset(mockXmlParsingService)
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
        when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )
        when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

        val request = fakeRequestDepartures(method = "POST", body = singleUseStringSource(CC015C.mkString), headers = standardHeaders)
        val result  = sut.submitDeclaration()(request)
        status(result) mustBe ACCEPTED

        contentAsJson(result) mustBe Json.toJson(HateoasDepartureDeclarationResponse(MovementId("123")))

        verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML))(any(), any())
        verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.DeclarationData), any())(any(), any())
        verify(mockDeparturesPersistenceService, times(1)).saveDeclaration(EORINumber(any()), any())(any(), any())
        verify(mockRouterService, times(1)).send(eqTo(MessageType.DeclarationData), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
          any(),
          any()
        )
      }

      "must return Accepted when body length is within limits and is considered valid" - Seq(true, false).foreach {
        auditEnabled =>
          when(
            mockValidationService
              .validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            invocation =>
              xmlValidationMockAnswer(invocation)
          }
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

          val success = if (auditEnabled) "is successful" else "fails"
          s"when auditing $success" in {
            beforeEach()
            when(
              mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
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

            // the response format is tested in HateoasDepartureDeclarationResponseSpec
            contentAsJson(result) mustBe Json.toJson(HateoasDepartureDeclarationResponse(MovementId("123")))

            verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML))(any(), any())
            verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.DeclarationData), any())(any(), any())
            verify(mockDeparturesPersistenceService, times(1)).saveDeclaration(EORINumber(any()), any())(any(), any())
            verify(mockRouterService, times(1))
              .send(eqTo(MessageType.DeclarationData), EORINumber(any()), MovementId(any()), MessageId(any()), any())(any(), any())
          }
      }

      "must return Bad Request when body is not an XML document" in {
        when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
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
        when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
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
        when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )

        val sut = new V2DeparturesControllerImpl(
          Helpers.stubControllerComponents(),
          FakeAuthNewEnrolmentOnlyAction(EORINumber("nope")),
          mockValidationService,
          mockConversionService,
          mockDeparturesPersistenceService,
          mockRouterService,
          mockAuditService,
          FakeMessageSizeActionProvider,
          new TestMetrics(),
          mockXmlParsingService,
          mockJsonParsingService
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
          mockValidationService.validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenAnswer(
            _ => EitherT.rightT(())
          )
        when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )
        // we're not testing what happens with the departures service here, so just pass through with a right.
        val mockDeparturesPersistenceService = mock[DeparturesService]
        when(
          mockDeparturesPersistenceService
            .saveDeclaration(any[String].asInstanceOf[EORINumber], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, DeclarationResponse](DeclarationResponse(MovementId("123"), MessageId("456")))))

        val sut = new V2DeparturesControllerImpl(
          Helpers.stubControllerComponents(),
          FakeAuthNewEnrolmentOnlyAction(EORINumber("nope")),
          mockValidationService,
          mockConversionService,
          mockDeparturesPersistenceService,
          mockRouterService,
          mockAuditService,
          FakeMessageSizeActionProvider,
          new TestMetrics(),
          mockXmlParsingService,
          mockJsonParsingService
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
            .validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
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
            .jsonToXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(
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

        verify(mockConversionService, times(1)).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
        verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.JSON))(any(), any())
      }

      "must return Bad Request when body is not an JSON document" in {
        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
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

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
      }

      "must return Bad Request when body is an JSON document that would fail schema validation" in {
        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
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

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
      }

      "must return Internal Service Error if the JSON to XML conversion service reports an error" in {
        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(invocation)
        }
        val jsonToXmlConversionError = (_: InvocationOnMock) => EitherT.leftT(ConversionError.UnexpectedError(None))

        when(
          mockConversionService
            .jsonToXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(
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

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
      }

      "must return Internal Service Error after JSON to XML conversion if the XML validation service reports an error" in {
        validateXmlNotOkStub()
        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(invocation)
        }

        when(
          mockConversionService
            .jsonToXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(
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

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
        verify(mockValidationService).validateXml(eqTo(MessageType.DeclarationData), any())(any(), any())
      }

      "must return Internal Service Error if the persistence service reports an error" in {
        validateXmlOkStub()

        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(invocation)
        }

        when(
          mockConversionService
            .jsonToXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(
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

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
        verify(mockDeparturesPersistenceService).saveDeclaration(any[String].asInstanceOf[EORINumber], any())(any(), any())
      }

      "must return Internal Service Error if the router service reports an error" in {
        validateXmlOkStub()

        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(invocation)
        }

        when(
          mockConversionService
            .jsonToXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(
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
                DeclarationResponse(MovementId("123"), MessageId("456"))
              )
          )

        when(
          mockRouterService.send(
            eqTo(MessageType.DeclarationData),
            any[String].asInstanceOf[EORINumber],
            any[String].asInstanceOf[MovementId],
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

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
        verify(mockDeparturesPersistenceService).saveDeclaration(any[String].asInstanceOf[EORINumber], any())(any(), any())
        verify(mockRouterService).send(
          eqTo(MessageType.DeclarationData),
          any[String].asInstanceOf[EORINumber],
          any[String].asInstanceOf[MovementId],
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

      when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
        .thenAnswer(
          _ => EitherT.rightT(())
        )
      // we're not testing what happens with the departures service here, so just pass through with a right.
      val mockDeparturesPersistenceService = mock[DeparturesService]
      when(
        mockDeparturesPersistenceService
          .saveDeclaration(any[String].asInstanceOf[EORINumber], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
      ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, DeclarationResponse](DeclarationResponse(MovementId("123"), MessageId("456")))))

      val sut = new V2DeparturesControllerImpl(
        Helpers.stubControllerComponents(),
        FakeAuthNewEnrolmentOnlyAction(EORINumber("nope")),
        mockValidationService,
        mockConversionService,
        mockDeparturesPersistenceService,
        mockRouterService,
        mockAuditService,
        FakeMessageSizeActionProvider,
        new TestMetrics(),
        mockXmlParsingService,
        mockJsonParsingService
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

  "for retrieving a list of message IDs" - {

    val datetimes = Seq(arbitrary[OffsetDateTime].sample, None)

    datetimes.foreach {
      dateTime =>
        s"when a departure is found ${dateTime
          .map(
            _ => "with"
          )
          .getOrElse("without")} a date filter" in forAll(Gen.listOfN(3, arbitrary[MessageSummary])) {
          messageResponse =>
            when(mockDeparturesPersistenceService.getMessageIds(EORINumber(any()), MovementId(any()), any())(any[HeaderCarrier], any[ExecutionContext]))
              .thenAnswer(
                _ => EitherT.rightT(messageResponse)
              )

            val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
            val result  = sut.getMessageIds(MovementId("0123456789abcdef"), dateTime)(request)

            status(result) mustBe OK
            contentAsJson(result) mustBe Json.toJson(HateoasDepartureMessageIdsResponse(MovementId("0123456789abcdef"), messageResponse, dateTime))
        }
    }

    "when no departure is found" in {
      when(mockDeparturesPersistenceService.getMessageIds(EORINumber(any()), MovementId(any()), any())(any[HeaderCarrier], any[ExecutionContext]))
        .thenAnswer(
          _ => EitherT.leftT(PersistenceError.DepartureNotFound(MovementId("0123456789abcdef")))
        )

      val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
      val result  = sut.getMessageIds(MovementId("0123456789abcdef"), None)(request)

      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "NOT_FOUND",
        "message" -> "Departure movement with ID 0123456789abcdef was not found"
      )
    }

    "when an unknown error occurs" in {
      when(mockDeparturesPersistenceService.getMessageIds(EORINumber(any()), MovementId(any()), any())(any[HeaderCarrier], any[ExecutionContext]))
        .thenAnswer(
          _ => EitherT.leftT(PersistenceError.UnexpectedError(thr = None))
        )

      val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
      val result  = sut.getMessageIds(MovementId("0123456789abcdef"), None)(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "INTERNAL_SERVER_ERROR",
        "message" -> "Internal server error"
      )
    }

  }

  Seq(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE, VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML).foreach {
    acceptHeaderValue =>
      val convertBodyToJson = acceptHeaderValue == VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML
      val movementId        = arbitraryMovementId.arbitrary.sample.value
      val messageId         = arbitraryMessageId.arbitrary.sample.value
      val messageSummary    = genMessageSummary.arbitrary.sample.value.copy(id = messageId, body = Some("<test>ABC</test>"))

      val headers =
        FakeHeaders(Seq(HeaderNames.ACCEPT -> acceptHeaderValue))
      val request =
        FakeRequest("GET", routing.routes.DeparturesRouter.getMessage(movementId.value, messageId.value).url, headers, Source.empty[ByteString])

      s"for retrieving a single message when the accept header equals $acceptHeaderValue" - {

        "when the message is found" in {

          when(mockDeparturesPersistenceService.getMessage(EORINumber(any()), MovementId(any()), MessageId(any()))(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => EitherT.rightT(messageSummary)
            )

          if (convertBodyToJson) {
            when(mockConversionService.xmlToJson(any[MessageType], any[Source[ByteString, _]])(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
              .thenAnswer(
                _ => EitherT.rightT(singleUseStringSource("{'test': 'ABC'}"))
              )
          }

          val result = sut.getMessage(movementId, messageId, acceptHeaderValue)(request)

          status(result) mustBe OK
          contentAsJson(result) mustBe Json.toJson(
            HateoasDepartureMessageResponse(
              movementId,
              messageId,
              if (convertBodyToJson) messageSummary.copy(body = Some("{'test': 'ABC'}")) else messageSummary
            )
          )
        }

        "when no message is found" in {
          when(mockDeparturesPersistenceService.getMessage(EORINumber(any()), MovementId(any()), MessageId(any()))(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => EitherT.leftT(PersistenceError.MessageNotFound(movementId, messageId))
            )

          val result = sut.getMessage(movementId, messageId, acceptHeaderValue)(request)

          status(result) mustBe NOT_FOUND
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "NOT_FOUND",
            "message" -> s"Message with ID ${messageId.value} for movement ${movementId.value} was not found"
          )
        }

        "when an unknown error occurs" in {
          when(mockDeparturesPersistenceService.getMessage(EORINumber(any()), MovementId(any()), MessageId(any()))(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => EitherT.leftT(PersistenceError.UnexpectedError(thr = None))
            )

          val result = sut.getMessage(movementId, messageId, acceptHeaderValue)(request)

          status(result) mustBe INTERNAL_SERVER_ERROR
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "INTERNAL_SERVER_ERROR",
            "message" -> "Internal server error"
          )
        }

      }
  }

  "GET  /traders/:EORI/movements" - {
    "should return ok with json body for departures" in {

      val enrolmentEORINumber = arbitrary[EORINumber].sample.value
      val dateTime            = OffsetDateTime.of(2022, 8, 4, 11, 34, 42, 0, ZoneOffset.UTC)

      val departureResponse1 = DepartureResponse(
        _id = arbitrary[MovementId].sample.value,
        enrollmentEORINumber = enrolmentEORINumber,
        movementEORINumber = arbitrary[EORINumber].sample.value,
        movementReferenceNumber = Some(arbitrary[MovementReferenceNumber].sample.value),
        created = dateTime,
        updated = dateTime.plusHours(1)
      )

      val departureResponse2 = DepartureResponse(
        _id = arbitrary[MovementId].sample.value,
        enrollmentEORINumber = enrolmentEORINumber,
        movementEORINumber = arbitrary[EORINumber].sample.value,
        movementReferenceNumber = Some(arbitrary[MovementReferenceNumber].sample.value),
        created = dateTime.plusHours(2),
        updated = dateTime.plusHours(3)
      )
      val departureResponses = Seq(departureResponse1, departureResponse2)

      when(mockDeparturesPersistenceService.getDeparturesForEori(EORINumber(any()))(any[HeaderCarrier], any[ExecutionContext]))
        .thenAnswer(
          _ => EitherT.rightT(departureResponses)
        )
      val request = FakeRequest(
        GET,
        routing.routes.DeparturesRouter.getDeparturesForEori().url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE)),
        AnyContentAsEmpty
      )
      val result = sut.getDeparturesForEori(None)(request)

      status(result) mustBe OK
      contentAsJson(result) mustBe Json.toJson(
        HateoasDepartureIdsResponse(
          departureResponses
        )
      )
    }

    "should return departure not found if persistence service returns 404" in {
      val eori = EORINumber("ERROR")

      when(mockDeparturesPersistenceService.getDeparturesForEori(EORINumber(any()))(any[HeaderCarrier], any[ExecutionContext]))
        .thenAnswer(
          _ => EitherT.leftT(PersistenceError.DeparturesNotFound(eori))
        )

      val request = FakeRequest(
        GET,
        routing.routes.DeparturesRouter.getDeparturesForEori().url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE)),
        AnyContentAsEmpty
      )
      val result = sut.getDeparturesForEori(None)(request)

      status(result) mustBe NOT_FOUND
      contentAsJson(result) mustBe Json.obj(
        "message" -> s"Departure movement IDs for ${eori.value} were not found",
        "code"    -> "NOT_FOUND"
      )
    }

    "should return unexpected error for all other errors" in {
      when(mockDeparturesPersistenceService.getDeparturesForEori(EORINumber(any()))(any[HeaderCarrier], any[ExecutionContext]))
        .thenAnswer(
          _ => EitherT.leftT(PersistenceError.UnexpectedError(None))
        )

      val request = FakeRequest(
        GET,
        routing.routes.DeparturesRouter.getDeparturesForEori().url,
        headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE)),
        AnyContentAsEmpty
      )
      val result = sut.getDeparturesForEori(None)(request)

      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "INTERNAL_SERVER_ERROR",
        "message" -> "Internal server error"
      )
    }

  }

  // for retrieving a single departure/movement
  "GET  /movements/departures/:departureId" - {
    "should return ok with json body of departure" in forAll(
      arbitrary[EORINumber],
      arbitrary[EORINumber],
      arbitrary[MovementId],
      arbitrary[MovementReferenceNumber]
    ) {
      (enrollmentEori, movementEori, departureId, mrn) =>
        val createdTime = OffsetDateTime.now()
        val departureResponse = DepartureResponse(
          departureId,
          enrollmentEori,
          movementEori,
          Some(mrn),
          createdTime,
          createdTime
        )

        when(mockDeparturesPersistenceService.getDeparture(EORINumber(any()), MovementId(any()))(any(), any()))
          .thenAnswer(
            _ => EitherT.rightT(departureResponse)
          )

        val request = FakeRequest(
          GET,
          routing.routes.DeparturesRouter.getDeparture(departureId.value).url,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE)),
          AnyContentAsEmpty
        )
        val result = sut.getDeparture(departureId)(request)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(
          HateoasDepartureResponse(
            departureId,
            DepartureResponse(
              departureId,
              enrollmentEori,
              movementEori,
              Some(mrn),
              createdTime,
              createdTime
            )
          )
        )
    }

    "should return departure not found if persistence service returns 404" in forAll(arbitrary[MovementId]) {
      departureId =>
        val FIRST_INVOCATION_ARGUMENT = 1
        when(mockDeparturesPersistenceService.getDeparture(EORINumber(any()), MovementId(any()))(any(), any()))
          .thenAnswer {
            inv =>
              val d = MovementId(inv.getArgument[String](FIRST_INVOCATION_ARGUMENT))
              EitherT.leftT(PersistenceError.DepartureNotFound(d))
          }

        val request = FakeRequest(
          GET,
          routing.routes.DeparturesRouter.getDeparture(departureId.value).url,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE)),
          AnyContentAsEmpty
        )
        val result = sut.getDeparture(departureId)(request)

        status(result) mustBe NOT_FOUND
    }

    "should return unexpected error for all other errors" in forAll(arbitrary[MovementId]) {
      departureId =>
        when(mockDeparturesPersistenceService.getDeparture(EORINumber(any()), MovementId(any()))(any(), any()))
          .thenAnswer {
            _ =>
              EitherT.leftT(PersistenceError.UnexpectedError(None))
          }

        val request = FakeRequest(
          GET,
          routing.routes.DeparturesRouter.getDeparture(departureId.value).url,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE)),
          AnyContentAsEmpty
        )
        val result = sut.getDeparture(departureId)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }

  "POST /movements/departures/:departureId/messages" - {
    "with content type set to application/xml" - {

      // For the content length headers, we have to ensure that we send something
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
      )

      "must return Accepted when body length is within limits and is considered valid" in {
        when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]])(any(), any()))
          .thenReturn(messageDataEither)

        when(mockValidationService.validateXml(eqTo(MessageType.DeclarationAmendment), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )
        when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

        when(
          mockDeparturesPersistenceService
            .updateDeparture(any[String].asInstanceOf[MovementId], any[String].asInstanceOf[MessageType], any[Source[ByteString, _]]())(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
        ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, UpdateMovementResponse](UpdateMovementResponse(MessageId("456")))))

        val request = fakeAttachDepartures(method = "POST", body = singleUseStringSource(CC013C.mkString), headers = standardHeaders)
        val result  = sut.attachMessage(departureId)(request)
        status(result) mustBe ACCEPTED

        contentAsJson(result) mustBe Json.toJson(HateoasDepartureUpdateMovementResponse(departureId, MessageId("456")))

        verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationAmendment), any(), eqTo(MimeTypes.XML))(any(), any())
        verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.DeclarationAmendment), any())(any(), any())
        verify(mockDeparturesPersistenceService, times(1)).updateDeparture(MovementId(any()), any(), any())(any(), any())
        verify(mockRouterService, times(1)).send(eqTo(MessageType.DeclarationAmendment), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
          any(),
          any()
        )
      }

      "must return Bad Request when body is not an XML document" in {
        when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]]())(any(), any()))
          .thenAnswer(
            _ => EitherT.leftT(ExtractionError.MalformedInput())
          )

        val request = fakeAttachDepartures(method = "POST", body = singleUseStringSource("notxml"), headers = standardHeaders)
        val result  = sut.attachMessage(departureId)(request)
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> "Input was malformed"
        )
      }

      "must return Internal Service Error if the persistence service reports an error" in {
        when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]])(any(), any()))
          .thenReturn(messageDataEither)
        when(mockValidationService.validateXml(eqTo(MessageType.DeclarationAmendment), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )
        when(
          mockDeparturesPersistenceService
            .updateDeparture(any[String].asInstanceOf[MovementId], any[String].asInstanceOf[MessageType], any[Source[ByteString, _]]())(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
        ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, UpdateMovementResponse](UpdateMovementResponse(MessageId("456")))))

        val sut = new V2DeparturesControllerImpl(
          Helpers.stubControllerComponents(),
          FakeAuthNewEnrolmentOnlyAction(EORINumber("nope")),
          mockValidationService,
          mockConversionService,
          mockDeparturesPersistenceService,
          mockRouterService,
          mockAuditService,
          FakeMessageSizeActionProvider,
          new TestMetrics(),
          mockXmlParsingService,
          mockJsonParsingService
        )

        val request  = fakeAttachDepartures("POST", body = Source.single(ByteString(CC013C.mkString, StandardCharsets.UTF_8)), headers = standardHeaders)
        val response = sut.attachMessage(departureId)(request)

        status(response) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(response) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )
      }

    }

    "with content type set to application/json" - {

      def setup(
        extractMessageTypeXml: EitherT[Future, ExtractionError, MessageType] = messageDataEither,
        extractMessageTypeJson: EitherT[Future, ExtractionError, MessageType] = messageDataEither,
        validateXml: EitherT[Future, FailedToValidateError, Unit] = EitherT.rightT(()),
        validateJson: EitherT[Future, FailedToValidateError, Unit] = EitherT.rightT(()),
        conversion: EitherT[Future, ConversionError, Source[ByteString, _]] = EitherT.rightT(singleUseStringSource(CC013C.mkString)),
        persistence: EitherT[Future, PersistenceError, UpdateMovementResponse] = EitherT.rightT(UpdateMovementResponse(MessageId("456"))),
        router: EitherT[Future, RouterError, Unit] = EitherT.rightT(())
      ): Unit = {

        when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]])(any(), any())).thenReturn(extractMessageTypeXml)
        when(mockJsonParsingService.extractMessageType(any[Source[ByteString, _]])(any(), any())).thenReturn(extractMessageTypeJson)

        when(mockValidationService.validateXml(eqTo(MessageType.DeclarationAmendment), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => validateXml
          )

        when(
          mockValidationService.validateJson(eqTo(MessageType.DeclarationAmendment), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenAnswer(
            _ => validateJson
          )

        when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON))(any(), any())).thenReturn(Future.successful(()))

        when(mockConversionService.jsonToXml(any(), any())(any(), any(), any())).thenReturn(conversion)

        when(
          mockDeparturesPersistenceService
            .updateDeparture(any[String].asInstanceOf[MovementId], any[String].asInstanceOf[MessageType], any[Source[ByteString, _]]())(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
        ).thenReturn(persistence)

        when(
          mockRouterService.send(
            any[String].asInstanceOf[MessageType],
            any[String].asInstanceOf[EORINumber],
            any[String].asInstanceOf[MovementId],
            any[String].asInstanceOf[MessageId],
            any()
          )(any(), any())
        )
          .thenReturn(router)

      }

      // For the content length headers, we have to ensure that we send something
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON, HeaderNames.CONTENT_LENGTH -> "1000")
      )

      def fakeJsonAttachRequest(content: String = CC013Cjson): Request[Source[ByteString, _]] =
        fakeAttachDepartures(method = "POST", body = singleUseStringSource(content), headers = standardHeaders)

      "must return Accepted when body length is within limits and is considered valid" in {

        setup()

        val request = fakeJsonAttachRequest()
        val result  = sut.attachMessage(departureId)(request)
        status(result) mustBe ACCEPTED

        contentAsJson(result) mustBe Json.toJson(HateoasDepartureUpdateMovementResponse(departureId, MessageId("456")))

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationAmendment), any())(any(), any())
        verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationAmendment), any(), eqTo(MimeTypes.JSON))(any(), any())
        verify(mockConversionService, times(1)).jsonToXml(eqTo(MessageType.DeclarationAmendment), any())(any(), any(), any())
        verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.DeclarationAmendment), any())(any(), any())
        verify(mockDeparturesPersistenceService, times(1)).updateDeparture(MovementId(any()), any(), any())(any(), any())
        verify(mockRouterService, times(1)).send(eqTo(MessageType.DeclarationAmendment), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
          any(),
          any()
        )
      }

      "must return Bad Request when body is not an JSON document" in {

        setup(extractMessageTypeJson = EitherT.leftT(ExtractionError.MalformedInput()))

        val request = fakeJsonAttachRequest("notJson")
        val result  = sut.attachMessage(departureId)(request)
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> "Input was malformed"
        )
      }

      "must return Bad Request when unable to find a message type " in {
        setup(extractMessageTypeJson = EitherT.leftT(MessageTypeNotFound("CC013C")))

        val request = fakeJsonAttachRequest()
        val result  = sut.attachMessage(departureId)(request)
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> "CC013C is not a valid message type"
        )
      }

      "must return BadRequest when JsonValidation service doesn't recognise message type" in {

        setup(validateJson = EitherT.leftT(InvalidMessageTypeError("CC013C")))

        val request = fakeJsonAttachRequest()
        val result  = sut.attachMessage(departureId)(request)
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "BAD_REQUEST",
          "message" -> "CC013C is not a valid message type"
        )
      }

      "must return BadRequest when json fails to validate" in {

        setup(validateJson = EitherT.leftT(JsonSchemaFailedToValidateError(NonEmptyList.one(JsonValidationError("sample", "message")))))

        val request = fakeJsonAttachRequest()
        val result  = sut.attachMessage(departureId)(request)
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "message"          -> "Request failed schema validation",
          "code"             -> ErrorCode.SchemaValidation.code,
          "validationErrors" -> Json.arr(Json.obj("schemaPath" -> "sample", "message" -> "message"))
        )
      }

      "must return InternalServerError when Unexpected error validating the json" in {

        setup(validateJson = EitherT.leftT(FailedToValidateError.UnexpectedError(None)))

        val request = fakeJsonAttachRequest()
        val result  = sut.attachMessage(departureId)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "must return InternalServerError when Unexpected error converting the json to xml" in {
        setup(conversion = EitherT.leftT(ConversionError.UnexpectedError(None)))

        val request = fakeJsonAttachRequest()
        val result  = sut.attachMessage(departureId)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "must return InternalServerError when xml failed validation" in {
        setup(validateXml = EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList.one(XmlValidationError(1, 1, "message")))))

        val request = fakeJsonAttachRequest()
        val result  = sut.attachMessage(departureId)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "must return BadRequest when message type not recognised by xml validator" in {
        setup(validateXml = EitherT.leftT(FailedToValidateError.InvalidMessageTypeError("test")))

        val request = fakeJsonAttachRequest()
        val result  = sut.attachMessage(departureId)(request)

        status(result) mustBe BAD_REQUEST
      }

      "must return InternalServerError when unexpected error from xml validator" in {
        setup(validateXml = EitherT.leftT(FailedToValidateError.UnexpectedError(None)))

        val request = fakeJsonAttachRequest()
        val result  = sut.attachMessage(departureId)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "must return NotFound when Departure not found by Persistence" in {
        setup(persistence = EitherT.leftT(PersistenceError.DepartureNotFound(departureId)))

        val request = fakeJsonAttachRequest()
        val result  = sut.attachMessage(departureId)(request)

        status(result) mustBe NOT_FOUND
      }

      "must return InternalServerError when Persistence return Unexpected Error" in {
        setup(persistence = EitherT.leftT(PersistenceError.UnexpectedError(None)))

        val request = fakeJsonAttachRequest()
        val result  = sut.attachMessage(departureId)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "must return InternalServerError when router throws unexpected error" in {
        setup(router = EitherT.leftT(RouterError.UnexpectedError(None)))

        val request = fakeJsonAttachRequest()
        val result  = sut.attachMessage(departureId)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
      }

      "must return BadRequest when router returns BadRequest" in {
        setup(router = EitherT.leftT(RouterError.UnrecognisedOffice))

        val request = fakeJsonAttachRequest()
        val result  = sut.attachMessage(departureId)(request)

        status(result) mustBe BAD_REQUEST
      }

    }

    "must return UNSUPPORTED_MEDIA_TYPE when the content type is invalid" in {
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "invalid", HeaderNames.CONTENT_LENGTH -> "1000")
      )

      val request  = fakeAttachDepartures("POST", body = Source.single(ByteString(CC013C.mkString, StandardCharsets.UTF_8)), headers = standardHeaders)
      val response = sut.attachMessage(departureId)(request)
      status(response) mustBe UNSUPPORTED_MEDIA_TYPE
      contentAsJson(response) mustBe Json.obj(
        "code"    -> "UNSUPPORTED_MEDIA_TYPE",
        "message" -> "Content-type header invalid is not supported!"
      )
    }

  }

  def validateXmlOkStub(): OngoingStubbing[EitherT[Future, FailedToValidateError, Unit]] =
    when(
      mockValidationService
        .validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
    ).thenAnswer {
      _ =>
        EitherT.rightT(())
    }

  def validateXmlNotOkStub(): OngoingStubbing[EitherT[Future, FailedToValidateError, Unit]] =
    when(
      mockValidationService
        .validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
    ).thenAnswer {
      _ =>
        EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList(XmlValidationError(1, 1, "invalid XML"), Nil)))
    }
}
