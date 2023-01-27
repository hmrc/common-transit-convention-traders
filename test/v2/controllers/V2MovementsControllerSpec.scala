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
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.reset
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.mockito.invocation.InvocationOnMock
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
import utils.TestMetrics
import v2.base.CommonGenerators
import v2.base.TestActorSystem
import v2.base.TestSourceProvider
import v2.fakes.controllers.actions.FakeAcceptHeaderActionProvider
import v2.fakes.controllers.actions.FakeAuthNewEnrolmentOnlyAction
import v2.fakes.controllers.actions.FakeMessageSizeActionProvider
import v2.fakes.utils.FakePreMaterialisedFutureProvider
import v2.models.MovementId
import v2.models._
import v2.models.errors.ExtractionError.MessageTypeNotFound
import v2.models.errors.FailedToValidateError.InvalidMessageTypeError
import v2.models.errors.FailedToValidateError.JsonSchemaFailedToValidateError
import v2.models.errors._
import v2.models.request.MessageType
import v2.models.responses.MessageSummary
import v2.models.responses.MovementResponse
import v2.models.responses.MovementSummary
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

class V2MovementsControllerSpec
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
      <SynIdeMES1>UNOC</SynIdeMES1>
    </CC015C>

  def CC007C: NodeSeq =
    <CC007C>
      <SynIdeMES1>UNOC</SynIdeMES1>
    </CC007C>

  def CC013C: NodeSeq =
    <CC013C>
      <test>testxml</test>
    </CC013C>

  def CC044C: NodeSeq =
    <contentXml>
      <test>testxml</test>
    </contentXml>

  val CC015Cjson = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
  val CC007Cjson = Json.stringify(Json.obj("CC007" -> Json.obj("SynIdeMES1" -> "UNOC")))
  val CC013Cjson = Json.stringify(Json.obj("CC013" -> Json.obj("field" -> "value")))
  val CC044Cjson = Json.stringify(Json.obj("CC044" -> Json.obj("field" -> "value")))

  val mockValidationService           = mock[ValidationService]
  val mockMovementsPersistenceService = mock[MovementsService]
  val mockRouterService               = mock[RouterService]
  val mockAuditService                = mock[AuditingService]
  val mockConversionService           = mock[ConversionService]
  val mockXmlParsingService           = mock[XmlMessageParsingService]
  val mockJsonParsingService          = mock[JsonMessageParsingService]
  val mockResponseFormatterService    = mock[ResponseFormatterService]
  val mockPushNotificationService     = mock[PushNotificationsService]
  val mockUpscanService               = mock[UpscanService]
  implicit val temporaryFileCreator   = SingletonTemporaryFileCreator

  lazy val sut: V2MovementsController = new V2MovementsControllerImpl(
    Helpers.stubControllerComponents(),
    FakeAuthNewEnrolmentOnlyAction(),
    mockValidationService,
    mockConversionService,
    mockMovementsPersistenceService,
    mockRouterService,
    mockAuditService,
    mockPushNotificationService,
    FakeMessageSizeActionProvider,
    FakeAcceptHeaderActionProvider,
    new TestMetrics(),
    mockXmlParsingService,
    mockJsonParsingService,
    mockResponseFormatterService,
    mockUpscanService,
    FakePreMaterialisedFutureProvider
  )

  implicit val timeout: Timeout = 5.seconds

  def fakeHeaders(contentType: String) = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> contentType))

  def fakeCreateMovementRequest[A](
    method: String,
    headers: FakeHeaders,
    body: A,
    movementType: MovementType
  ): Request[A] =
    FakeRequest(
      method = method,
      uri =
        if (movementType == MovementType.Departure) routing.routes.DeparturesRouter.submitDeclaration().url
        else routing.routes.ArrivalsRouter.createArrivalNotification().url,
      headers = headers,
      body = body
    )

  def fakeAttachMessageRequest[A](
    method: String,
    headers: FakeHeaders,
    body: A,
    movementType: MovementType
  ): Request[A] =
    FakeRequest(
      method = method,
      uri =
        if (movementType == MovementType.Departure) routing.routes.DeparturesRouter.attachMessage("123").url
        else routing.routes.ArrivalsRouter.attachMessage("123").url,
      headers = headers,
      body = body
    )

  override def beforeEach(): Unit = {
    reset(mockValidationService)
    reset(mockConversionService)
    reset(mockMovementsPersistenceService)
    reset(mockRouterService)
    reset(mockAuditService)
    reset(mockXmlParsingService)
    reset(mockJsonParsingService)
    reset(mockResponseFormatterService)
    reset(mockPushNotificationService)
    reset(mockUpscanService)
  }

  def testSinkJson(rootNode: String): Sink[ByteString, Future[Either[FailedToValidateError, Unit]]] =
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
                if ((jsVal \ rootNode).isDefined) Right(())
                else
                  Left(
                    FailedToValidateError
                      .JsonSchemaFailedToValidateError(validationErrors =
                        NonEmptyList(JsonValidationError(rootNode, s"$rootNode expected but not present"), Nil)
                      )
                  )
            }
      }
      .toMat(Sink.last)(Keep.right)

  def jsonValidationMockAnswer(movementType: MovementType) = (invocation: InvocationOnMock) =>
    EitherT(
      invocation
        .getArgument[Source[ByteString, _]](1)
        .fold(ByteString())(
          (current, next) => current ++ next
        )
        .runWith(testSinkJson(if (movementType == MovementType.Departure) "CC015" else "CC007"))
    )

  // Version 2
  "for a departure declaration with accept header set to application/vnd.hmrc.2.0+json (version two)" - {

    "with content type set to application/xml" - {

      // For the content length headers, we have to ensure that we send something
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
      )

      "must return Accepted when body length is within limits and is considered valid" in forAll(
        arbitraryMovementResponse(true, true).arbitrary
      ) {
        movementResponse =>
          beforeEach()
          when(mockValidationService.validateXml(any[MessageType], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(any(), any(), any())(any(), any())).thenReturn(Future.successful(()))

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(
            mockRouterService.send(
              any[String].asInstanceOf[MessageType],
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId],
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.rightT(())
          )

          when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(movementResponse.boxResponse.get)
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015C.mkString), MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(
            HateoasNewMovementResponse(movementResponse, None, MovementType.Departure)
          )

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML))(any(), any())
          verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockMovementsPersistenceService, times(1)).createMovement(EORINumber(any()), eqTo(MovementType.Departure), any())(any(), any())
          verify(mockRouterService, times(1)).send(eqTo(MessageType.DeclarationData), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
            any(),
            any()
          )
          verify(mockPushNotificationService, times(1)).associate(MovementId(any()), eqTo(MovementType.Departure), any())(any(), any())
      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryMovementResponse(true, false).arbitrary
      ) {
        movementResponse =>
          beforeEach()
          when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(
            mockRouterService.send(
              any[String].asInstanceOf[MessageType],
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId],
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.rightT(())
          )

          when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
            .thenAnswer(
              _ => EitherT.leftT(PushNotificationError.UnexpectedError(None))
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015C.mkString), MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse, None, MovementType.Departure))

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML))(any(), any())
          verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockMovementsPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockRouterService, times(1)).send(eqTo(MessageType.DeclarationData), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
            any(),
            any()
          )
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Departure), any())(any(), any())

      }

      "must return Bad Request when body is an XML document that would fail schema validation" in {
        when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList(XmlValidationError(1, 1, "an error"), Nil)))
          )

        val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(<test></test>.mkString), MovementType.Departure)
        val result  = sut.createMovement(MovementType.Departure)(request)
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

        when(
          mockMovementsPersistenceService
            .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
        ).thenAnswer(
          _ => EitherT.leftT(PersistenceError.UnexpectedError(None))
        )

        val request =
          fakeCreateMovementRequest("POST", standardHeaders, Source.single(ByteString(CC015C.mkString, StandardCharsets.UTF_8)), MovementType.Departure)
        val response = sut.createMovement(MovementType.Departure)(request)

        status(response) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(response) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )
      }

      "must return Internal Service Error if the router service reports an error" in forAll(
        arbitraryMovementResponse(true, true).arbitrary
      ) {
        movementResponse =>
          beforeEach()

          when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON))(any(), any())).thenReturn(Future.successful(()))

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, MovementResponse](movementResponse)))

          when(
            mockRouterService.send(
              any[String].asInstanceOf[MessageType],
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId],
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.leftT(RouterError.UnexpectedError(None))
          )

          when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(movementResponse.boxResponse.get)
            )

          val request =
            fakeCreateMovementRequest("POST", standardHeaders, Source.single(ByteString(CC015C.mkString, StandardCharsets.UTF_8)), MovementType.Departure)
          val response = sut.createMovement(MovementType.Departure)(request)

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

      "must return Accepted when body length is within limits and is considered valid" in forAll(
        arbitraryMovementResponse(true, true).arbitrary
      ) {
        movementResponse =>
          beforeEach()

          when(
            mockValidationService
              .validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            _ =>
              EitherT.rightT(())
          }

          when(
            mockValidationService
              .validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            invocation =>
              jsonValidationMockAnswer(MovementType.Departure)(invocation)
          }
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON))(any(), any())).thenReturn(Future.successful(()))

          when(
            mockConversionService
              .jsonToXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(
                any[HeaderCarrier],
                any[ExecutionContext],
                any[Materializer]
              )
          ).thenReturn {
            val source = singleUseStringSource(CC015C.mkString)
            EitherT.rightT[Future, ConversionError](source)
          }

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(movementResponse.boxResponse.get)
            )

          when(
            mockRouterService.send(
              any[String].asInstanceOf[MessageType],
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId],
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.rightT(())
          )

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015Cjson), MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe ACCEPTED
          contentAsJson(result) mustBe Json.toJson(
            HateoasNewMovementResponse(movementResponse, None, MovementType.Departure)
          )

          verify(mockConversionService, times(1)).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
          verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockConversionService).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.JSON))(any(), any())
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Departure), any())(any(), any())
      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryMovementResponse(true, false).arbitrary
      ) {
        movementResponse =>
          beforeEach()

          when(
            mockValidationService
              .validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            _ =>
              EitherT.rightT(())
          }

          when(
            mockValidationService
              .validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            invocation =>
              jsonValidationMockAnswer(MovementType.Departure)(invocation)
          }
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON))(any(), any())).thenReturn(Future.successful(()))

          when(
            mockConversionService
              .jsonToXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(
                any[HeaderCarrier],
                any[ExecutionContext],
                any[Materializer]
              )
          ).thenReturn {
            val source = singleUseStringSource(CC015C.mkString)
            EitherT.rightT[Future, ConversionError](source)
          }

          when(
            mockRouterService.send(
              any[String].asInstanceOf[MessageType],
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId],
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.rightT(())
          )

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(MovementId(anyString()), any(), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.leftT(PushNotificationError.UnexpectedError(None))
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015Cjson), MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse, None, MovementType.Departure))

          verify(mockConversionService, times(1)).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
          verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockConversionService).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.JSON))(any(), any())
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Departure), any())(any(), any())
      }

      "must return Bad Request when body is not an JSON document" in {
        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(MovementType.Departure)(invocation)
        }

        val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource("notjson"), MovementType.Departure)
        val result  = sut.createMovement(MovementType.Departure)(request)
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
            jsonValidationMockAnswer(MovementType.Departure)(invocation)
        }

        val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource("{}"), MovementType.Departure)
        val result  = sut.createMovement(MovementType.Departure)(request)
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
            jsonValidationMockAnswer(MovementType.Departure)(invocation)
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

        val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015Cjson), MovementType.Departure)
        val result  = sut.createMovement(MovementType.Departure)(request)
        status(result) mustBe INTERNAL_SERVER_ERROR

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
      }

      "must return Internal Service Error after JSON to XML conversion if the XML validation service reports an error" in {
        when(
          mockValidationService
            .validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          _ =>
            EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList(XmlValidationError(1, 1, "invalid XML"), Nil)))
        }

        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(MovementType.Departure)(invocation)
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

        val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015Cjson), MovementType.Departure)
        val result  = sut.createMovement(MovementType.Departure)(request)

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
        when(
          mockValidationService
            .validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          _ =>
            EitherT.rightT(())
        }

        when(
          mockValidationService
            .validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(MovementType.Departure)(invocation)
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
          mockMovementsPersistenceService
            .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]])(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
        )
          .thenAnswer(
            _ => EitherT.leftT(PersistenceError.UnexpectedError(None))
          )

        val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015Cjson), MovementType.Departure)
        val result  = sut.createMovement(MovementType.Departure)(request)
        status(result) mustBe INTERNAL_SERVER_ERROR

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
        verify(mockMovementsPersistenceService).createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any(), any())
      }

      "must return Internal Service Error if the router service reports an error" in forAll(
        arbitraryMovementResponse(true, true).arbitrary
      ) {
        movementResponse =>
          beforeEach()

          when(
            mockValidationService
              .validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            _ =>
              EitherT.rightT(())
          }

          when(
            mockValidationService
              .validateJson(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            invocation =>
              jsonValidationMockAnswer(MovementType.Departure)(invocation)
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
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]])(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer(
              _ =>
                EitherT.rightT(
                  movementResponse
                )
            )

          when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(movementResponse.boxResponse.get)
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
              _ =>
                EitherT.leftT(RouterError.UnexpectedError(None))
            }

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015Cjson), MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe INTERNAL_SERVER_ERROR

          verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockConversionService).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
          verify(mockMovementsPersistenceService).createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any(), any())
          verify(mockRouterService).send(
            eqTo(MessageType.DeclarationData),
            any[String].asInstanceOf[EORINumber],
            any[String].asInstanceOf[MovementId],
            any[String].asInstanceOf[MessageId],
            any[Source[ByteString, _]]
          )(any[ExecutionContext], any[HeaderCarrier])
      }

    }

    "with content type set to None" - {

      // For the content length headers, we have to ensure that we send something
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_LENGTH -> "1000")
      )

      "must return Accepted when call to upscan is success" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary,
        arbitraryMovementResponse(false, true).arbitrary
      ) {
        (upscanResponse, movementResponse) =>
          when(mockUpscanService.upscanInitiate()(any(), any()))
            .thenAnswer {
              _ => EitherT.rightT(upscanResponse)
            }

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(MovementId(anyString()), any(), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(movementResponse.boxResponse)
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, Source.empty[ByteString], MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(
            HateoasNewMovementResponse(movementResponse, Some(upscanResponse), MovementType.Departure)
          )

          verify(mockUpscanService, times(1)).upscanInitiate()(any(), any())
          verify(mockMovementsPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Departure), any())(any(), any())
      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary,
        arbitraryMovementResponse(false, false).arbitrary
      ) {
        (upscanResponse, movementResponse) =>
          when(mockUpscanService.upscanInitiate()(any(), any()))
            .thenAnswer {
              _ => EitherT.rightT(upscanResponse)
            }

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(MovementId(anyString()), any(), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.leftT(PushNotificationError.UnexpectedError(None))
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, Source.empty[ByteString], MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse, Some(upscanResponse), MovementType.Departure))

          verify(mockUpscanService, times(1)).upscanInitiate()(any(), any())
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML))(any(), any())
          verify(mockMovementsPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Departure), any())(any(), any())

      }

      "must return Internal Service Error if the persistence service reports an error" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary
      ) {
        upscanResponse =>
          when(mockUpscanService.upscanInitiate()(any(), any()))
            .thenAnswer {
              _ => EitherT.rightT(upscanResponse)
            }

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer(
            _ => EitherT.leftT(PersistenceError.UnexpectedError(None))
          )

          val request =
            fakeCreateMovementRequest("POST", standardHeaders, Source.empty[ByteString], MovementType.Departure)
          val response = sut.createMovement(MovementType.Departure)(request)

          status(response) mustBe INTERNAL_SERVER_ERROR
          contentAsJson(response) mustBe Json.obj(
            "code"    -> "INTERNAL_SERVER_ERROR",
            "message" -> "Internal server error"
          )
      }

      "must return Internal Service Error if the upscan service reports an error" in {

        when(mockUpscanService.upscanInitiate()(any(), any()))
          .thenAnswer {
            _ => EitherT.leftT(UpscanInitiateError.UnexpectedError(None))
          }

        val request =
          fakeCreateMovementRequest("POST", standardHeaders, Source.empty[ByteString], MovementType.Departure)
        val response = sut.createMovement(MovementType.Departure)(request)

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
      val request = fakeCreateMovementRequest("POST", standardHeaders, Source.single(json), MovementType.Departure)
      val result  = sut.createMovement(MovementType.Departure)(request)
      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "UNSUPPORTED_MEDIA_TYPE",
        "message" -> "Content-type header invalid is not supported!"
      )

    }
  }

  "for an arrival notification with accept header set to application/vnd.hmrc.2.0+json (version two)" - {
    "with content type set to application/xml" - {

      // For the content length headers, we have to ensure that we send something
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
      )

      "must return Accepted when body length is within limits and is considered valid" in forAll(
        arbitraryMovementResponse(true, true).arbitrary
      ) {
        movementResponse =>
          beforeEach()
          when(
            mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(MovementId(anyString()), any(), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(movementResponse.boxResponse.get)
            )

          when(
            mockRouterService.send(
              any[String].asInstanceOf[MessageType],
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId],
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.rightT(())
          )

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007C.mkString), MovementType.Arrival)
          val result  = sut.createMovement(MovementType.Arrival)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse, None, MovementType.Arrival))

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML))(any(), any())
          verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.ArrivalNotification), any())(any(), any())
          verify(mockMovementsPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockRouterService, times(1)).send(eqTo(MessageType.ArrivalNotification), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
            any(),
            any()
          )
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Arrival), any())(any(), any())
      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryMovementResponse(true, false).arbitrary
      ) {
        movementResponse =>
          beforeEach()
          when(
            mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => EitherT.rightT(())
            )
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(
            mockRouterService.send(
              any[String].asInstanceOf[MessageType],
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId],
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.rightT(())
          )

          when(mockPushNotificationService.associate(MovementId(anyString()), any(), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.leftT(PushNotificationError.UnexpectedError(None))
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007C.mkString), MovementType.Arrival)
          val result  = sut.createMovement(MovementType.Arrival)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse, None, MovementType.Arrival))

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML))(any(), any())
          verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.ArrivalNotification), any())(any(), any())
          verify(mockMovementsPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockRouterService, times(1)).send(eqTo(MessageType.ArrivalNotification), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
            any(),
            any()
          )
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Arrival), any())(any(), any())
      }

      "must return Bad Request when body is an XML document that would fail schema validation" in {
        when(mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList(XmlValidationError(1, 1, "an error"), Nil)))
          )
        val request =
          fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(<test></test>.mkString), MovementType.Arrival)
        val result = sut.createMovement(MovementType.Arrival)(request)
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

        when(
          mockMovementsPersistenceService
            .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
        ).thenAnswer(
          _ => EitherT.leftT(PersistenceError.UnexpectedError(None))
        )

        val request  = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007C.mkString), MovementType.Arrival)
        val response = sut.createMovement(MovementType.Arrival)(request)

        status(response) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(response) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )
      }

      "must return Internal Service Error if the router service reports an error" in forAll(
        arbitraryMovementResponse(true, true).arbitrary
      ) {
        movementResponse =>
          beforeEach()

          when(
            mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(
            mockRouterService.send(
              any[String].asInstanceOf[MessageType],
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId],
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.leftT(RouterError.UnexpectedError(None))
          )

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, MovementResponse](movementResponse)))

          when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(movementResponse.boxResponse.get)
            )

          val request  = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007C.mkString), MovementType.Arrival)
          val response = sut.createMovement(MovementType.Arrival)(request)

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

      "must return Accepted when body length is within limits and is considered valid" in forAll(
        arbitraryMovementResponse(true, true).arbitrary
      ) {
        movementResponse =>
          beforeEach()
          when(
            mockValidationService
              .validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            _ =>
              EitherT.rightT(())
          }

          when(
            mockValidationService
              .validateJson(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            invocation =>
              jsonValidationMockAnswer(MovementType.Arrival)(invocation)
          }
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON))(any(), any())).thenReturn(Future.successful(()))

          when(
            mockConversionService
              .jsonToXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(
                any[HeaderCarrier],
                any[ExecutionContext],
                any[Materializer]
              )
          ).thenReturn {
            val source = singleUseStringSource(CC007C.mkString)
            EitherT.rightT[Future, ConversionError](source)
          }

          when(
            mockRouterService.send(
              any[String].asInstanceOf[MessageType],
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId],
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.rightT(())
          )

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(movementResponse.boxResponse.get)
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007Cjson), MovementType.Arrival)
          val result  = sut.createMovement(MovementType.Arrival)(request)

          status(result) mustBe ACCEPTED
          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse, None, MovementType.Arrival))
      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryMovementResponse(true, false).arbitrary
      ) {
        movementResponse =>
          beforeEach()
          when(
            mockValidationService
              .validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            _ =>
              EitherT.rightT(())
          }

          when(
            mockValidationService
              .validateJson(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            invocation =>
              jsonValidationMockAnswer(MovementType.Arrival)(invocation)
          }
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON))(any(), any())).thenReturn(Future.successful(()))

          when(
            mockConversionService
              .jsonToXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(
                any[HeaderCarrier],
                any[ExecutionContext],
                any[Materializer]
              )
          ).thenReturn {
            val source = singleUseStringSource(CC007C.mkString)
            EitherT.rightT[Future, ConversionError](source)
          }

          when(
            mockRouterService.send(
              any[String].asInstanceOf[MessageType],
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId],
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.rightT(())
          )

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(MovementId(anyString()), any(), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.leftT(PushNotificationError.UnexpectedError(None))
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007Cjson), MovementType.Arrival)
          val result  = sut.createMovement(MovementType.Arrival)(request)

          status(result) mustBe ACCEPTED
          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse, None, MovementType.Arrival))
      }

      "must return Bad Request when body is not an JSON document" in {
        when(
          mockValidationService
            .validateJson(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(MovementType.Arrival)(invocation)
        }

        val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource("notjson"), MovementType.Arrival)

        val result = sut.createMovement(MovementType.Arrival)(request)
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

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.ArrivalNotification), any())(any(), any())
      }

      "must return Bad Request when body is an JSON document that would fail schema validation" in {
        when(
          mockValidationService
            .validateJson(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(MovementType.Arrival)(invocation)
        }

        val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource("{}"), MovementType.Arrival)

        val result = sut.createMovement(MovementType.Arrival)(request)
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "SCHEMA_VALIDATION",
          "message" -> "Request failed schema validation",
          "validationErrors" -> Seq(
            Json.obj(
              "schemaPath" -> "CC007",
              "message"    -> "CC007 expected but not present"
            )
          )
        )

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.ArrivalNotification), any())(any(), any())
      }

      "must return Internal Service Error if the JSON to XML conversion service reports an error" in {
        when(
          mockValidationService
            .validateJson(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(MovementType.Arrival)(invocation)
        }
        val jsonToXmlConversionError = (_: InvocationOnMock) => EitherT.leftT(ConversionError.UnexpectedError(None))

        when(
          mockConversionService
            .jsonToXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(
              any[HeaderCarrier],
              any[ExecutionContext],
              any[Materializer]
            )
        ).thenAnswer {
          invocation =>
            jsonToXmlConversionError(invocation)
        }

        val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007Cjson), MovementType.Arrival)

        val result = sut.createMovement(MovementType.Arrival)(request)
        status(result) mustBe INTERNAL_SERVER_ERROR

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.ArrivalNotification), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.ArrivalNotification), any())(any(), any(), any())
      }

      "must return Internal Service Error after JSON to XML conversion if the XML validation service reports an error" in {
        when(
          mockValidationService
            .validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          _ =>
            EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList(XmlValidationError(1, 1, "invalid XML"), Nil)))
        }

        when(
          mockValidationService
            .validateJson(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(MovementType.Arrival)(invocation)
        }

        when(
          mockConversionService
            .jsonToXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(
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

        val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007Cjson), MovementType.Arrival)
        val result  = sut.createMovement(MovementType.Arrival)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.ArrivalNotification), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.ArrivalNotification), any())(any(), any(), any())
        verify(mockValidationService).validateXml(eqTo(MessageType.ArrivalNotification), any())(any(), any())
      }

      "must return Internal Service Error if the persistence service reports an error" in {
        when(
          mockValidationService
            .validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          _ =>
            EitherT.rightT(())
        }

        when(
          mockValidationService
            .validateJson(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
        ).thenAnswer {
          invocation =>
            jsonValidationMockAnswer(MovementType.Arrival)(invocation)
        }

        when(
          mockConversionService
            .jsonToXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(
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
          mockMovementsPersistenceService
            .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]])(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
        )
          .thenAnswer(
            _ => EitherT.leftT(PersistenceError.UnexpectedError(None))
          )

        val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007Cjson), MovementType.Arrival)

        val result = sut.createMovement(MovementType.Arrival)(request)
        status(result) mustBe INTERNAL_SERVER_ERROR

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.ArrivalNotification), any())(any(), any())
        verify(mockConversionService).jsonToXml(eqTo(MessageType.ArrivalNotification), any())(any(), any(), any())
        verify(mockMovementsPersistenceService).createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any(), any())
      }

      "must return Internal Service Error if the router service reports an error" in forAll(
        arbitraryMovementResponse(true, true).arbitrary
      ) {
        movementResponse =>
          beforeEach()
          when(
            mockValidationService
              .validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            _ =>
              EitherT.rightT(())
          }

          when(
            mockValidationService
              .validateJson(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            invocation =>
              jsonValidationMockAnswer(MovementType.Arrival)(invocation)
          }

          when(
            mockConversionService
              .jsonToXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(
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
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]])(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer(
              _ =>
                EitherT.rightT(
                  movementResponse
                )
            )

          when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(movementResponse.boxResponse.get)
            )

          when(
            mockRouterService.send(
              eqTo(MessageType.ArrivalNotification),
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId],
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          )
            .thenAnswer {
              _ =>
                EitherT.leftT(RouterError.UnexpectedError(None))
            }

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007Cjson), MovementType.Arrival)

          val result = sut.createMovement(MovementType.Arrival)(request)
          status(result) mustBe INTERNAL_SERVER_ERROR

          verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.ArrivalNotification), any())(any(), any())
          verify(mockConversionService).jsonToXml(eqTo(MessageType.ArrivalNotification), any())(any(), any(), any())
          verify(mockMovementsPersistenceService).createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any(), any())
          verify(mockRouterService).send(
            eqTo(MessageType.ArrivalNotification),
            any[String].asInstanceOf[EORINumber],
            any[String].asInstanceOf[MovementId],
            any[String].asInstanceOf[MessageId],
            any[Source[ByteString, _]]
          )(any[ExecutionContext], any[HeaderCarrier])
      }

    }

    "when the content type is not present" - {

      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json")
      )

      "must return Accepted if the call to upscan and the persistence service succeeds" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary,
        arbitraryMovementResponse(false, true).arbitrary
      ) {
        (upscanResponse, movementResponse) =>
          when(mockUpscanService.upscanInitiate()(any(), any()))
            .thenAnswer {
              _ => EitherT.rightT(upscanResponse)
            }

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(MovementId(anyString()), any(), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(movementResponse.boxResponse.get)
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, Source.empty[ByteString], MovementType.Arrival)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(
            HateoasNewMovementResponse(movementResponse, Some(upscanResponse), MovementType.Arrival)
          )

          verify(mockUpscanService, times(1)).upscanInitiate()(any(), any())
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML))(any(), any())
          verify(mockMovementsPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Departure), any())(any(), any())
      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary,
        arbitraryMovementResponse(false, false).arbitrary
      ) {
        (upscanResponse, movementResponse) =>
          when(mockUpscanService.upscanInitiate()(any(), any()))
            .thenAnswer {
              _ => EitherT.rightT(upscanResponse)
            }

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(MovementId(anyString()), any(), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.leftT(PushNotificationError.UnexpectedError(None))
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, Source.empty[ByteString], MovementType.Arrival)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(
            HateoasNewMovementResponse(movementResponse, Some(upscanResponse), MovementType.Arrival)
          )

          verify(mockUpscanService, times(1)).upscanInitiate()(any(), any())
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML))(any(), any())
          verify(mockMovementsPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Departure), any())(any(), any())
      }

      "must return Internal Service Error if the persistence service reports an error" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary
      ) {
        upscanResponse =>
          when(mockUpscanService.upscanInitiate()(any(), any()))
            .thenAnswer {
              _ => EitherT.rightT(upscanResponse)
            }

          when(
            mockMovementsPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer(
            _ => EitherT.leftT(PersistenceError.UnexpectedError(None))
          )

          val request =
            fakeCreateMovementRequest("POST", standardHeaders, Source.empty[ByteString], MovementType.Arrival)
          val response = sut.createMovement(MovementType.Departure)(request)

          status(response) mustBe INTERNAL_SERVER_ERROR
          contentAsJson(response) mustBe Json.obj(
            "code"    -> "INTERNAL_SERVER_ERROR",
            "message" -> "Internal server error"
          )
      }

      "must return Internal Service Error if the upscan service reports an error" in {

        when(mockUpscanService.upscanInitiate()(any(), any()))
          .thenAnswer {
            _ => EitherT.leftT(UpscanInitiateError.UnexpectedError(None))
          }

        val request =
          fakeCreateMovementRequest("POST", standardHeaders, Source.empty[ByteString], MovementType.Arrival)
        val response = sut.createMovement(MovementType.Arrival)(request)

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
      val request = fakeCreateMovementRequest("POST", standardHeaders, Source.single(json), MovementType.Arrival)

      val result = sut.createMovement(MovementType.Arrival)(request)
      status(result) mustBe UNSUPPORTED_MEDIA_TYPE
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "UNSUPPORTED_MEDIA_TYPE",
        "message" -> "Content-type header invalid is not supported!"
      )
    }

    "must return Internal Service Error if the router service reports an error" in forAll(
      arbitraryMovementResponse(true, true).arbitrary
    ) {
      movementResponse =>
        beforeEach()

        val standardHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
        )

        when(mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )

        when(
          mockRouterService.send(
            any[String].asInstanceOf[MessageType],
            any[String].asInstanceOf[EORINumber],
            any[String].asInstanceOf[MovementId],
            any[String].asInstanceOf[MessageId],
            any[Source[ByteString, _]]
          )(any[ExecutionContext], any[HeaderCarrier])
        ).thenAnswer(
          _ => EitherT.leftT(RouterError.UnexpectedError(None))
        )

        when(
          mockMovementsPersistenceService
            .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
        ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, MovementResponse](movementResponse)))

        when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
          .thenAnswer(
            _ => EitherT.rightT(movementResponse.boxResponse.get)
          )

        val request  = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007C.mkString), MovementType.Arrival)
        val response = sut.createMovement(MovementType.Arrival)(request)
        status(response) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(response) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )

    }
  }

  for (movementType <- Seq(MovementType.Arrival, MovementType.Departure)) {

    val contentJson = if (movementType == MovementType.Departure) CC013Cjson else CC044Cjson
    val contentXml  = if (movementType == MovementType.Departure) CC013C else CC044C

    s"GET /movements/${movementType.urlFragment}/:movementId/messages " - {

      val datetimes = Seq(arbitrary[OffsetDateTime].sample, None)

      datetimes.foreach {
        dateTime =>
          s"when a movement is found ${dateTime
            .map(
              _ => "with"
            )
            .getOrElse("without")} a date filter" in forAll(Gen.listOfN(3, arbitraryMessageSummaryXml.arbitrary.sample.head)) {
            messageResponse =>
              when(
                mockMovementsPersistenceService.getMessages(EORINumber(any()), any[MovementType], MovementId(any()), any())(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(messageResponse)
                )

              val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
              val result  = sut.getMessageIds(movementType, MovementId("0123456789abcdef"), dateTime)(request)

              status(result) mustBe OK
              contentAsJson(result) mustBe Json.toJson(
                HateoasMovementMessageIdsResponse(MovementId("0123456789abcdef"), messageResponse, dateTime, movementType)
              )
          }
      }

      "when no movement is found" in {
        when(
          mockMovementsPersistenceService.getMessages(EORINumber(any()), any[MovementType], MovementId(any()), any())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenAnswer(
            _ => EitherT.leftT(PersistenceError.MovementNotFound(MovementId("0123456789abcdef"), movementType))
          )

        val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
        val result  = sut.getMessageIds(movementType, MovementId("0123456789abcdef"), None)(request)

        status(result) mustBe NOT_FOUND
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "NOT_FOUND",
          "message" -> s"${movementType.movementType.capitalize} movement with ID 0123456789abcdef was not found"
        )
      }

      "when an unknown error occurs" in {
        when(
          mockMovementsPersistenceService.getMessages(EORINumber(any()), any[MovementType], MovementId(any()), any())(any[HeaderCarrier], any[ExecutionContext])
        )
          .thenAnswer(
            _ => EitherT.leftT(PersistenceError.UnexpectedError(thr = None))
          )

        val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
        val result  = sut.getMessageIds(movementType, MovementId("0123456789abcdef"), None)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )
      }

    }

    s"/movements/${movementType.urlFragment}/:movementId/messages/:messageId " - {
      val movementId         = arbitraryMovementId.arbitrary.sample.value
      val messageId          = arbitraryMessageId.arbitrary.sample.value
      val messageSummaryXml  = arbitraryMessageSummaryXml.arbitrary.sample.value.copy(id = messageId, body = Some(XmlPayload("<test>ABC</test>")))
      val messageSummaryJson = messageSummaryXml.copy(body = Some(JsonPayload("""{"test": "ABC"}""")))

      Seq(
        VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON,
        VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML,
        VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN
      ).foreach {

        acceptHeaderValue =>
          val headers =
            FakeHeaders(Seq(HeaderNames.ACCEPT -> acceptHeaderValue))
          val request =
            FakeRequest(
              "GET",
              if (movementType == MovementType.Departure) routing.routes.DeparturesRouter.getMessage(movementId.value, messageId.value).url
              else routing.routes.ArrivalsRouter.getArrivalMessage(movementId.value, messageId.value).url,
              headers,
              Source.empty[ByteString]
            )
          val convertBodyToJson = acceptHeaderValue == VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON

          s"when the accept header equals $acceptHeaderValue" - {

            "when the message is found" in {
              when(
                mockMovementsPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(messageSummaryXml)
                )

              when(
                mockResponseFormatterService.formatMessageSummary(any[MessageSummary], eqTo(acceptHeaderValue))(
                  any[ExecutionContext],
                  any[HeaderCarrier],
                  any[Materializer]
                )
              )
                .thenAnswer(
                  _ => if (convertBodyToJson) EitherT.rightT(messageSummaryJson) else EitherT.rightT(messageSummaryXml)
                )

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              status(result) mustBe OK
              contentAsJson(result) mustBe Json.toJson(
                HateoasMovementMessageResponse(
                  movementId,
                  messageId,
                  if (convertBodyToJson) messageSummaryJson else messageSummaryXml,
                  movementType
                )
              )
            }

            "when no message is found" in {
              when(
                mockMovementsPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(PersistenceError.MessageNotFound(movementId, messageId))
                )

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              status(result) mustBe NOT_FOUND
              contentAsJson(result) mustBe Json.obj(
                "code"    -> "NOT_FOUND",
                "message" -> s"Message with ID ${messageId.value} for movement ${movementId.value} was not found"
              )
            }

            "when formatter service fail" in {
              when(
                mockMovementsPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(messageSummaryXml)
                )

              when(
                mockResponseFormatterService.formatMessageSummary(any[MessageSummary], eqTo(acceptHeaderValue))(
                  any[ExecutionContext],
                  any[HeaderCarrier],
                  any[Materializer]
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(PresentationError.internalServiceError())
                )

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              status(result) mustBe INTERNAL_SERVER_ERROR
              contentAsJson(result) mustBe Json.obj(
                "code"    -> "INTERNAL_SERVER_ERROR",
                "message" -> "Internal server error"
              )
            }

            "when an unknown error occurs" in {
              when(
                mockMovementsPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(PersistenceError.UnexpectedError(thr = None))
                )

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              status(result) mustBe INTERNAL_SERVER_ERROR
              contentAsJson(result) mustBe Json.obj(
                "code"    -> "INTERNAL_SERVER_ERROR",
                "message" -> "Internal server error"
              )
            }

          }
      }
    }

    s"GET  /movements/${movementType.movementType}" - {
      val url =
        if (movementType == MovementType.Departure) routing.routes.DeparturesRouter.getDeparturesForEori().url
        else routing.routes.ArrivalsRouter.getArrivalsForEori().url

      "should return ok with json body for movements" in {

        val enrolmentEORINumber = arbitrary[EORINumber].sample.value
        val dateTime            = OffsetDateTime.of(2022, 8, 4, 11, 34, 42, 0, ZoneOffset.UTC)

        val departureResponse1 = MovementSummary(
          _id = arbitrary[MovementId].sample.value,
          enrollmentEORINumber = enrolmentEORINumber,
          movementEORINumber = arbitrary[EORINumber].sample.value,
          movementReferenceNumber = Some(arbitrary[MovementReferenceNumber].sample.value),
          created = dateTime,
          updated = dateTime.plusHours(1)
        )

        val departureResponse2 = MovementSummary(
          _id = arbitrary[MovementId].sample.value,
          enrollmentEORINumber = enrolmentEORINumber,
          movementEORINumber = arbitrary[EORINumber].sample.value,
          movementReferenceNumber = Some(arbitrary[MovementReferenceNumber].sample.value),
          created = dateTime.plusHours(2),
          updated = dateTime.plusHours(3)
        )
        val departureResponses = Seq(departureResponse1, departureResponse2)

        when(
          mockMovementsPersistenceService.getMovements(EORINumber(any()), any[MovementType], any[Option[OffsetDateTime]], any[Option[EORINumber]])(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        )
          .thenAnswer(
            _ => EitherT.rightT(departureResponses)
          )
        val request = FakeRequest(
          GET,
          url,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON)),
          AnyContentAsEmpty
        )
        val result = sut.getMovements(movementType, None, None)(request)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(
          HateoasMovementIdsResponse(
            departureResponses,
            movementType,
            None,
            None
          )
        )
      }

      "should return departure not found if persistence service returns 404" in {
        val eori = EORINumber("ERROR")

        when(
          mockMovementsPersistenceService.getMovements(EORINumber(any()), any[MovementType], any[Option[OffsetDateTime]], any[Option[EORINumber]])(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        )
          .thenAnswer(
            _ => EitherT.leftT(PersistenceError.MovementsNotFound(eori, movementType))
          )

        val request = FakeRequest(
          GET,
          url,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON)),
          AnyContentAsEmpty
        )
        val result = sut.getMovements(movementType, None, None)(request)

        status(result) mustBe NOT_FOUND
        contentAsJson(result) mustBe Json.obj(
          "message" -> s"${movementType.movementType.capitalize} movement IDs for ${eori.value} were not found",
          "code"    -> "NOT_FOUND"
        )
      }

      "should return unexpected error for all other errors" in {
        when(
          mockMovementsPersistenceService.getMovements(EORINumber(any()), any[MovementType], any[Option[OffsetDateTime]], any[Option[EORINumber]])(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        )
          .thenAnswer(
            _ => EitherT.leftT(PersistenceError.UnexpectedError(None))
          )

        val request = FakeRequest(
          GET,
          url,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON)),
          AnyContentAsEmpty
        )
        val result = sut.getMovements(movementType, None, None)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )
      }

    }

    s"GET  /movements/${movementType.urlFragment}/:movementId" - {

      "should return ok with json body of movement" in forAll(
        arbitrary[EORINumber],
        arbitrary[EORINumber],
        arbitrary[MovementId],
        arbitrary[MovementReferenceNumber]
      ) {
        (enrollmentEori, movementEori, movementId, mrn) =>
          val createdTime = OffsetDateTime.now()
          val departureResponse = MovementSummary(
            movementId,
            enrollmentEori,
            movementEori,
            Some(mrn),
            createdTime,
            createdTime
          )

          when(mockMovementsPersistenceService.getMovement(EORINumber(any()), any[MovementType], MovementId(any()))(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(departureResponse)
            )

          val url =
            if (movementType == MovementType.Departure) routing.routes.DeparturesRouter.getDeparture(movementId.value).url
            else routing.routes.ArrivalsRouter.getArrival(movementId.value).url

          val request = FakeRequest(
            GET,
            url,
            headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON)),
            AnyContentAsEmpty
          )
          val result = sut.getMovement(movementType, movementId)(request)

          status(result) mustBe OK
          contentAsJson(result) mustBe Json.toJson(
            HateoasMovementResponse(
              movementId,
              MovementSummary(
                movementId,
                enrollmentEori,
                movementEori,
                Some(mrn),
                createdTime,
                createdTime
              ),
              movementType
            )
          )
      }

      "should return movement not found if persistence service returns 404" in {
        when(
          mockMovementsPersistenceService
            .getMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[String].asInstanceOf[MovementId])(any(), any())
        )
          .thenAnswer {
            _ =>
              EitherT.leftT(PersistenceError.MovementNotFound(MovementId("ABC123"), movementType))
          }

        val movementId = arbitraryMovementId.arbitrary.sample.get

        val url =
          if (movementType == MovementType.Departure) routing.routes.DeparturesRouter.getDeparture(movementId.value).url
          else routing.routes.ArrivalsRouter.getArrival(movementId.value).url

        val request = FakeRequest(
          GET,
          url,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON)),
          AnyContentAsEmpty
        )
        val result = sut.getMovement(movementType, movementId)(request)

        status(result) mustBe NOT_FOUND
      }

      "should return unexpected error for all other errors" in forAll(arbitrary[MovementId]) {
        movementId =>
          when(mockMovementsPersistenceService.getMovement(EORINumber(any()), any[MovementType], MovementId(any()))(any(), any()))
            .thenAnswer {
              _ =>
                EitherT.leftT(PersistenceError.UnexpectedError(None))
            }

          val url =
            if (movementType == MovementType.Departure) routing.routes.DeparturesRouter.getDeparture(movementId.value).url
            else routing.routes.ArrivalsRouter.getArrival(movementId.value).url

          val request = FakeRequest(
            GET,
            url,
            headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON)),
            AnyContentAsEmpty
          )
          val result = sut.getMovement(movementType, movementId)(request)

          status(result) mustBe INTERNAL_SERVER_ERROR
      }
    }

    s"POST /movements/${movementType.urlFragment}/:movementId/messages" - {

      lazy val movementId = arbitraryMovementId.arbitrary.sample.get

      val messageType =
        if (movementType == MovementType.Departure) MessageType.DeclarationAmendment
        else MessageType.UnloadingRemarks

      lazy val messageDataEither: EitherT[Future, ExtractionError, MessageType] =
        EitherT.rightT(messageType)

      "with content type set to application/xml" - {

        // For the content length headers, we have to ensure that we send something
        val standardHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
        )

        "must return Accepted when body length is within limits and is considered valid" in {
          when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any(), any()))
            .thenReturn(messageDataEither)

          when(
            mockValidationService.validateXml(eqTo(messageType), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(
            mockRouterService.send(
              any[String].asInstanceOf[MessageType],
              any[String].asInstanceOf[EORINumber],
              any[String].asInstanceOf[MovementId],
              any[String].asInstanceOf[MessageId],
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.rightT(())
          )

          when(
            mockMovementsPersistenceService
              .updateMovement(any[String].asInstanceOf[MovementId], any[MovementType], any[String].asInstanceOf[MessageType], any[Source[ByteString, _]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, UpdateMovementResponse](UpdateMovementResponse(MessageId("456")))))

          val request = fakeAttachMessageRequest("POST", standardHeaders, singleUseStringSource(contentXml.mkString), movementType)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe ACCEPTED
          contentAsJson(result) mustBe Json.toJson(HateoasMovementUpdateResponse(movementId, MessageId("456"), movementType))

          verify(mockAuditService, times(1)).audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())
          verify(mockValidationService, times(1)).validateXml(eqTo(messageType), any())(any(), any())
          verify(mockMovementsPersistenceService, times(1)).updateMovement(MovementId(any()), any(), any(), any())(any(), any())
          verify(mockRouterService, times(1)).send(eqTo(messageType), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
            any(),
            any()
          )
          verify(mockPushNotificationService, times(1)).update(MovementId(eqTo(movementId.value)))(any(), any())
        }

        "must return Bad Request when body is not an XML document" in {
          when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]](), any[Seq[MessageType]])(any(), any()))
            .thenAnswer(
              _ => EitherT.leftT(ExtractionError.MalformedInput)
            )

          val request = fakeAttachMessageRequest("POST", standardHeaders, singleUseStringSource("notxml"), movementType)
          val result  = sut.attachMessage(movementType, movementId)(request)
          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "BAD_REQUEST",
            "message" -> "Input was malformed"
          )
        }

        "must return Internal Service Error if the persistence service reports an error" in {
          when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any(), any()))
            .thenReturn(messageDataEither)
          when(
            mockValidationService.validateXml(eqTo(messageType), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => EitherT.rightT(())
            )
          when(
            mockMovementsPersistenceService
              .updateMovement(any[String].asInstanceOf[MovementId], any[MovementType], any[String].asInstanceOf[MessageType], any[Source[ByteString, _]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          ).thenReturn(EitherT.fromEither[Future](Left[PersistenceError, UpdateMovementResponse](PersistenceError.UnexpectedError(None))))

          val request  = fakeAttachMessageRequest("POST", standardHeaders, Source.single(ByteString(contentXml.mkString, StandardCharsets.UTF_8)), movementType)
          val response = sut.attachMessage(movementType, movementId)(request)

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
          conversion: EitherT[Future, ConversionError, Source[ByteString, _]] =
            if (movementType == MovementType.Departure) EitherT.rightT(singleUseStringSource(contentXml.mkString))
            else EitherT.rightT(singleUseStringSource(CC044Cjson.mkString)),
          persistence: EitherT[Future, PersistenceError, UpdateMovementResponse] = EitherT.rightT(UpdateMovementResponse(MessageId("456"))),
          router: EitherT[Future, RouterError, Unit] = EitherT.rightT(())
        ): Unit = {

          when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any(), any())).thenReturn(extractMessageTypeXml)
          when(mockJsonParsingService.extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any(), any())).thenReturn(extractMessageTypeJson)

          when(mockValidationService.validateXml(any[MessageType], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => validateXml
            )

          when(
            mockValidationService.validateJson(any[MessageType], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => validateJson
            )

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON))(any(), any())).thenReturn(Future.successful(()))

          when(mockConversionService.jsonToXml(any(), any())(any(), any(), any())).thenReturn(conversion)

          when(
            mockMovementsPersistenceService
              .updateMovement(
                any[String].asInstanceOf[MovementId],
                any[MovementType],
                any[String].asInstanceOf[MessageType],
                any[Source[ByteString, _]]()
              )(
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

        def fakeJsonAttachRequest(content: String): Request[Source[ByteString, _]] =
          fakeAttachMessageRequest("POST", standardHeaders, singleUseStringSource(contentJson), movementType)

        "must return Accepted when body length is within limits and is considered valid" in {

          setup()
          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe ACCEPTED
          contentAsJson(result) mustBe Json.toJson(HateoasMovementUpdateResponse(movementId, MessageId("456"), movementType))

          verify(mockValidationService, times(1)).validateJson(any(), any())(any(), any())
          verify(mockAuditService, times(1)).audit(any(), any(), eqTo(MimeTypes.JSON))(any(), any())
          verify(mockConversionService, times(1)).jsonToXml(any(), any())(any(), any(), any())
          verify(mockValidationService, times(1)).validateXml(any(), any())(any(), any())
          verify(mockMovementsPersistenceService, times(1)).updateMovement(MovementId(any()), any(), any(), any())(any(), any())
          verify(mockRouterService, times(1)).send(any(), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
            any(),
            any()
          )
        }

        "must return Bad Request when body is not an JSON document" in {

          setup(extractMessageTypeJson = EitherT.leftT(ExtractionError.MalformedInput))

          val request = fakeJsonAttachRequest("notJson")
          val result  = sut.attachMessage(movementType, movementId)(request)
          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "BAD_REQUEST",
            "message" -> "Input was malformed"
          )
        }

        "must return Bad Request when unable to find a message type " in {
          setup(extractMessageTypeJson = EitherT.leftT(MessageTypeNotFound("contentXml")))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)
          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "BAD_REQUEST",
            "message" -> "contentXml is not a valid message type"
          )
        }

        "must return BadRequest when JsonValidation service doesn't recognise message type" in {

          setup(validateJson = EitherT.leftT(InvalidMessageTypeError("contentXml")))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)
          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "BAD_REQUEST",
            "message" -> "contentXml is not a valid message type"
          )
        }

        "must return BadRequest when json fails to validate" in {

          setup(validateJson = EitherT.leftT(JsonSchemaFailedToValidateError(NonEmptyList.one(JsonValidationError("sample", "message")))))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)
          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "message"          -> "Request failed schema validation",
            "code"             -> ErrorCode.SchemaValidation.code,
            "validationErrors" -> Json.arr(Json.obj("schemaPath" -> "sample", "message" -> "message"))
          )
        }

        "must return InternalServerError when Unexpected error validating the json" in {

          setup(validateJson = EitherT.leftT(FailedToValidateError.UnexpectedError(None)))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return InternalServerError when Unexpected error converting the json to xml" in {
          setup(conversion = EitherT.leftT(ConversionError.UnexpectedError(None)))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return InternalServerError when xml failed validation" in {
          setup(validateXml = EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList.one(XmlValidationError(1, 1, "message")))))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return BadRequest when message type not recognised by xml validator" in {
          setup(validateXml = EitherT.leftT(FailedToValidateError.InvalidMessageTypeError("test")))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe BAD_REQUEST
        }

        "must return InternalServerError when unexpected error from xml validator" in {
          setup(validateXml = EitherT.leftT(FailedToValidateError.UnexpectedError(None)))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return NotFound when movement not found by Persistence" in {
          setup(persistence = EitherT.leftT(PersistenceError.MovementNotFound(movementId, movementType)))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe NOT_FOUND
        }

        "must return InternalServerError when Persistence return Unexpected Error" in {
          setup(persistence = EitherT.leftT(PersistenceError.UnexpectedError(None)))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return InternalServerError when router throws unexpected error" in {
          setup(router = EitherT.leftT(RouterError.UnexpectedError(None)))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return BadRequest when router returns BadRequest" in {
          setup(router = EitherT.leftT(RouterError.UnrecognisedOffice("AB012345", "field")))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe BAD_REQUEST
        }

      }

      "must return UNSUPPORTED_MEDIA_TYPE when the content type is invalid" in {
        val standardHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "invalid", HeaderNames.CONTENT_LENGTH -> "1000")
        )

        val request  = fakeAttachMessageRequest("POST", standardHeaders, Source.single(ByteString(contentXml.mkString, StandardCharsets.UTF_8)), movementType)
        val response = sut.attachMessage(movementType, movementId)(request)
        status(response) mustBe UNSUPPORTED_MEDIA_TYPE
        contentAsJson(response) mustBe Json.obj(
          "code"    -> "UNSUPPORTED_MEDIA_TYPE",
          "message" -> "Content-type header invalid is not supported!"
        )
      }
    }
  }

}
