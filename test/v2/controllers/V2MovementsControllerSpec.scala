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
import config.AppConfig
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.{eq => eqTo}
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
import play.api.Logger
import play.api.http.HeaderNames
import play.api.http.HttpVerbs.GET
import play.api.http.MimeTypes
import play.api.http.Status._
import play.api.libs.Files.SingletonTemporaryFileCreator
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.json.JsError
import play.api.libs.json.JsSuccess
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Request
import play.api.mvc.Result
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers
import play.api.test.Helpers.POST
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.contentAsString
import play.api.test.Helpers.contentType
import play.api.test.Helpers.status
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import utils.TestMetrics
import v2.base.HeaderCarrierMatcher
import v2.base.SourceMatcher
import v2.base.TestActorSystem
import v2.base.TestCommonGenerators
import v2.base.TestSourceProvider
import v2.controllers.actions.providers.AcceptHeaderActionProvider
import v2.controllers.actions.providers.AcceptHeaderActionProviderImpl
import v2.fakes.controllers.actions.FakeAcceptHeaderActionProvider
import v2.fakes.controllers.actions.FakeAuthNewEnrolmentOnlyAction
import v2.models.EORINumber
import v2.models._
import v2.models.errors.ExtractionError.MessageTypeNotFound
import v2.models.errors.FailedToValidateError.InvalidMessageTypeError
import v2.models.errors.FailedToValidateError.JsonSchemaFailedToValidateError
import v2.models.errors._
import v2.models.request.MessageType
import v2.models.request.MessageUpdate
import v2.models.responses.FailureDetails
import v2.models.responses.MovementResponse
import v2.models.responses.MovementSummary
import v2.models.responses.PaginationMessageSummary
import v2.models.responses.PaginationMovementSummary
import v2.models.responses.TraderFailedUploadAuditRequest
import v2.models.responses.UpdateMovementResponse
import v2.models.responses.UploadDetails
import v2.models.responses.UpscanFailedResponse
import v2.models.responses.UpscanResponse
import v2.models.responses.UpscanSuccessResponse
import v2.models.LocalReferenceNumber
import v2.models.responses.UpscanResponse.DownloadUrl
import v2.models.responses.UpscanResponse.Reference
import v2.models.responses.hateoas._
import v2.services.ConversionService
import v2.services._

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
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
    with TestCommonGenerators {

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

  val CC015Cjson: String = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
  val CC007Cjson: String = Json.stringify(Json.obj("CC007" -> Json.obj("SynIdeMES1" -> "UNOC")))
  val CC013Cjson: String = Json.stringify(Json.obj("CC013" -> Json.obj("field" -> "value")))
  val CC044Cjson: String = Json.stringify(Json.obj("CC044" -> Json.obj("field" -> "value")))

  val upscanDownloadUrl: DownloadUrl = DownloadUrl("https://bucketName.s3.eu-west-2.amazonaws.com?1235676")

  val upscanSuccess: UpscanSuccessResponse =
    UpscanSuccessResponse(
      Reference("11370e18-6e24-453e-b45a-76d3e32ea33d"),
      upscanDownloadUrl,
      UploadDetails(
        "test.xml",
        MimeTypes.XML,
        Instant.from(OffsetDateTime.of(2018, 4, 24, 8, 30, 0, 0, ZoneOffset.UTC)),
        "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
        600000L
      )
    )

  val upscanFailed: UpscanResponse =
    UpscanFailedResponse(
      Reference("11370e18-6e24-453e-b45a-76d3e32ea33d"),
      FailureDetails(
        "QUARANTINE",
        "e.g. This file has a virus"
      )
    )

  case class ControllerAndMocks(
    sut: V2MovementsController,
    mockValidationService: ValidationService,
    mockPersistenceService: PersistenceService,
    mockRouterService: RouterService,
    mockAuditService: AuditingService,
    mockConversionService: ConversionService,
    mockXmlParsingService: XmlMessageParsingService,
    mockJsonParsingService: JsonMessageParsingService,
    mockPushNotificationService: PushNotificationsService,
    mockUpscanService: UpscanService,
    mockAppConfig: AppConfig
  )

  def createControllerAndMocks(acceptHeaderProvider: AcceptHeaderActionProvider = FakeAcceptHeaderActionProvider): ControllerAndMocks = {
    val mockValidationService       = mock[ValidationService]
    val mockPersistenceService      = mock[PersistenceService]
    val mockRouterService           = mock[RouterService]
    val mockAuditService            = mock[AuditingService]
    val mockConversionService       = mock[ConversionService]
    val mockXmlParsingService       = mock[XmlMessageParsingService]
    val mockJsonParsingService      = mock[JsonMessageParsingService]
    val mockPushNotificationService = mock[PushNotificationsService]
    val mockUpscanService           = mock[UpscanService]
    val mockAppConfig               = mock[AppConfig]

    implicit val temporaryFileCreator: TemporaryFileCreator = SingletonTemporaryFileCreator

    when(mockAppConfig.smallMessageSizeLimit).thenReturn(500000)

    val sut: V2MovementsController = new V2MovementsControllerImpl(
      Helpers.stubControllerComponents(),
      FakeAuthNewEnrolmentOnlyAction(),
      mockValidationService,
      mockConversionService,
      mockPersistenceService,
      mockRouterService,
      mockAuditService,
      mockPushNotificationService,
      acceptHeaderProvider,
      new TestMetrics(),
      mockXmlParsingService,
      mockJsonParsingService,
      mockUpscanService,
      mockAppConfig
    ) {
      // suppress logging
      override protected val logger: Logger = mock[Logger]
    }

    ControllerAndMocks(
      sut,
      mockValidationService,
      mockPersistenceService,
      mockRouterService,
      mockAuditService,
      mockConversionService,
      mockXmlParsingService,
      mockJsonParsingService,
      mockPushNotificationService,
      mockUpscanService,
      mockAppConfig
    )
  }

  lazy val movementId: MovementId = arbitraryMovementId.arbitrary.sample.get
  lazy val messageId: MessageId   = arbitraryMessageId.arbitrary.sample.get

  implicit val timeout: Timeout = 5.seconds

  lazy val messageUpdateSuccess: MessageUpdate =
    MessageUpdate(MessageStatus.Success, None, None)

  lazy val messageUpdateFailure: MessageUpdate =
    MessageUpdate(MessageStatus.Failed, None, None)

  def fakeHeaders(contentType: String): FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> contentType))

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

  def jsonValidationMockAnswer(movementType: MovementType): InvocationOnMock => EitherT[Future, FailedToValidateError, Unit] = (invocation: InvocationOnMock) =>
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
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            _,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()

          when(mockValidationService.validateXml(any[MessageType], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(any(), any(), any(), any[Long]())(any(), any())).thenReturn(Future.successful(()))

          when(
            mockPersistenceService
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
              _ => EitherT.rightT(boxResponse)
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
            _ => EitherT.rightT(SubmissionRoute.ViaEIS)
          )

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Departure),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateSuccess)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(())
            }

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015C.mkString), MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(
            HateoasNewMovementResponse(movementResponse.movementId, Some(boxResponse), None, MovementType.Departure)
          )

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML), any[Long]())(any(), any())
          verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockPersistenceService, times(1)).createMovement(EORINumber(any()), eqTo(MovementType.Departure), any())(any(), any())
          verify(mockRouterService, times(1)).send(eqTo(MessageType.DeclarationData), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
            any(),
            any()
          )
          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Departure),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateSuccess)
          )(
            any(),
            any()
          )
          verify(mockPushNotificationService, times(1)).associate(MovementId(any()), eqTo(MovementType.Departure), any())(any(), any())
      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryMovementResponse().arbitrary
      ) {
        movementResponse =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            _,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()
          when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML), any[Long]())(any(), any())).thenReturn(Future.successful(()))

          when(
            mockPersistenceService
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
            _ => EitherT.rightT(SubmissionRoute.ViaEIS)
          )

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Departure),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateSuccess)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(())
            }

          when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
            .thenAnswer(
              _ => EitherT.leftT(PushNotificationError.UnexpectedError(None))
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015C.mkString), MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse.movementId, None, None, MovementType.Departure))

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML), any[Long]())(any(), any())
          verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockRouterService, times(1)).send(eqTo(MessageType.DeclarationData), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
            any(),
            any()
          )
          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Departure),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateSuccess)
          )(
            any(),
            any()
          )
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Departure), any())(any(), any())

      }

      "must return error when the persistence service of message status update fails" in forAll(
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            _,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()

          when(
            mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML), any[Long]())(any(), any())).thenReturn(Future.successful(()))

          when(
            mockPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], eqTo(MovementType.Departure), any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(MovementId(eqTo(movementResponse.movementId.value)), eqTo(MovementType.Departure), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(boxResponse)
            )

          when(
            mockRouterService.send(
              eqTo(MessageType.DeclarationData),
              any[String].asInstanceOf[EORINumber],
              MovementId(eqTo(movementResponse.movementId.value)),
              MessageId(eqTo(movementResponse.messageId.value)),
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.rightT(SubmissionRoute.ViaEIS)
          )

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Departure),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateSuccess)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.leftT(PersistenceError.MovementNotFound(movementResponse.movementId, MovementType.Departure))
            }

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015C.mkString), MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe NOT_FOUND

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML), any[Long]())(any(), any())
          verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockPersistenceService, times(1)).createMovement(EORINumber(any()), eqTo(MovementType.Departure), any())(any(), any())
          verify(mockRouterService, times(1)).send(
            eqTo(MessageType.DeclarationData),
            EORINumber(any()),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            any()
          )(
            any(),
            any()
          )
          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Departure),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateSuccess)
          )(
            any(),
            any()
          )
          verify(mockPushNotificationService, times(1))
            .associate(MovementId(eqTo(movementResponse.movementId.value)), eqTo(MovementType.Departure), any())(any(), any())
      }

      "must return Bad Request when body is an XML document that would fail schema validation" in {
        val ControllerAndMocks(
          sut,
          mockValidationService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
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
        val ControllerAndMocks(
          sut,
          mockValidationService,
          mockPersistenceService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
        when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )

        when(
          mockPersistenceService
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
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            _,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()

          when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())).thenReturn(Future.successful(()))

          when(
            mockPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, MovementResponse](movementResponse)))

          when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(boxResponse)
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
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Departure),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateFailure)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(())
            }

          val request =
            fakeCreateMovementRequest("POST", standardHeaders, Source.single(ByteString(CC015C.mkString, StandardCharsets.UTF_8)), MovementType.Departure)
          val response = sut.createMovement(MovementType.Departure)(request)

          status(response) mustBe INTERNAL_SERVER_ERROR
          contentAsJson(response) mustBe Json.obj(
            "code"    -> "INTERNAL_SERVER_ERROR",
            "message" -> "Internal server error"
          )

          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Departure),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateFailure)
          )(
            any(),
            any()
          )
      }

      "must return Conflict Error if the router service reports a duplicate lrn error" in forAll(
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            _,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()

          when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())).thenReturn(Future.successful(()))

          when(
            mockPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, MovementResponse](movementResponse)))

          when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(boxResponse)
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
            _ => EitherT.leftT(RouterError.DuplicateLRN(LocalReferenceNumber("1234")))
          )

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Departure),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateFailure)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(())
            }

          val request =
            fakeCreateMovementRequest("POST", standardHeaders, Source.single(ByteString(CC015C.mkString, StandardCharsets.UTF_8)), MovementType.Departure)
          val response = sut.createMovement(MovementType.Departure)(request)

          status(response) mustBe CONFLICT
          contentAsJson(response) mustBe Json.obj(
            "code"    -> "CONFLICT",
            "message" -> "LRN 1234 has previously been used and cannot be reused"
          )

          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Departure),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateFailure)
          )(
            any(),
            any()
          )
      }
    }

    "with content type set to application/json" - {
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON, HeaderNames.CONTENT_LENGTH -> "1000")
      )

      "must return Accepted when body length is within limits and is considered valid" in forAll(
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            mockConversionService,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()

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
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())).thenReturn(Future.successful(()))

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
            mockPersistenceService
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
              _ => EitherT.rightT(boxResponse)
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
            _ => EitherT.rightT(SubmissionRoute.ViaEIS)
          )

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Departure),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateSuccess)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(())
            }

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015Cjson), MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe ACCEPTED
          contentAsJson(result) mustBe Json.toJson(
            HateoasNewMovementResponse(movementResponse.movementId, Some(boxResponse), None, MovementType.Departure)
          )

          verify(mockConversionService, times(1)).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
          verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockConversionService).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())
          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Departure),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateSuccess)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Departure), any())(any(), any())
      }

      "must return Payload Too Large when converted JSON in XML is greater in limit" in forAll(
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            mockConversionService,
            _,
            _,
            mockPushNotificationService,
            _,
            mockAppConfig
          ) = createControllerAndMocks()

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
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())).thenReturn(Future.successful(()))

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
          when(mockAppConfig.smallMessageSizeLimit).thenReturn(40)

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015Cjson), MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe REQUEST_ENTITY_TOO_LARGE
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "REQUEST_ENTITY_TOO_LARGE",
            "message" -> "Request Entity Too Large"
          )
          verify(mockConversionService, times(1)).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
          verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())

      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryMovementResponse().arbitrary
      ) {
        movementResponse =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            mockConversionService,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()

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
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())).thenReturn(Future.successful(()))

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
            _ => EitherT.rightT(SubmissionRoute.ViaEIS)
          )

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Departure),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateSuccess)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(())
            }

          when(
            mockPersistenceService
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

          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse.movementId, None, None, MovementType.Departure))

          verify(mockConversionService, times(1)).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
          verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockConversionService).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())
          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Departure),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateSuccess)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Departure), any())(any(), any())
      }

      "must return error when the persistence service of message status update fails" in forAll(
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            mockConversionService,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()

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
          when(mockAuditService.audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any()))
            .thenReturn(Future.successful(()))

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
            mockPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], eqTo(MovementType.Departure), any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(MovementId(eqTo(movementResponse.movementId.value)), eqTo(MovementType.Departure), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(boxResponse)
            )

          when(
            mockRouterService.send(
              eqTo(MessageType.DeclarationData),
              any[String].asInstanceOf[EORINumber],
              MovementId(eqTo(movementResponse.movementId.value)),
              MessageId(eqTo(movementResponse.messageId.value)),
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.rightT(SubmissionRoute.ViaEIS)
          )

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Departure),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateSuccess)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ =>
                EitherT.leftT(PersistenceError.MovementNotFound(movementResponse.movementId, MovementType.Departure))
            }

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015Cjson), MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe NOT_FOUND

          verify(mockConversionService, times(1)).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
          verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())
          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Departure),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateSuccess)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
          verify(mockPushNotificationService, times(1))
            .associate(MovementId(eqTo(movementResponse.movementId.value)), eqTo(MovementType.Departure), any())(any(), any())
      }

      "must return Bad Request when body is not an JSON document" in {
        val ControllerAndMocks(
          sut,
          mockValidationService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
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
        val ControllerAndMocks(
          sut,
          mockValidationService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
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
        val ControllerAndMocks(
          sut,
          mockValidationService,
          _,
          _,
          _,
          mockConversionService,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
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
        val ControllerAndMocks(
          sut,
          mockValidationService,
          _,
          _,
          _,
          mockConversionService,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
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
        val ControllerAndMocks(
          sut,
          mockValidationService,
          mockPersistenceService,
          _,
          _,
          mockConversionService,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
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
          mockPersistenceService
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
        verify(mockPersistenceService).createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any(), any())
      }

      "must return Internal Service Error if the router service reports an error" in forAll(
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            _,
            mockConversionService,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()

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
            mockPersistenceService
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
              _ => EitherT.rightT(boxResponse)
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

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Departure),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateFailure)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(())
            }

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015Cjson), MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)
          status(result) mustBe INTERNAL_SERVER_ERROR

          verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DeclarationData), any())(any(), any())
          verify(mockConversionService).jsonToXml(eqTo(MessageType.DeclarationData), any())(any(), any(), any())
          verify(mockPersistenceService).createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any(), any())
          verify(mockRouterService).send(
            eqTo(MessageType.DeclarationData),
            any[String].asInstanceOf[EORINumber],
            any[String].asInstanceOf[MovementId],
            any[String].asInstanceOf[MessageId],
            any[Source[ByteString, _]]
          )(any[ExecutionContext], any[HeaderCarrier])
          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Departure),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateFailure)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
      }

    }

    "with content type set to None" - {

      // For the content length headers, we have to ensure that we send something
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_LENGTH -> "1000")
      )

      "must return Accepted when call to upscan is success" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary,
        arbitraryBoxResponse.arbitrary,
        arbitraryMovementResponse().arbitrary
      ) {
        (upscanResponse, boxResponse, movementResponse) =>
          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            mockAuditService,
            _,
            _,
            _,
            mockPushNotificationService,
            mockUpscanService,
            _
          ) = createControllerAndMocks()

          when(
            mockUpscanService
              .upscanInitiate(
                any[String].asInstanceOf[EORINumber],
                any[String].asInstanceOf[MovementType],
                any[String].asInstanceOf[MovementId],
                any[String].asInstanceOf[MessageId]
              )(any(), any())
          )
            .thenAnswer {
              _ => EitherT.rightT(upscanResponse)
            }

          when(
            mockPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(MovementId(anyString()), any(), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(boxResponse)
            )

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())).thenReturn(Future.successful(()))

          val request = fakeCreateMovementRequest("POST", standardHeaders, Source.empty[ByteString], MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)

          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(
            HateoasNewMovementResponse(movementResponse.movementId, Some(boxResponse), Some(upscanResponse), MovementType.Departure)
          )

          verify(mockUpscanService, times(1)).upscanInitiate(EORINumber(any()), eqTo(MovementType.Departure), MovementId(any()), MessageId(any()))(any(), any())
          verify(mockPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Departure), any())(any(), any())
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.LargeMessageSubmissionRequested), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())
      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary,
        arbitraryMovementResponse().arbitrary
      ) {
        (upscanResponse, movementResponse) =>
          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            _,
            _,
            _,
            _,
            mockPushNotificationService,
            mockUpscanService,
            _
          ) = createControllerAndMocks()

          when(
            mockUpscanService
              .upscanInitiate(
                any[String].asInstanceOf[EORINumber],
                any[String].asInstanceOf[MovementType],
                any[String].asInstanceOf[MovementId],
                any[String].asInstanceOf[MessageId]
              )(any(), any())
          )
            .thenAnswer {
              _ => EitherT.rightT(upscanResponse)
            }

          when(
            mockPersistenceService
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

          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse.movementId, None, Some(upscanResponse), MovementType.Departure))

          verify(mockUpscanService, times(1)).upscanInitiate(EORINumber(any()), eqTo(MovementType.Departure), MovementId(any()), MessageId(any()))(any(), any())
          verify(mockPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Departure), any())(any(), any())
      }

      "must return Internal Service Error if the persistence service reports an error" in {
        val ControllerAndMocks(
          sut,
          _,
          mockPersistenceService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
        when(
          mockPersistenceService
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

      "must return Internal Service Error if the upscan service reports an error" in forAll(
        arbitraryMovementResponse().arbitrary,
        arbitraryUpscanInitiateResponse.arbitrary
      ) {
        (movementResponse, _) =>
          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            _,
            _,
            _,
            _,
            _,
            mockUpscanService,
            _
          ) = createControllerAndMocks()
          when(
            mockPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(
            mockUpscanService
              .upscanInitiate(
                any[String].asInstanceOf[EORINumber],
                any[String].asInstanceOf[MovementType],
                any[String].asInstanceOf[MovementId],
                any[String].asInstanceOf[MessageId]
              )(any(), any())
          )
            .thenAnswer {
              _ => EitherT.leftT(UpscanError.UnexpectedError(None))
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
      val ControllerAndMocks(
        sut,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _
      ) = createControllerAndMocks()
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

    s"must return NOT_ACCEPTABLE when the accept type is ${VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN}" in {
      val ControllerAndMocks(
        sut,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _
      ) = createControllerAndMocks(
        new AcceptHeaderActionProviderImpl()
      )
      val standardHeaders = FakeHeaders(
        Seq(
          HeaderNames.ACCEPT         -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN,
          HeaderNames.CONTENT_TYPE   -> MimeTypes.JSON,
          HeaderNames.CONTENT_LENGTH -> "1000"
        )
      )

      val json     = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
      val request  = fakeCreateMovementRequest("POST", standardHeaders, Source.single(json), MovementType.Departure)
      val response = sut.createMovement(MovementType.Departure)(request)
      status(response) mustBe NOT_ACCEPTABLE
      contentAsJson(response) mustBe Json.obj(
        "code"    -> "NOT_ACCEPTABLE",
        "message" -> "The Accept header is missing or invalid."
      )
    }

    "must return NOT_ACCEPTABLE when the accept type is invalid" in {
      val ControllerAndMocks(
        sut,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _
      ) = createControllerAndMocks(
        new AcceptHeaderActionProviderImpl()
      )
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON, HeaderNames.CONTENT_LENGTH -> "1000")
      )

      val json     = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
      val request  = fakeCreateMovementRequest("POST", standardHeaders, Source.single(json), MovementType.Departure)
      val response = sut.createMovement(MovementType.Departure)(request)
      status(response) mustBe NOT_ACCEPTABLE
      contentAsJson(response) mustBe Json.obj(
        "code"    -> "NOT_ACCEPTABLE",
        "message" -> "The Accept header is missing or invalid."
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
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            _,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()
          when(
            mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML), any[Long]())(any(), any())).thenReturn(Future.successful(()))

          when(
            mockPersistenceService
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
              _ => EitherT.rightT(boxResponse)
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
            _ => EitherT.rightT(SubmissionRoute.ViaEIS)
          )

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Arrival),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateSuccess)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(())
            }

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007C.mkString), MovementType.Arrival)
          val result  = sut.createMovement(MovementType.Arrival)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse.movementId, Some(boxResponse), None, MovementType.Arrival))

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML), any[Long]())(any(), any())
          verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.ArrivalNotification), any())(any(), any())
          verify(mockPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockRouterService, times(1)).send(eqTo(MessageType.ArrivalNotification), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
            any(),
            any()
          )
          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Arrival),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateSuccess)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Arrival), any())(any(), any())
      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryMovementResponse().arbitrary
      ) {
        movementResponse =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            _,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()

          when(
            mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => EitherT.rightT(())
            )
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML), any[Long]())(any(), any())).thenReturn(Future.successful(()))

          when(
            mockPersistenceService
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
            _ => EitherT.rightT(SubmissionRoute.ViaEIS)
          )

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Arrival),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateSuccess)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(())
            }

          when(mockPushNotificationService.associate(MovementId(anyString()), any(), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.leftT(PushNotificationError.UnexpectedError(None))
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007C.mkString), MovementType.Arrival)
          val result  = sut.createMovement(MovementType.Arrival)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse.movementId, None, None, MovementType.Arrival))

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML), any[Long]())(any(), any())
          verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.ArrivalNotification), any())(any(), any())
          verify(mockPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockRouterService, times(1)).send(eqTo(MessageType.ArrivalNotification), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
            any(),
            any()
          )
          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Arrival),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateSuccess)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Arrival), any())(any(), any())
      }

      "must return error when the persistence service of message status update fails" in forAll(
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            _,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()

          when(
            mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML), any[Long]())(any(), any()))
            .thenReturn(Future.successful(()))

          when(
            mockPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], eqTo(MovementType.Arrival), any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(MovementId(eqTo(movementResponse.movementId.value)), eqTo(MovementType.Arrival), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(boxResponse)
            )

          when(
            mockRouterService.send(
              eqTo(MessageType.ArrivalNotification),
              any[String].asInstanceOf[EORINumber],
              MovementId(eqTo(movementResponse.movementId.value)),
              MessageId(eqTo(movementResponse.messageId.value)),
              any[Source[ByteString, _]]
            )(any[ExecutionContext], any[HeaderCarrier])
          ).thenAnswer(
            _ => EitherT.rightT(SubmissionRoute.ViaEIS)
          )

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Arrival),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateSuccess)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.leftT(PersistenceError.MovementNotFound(movementResponse.movementId, MovementType.Arrival))
            }

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC015C.mkString), MovementType.Arrival)
          val result  = sut.createMovement(MovementType.Arrival)(request)
          status(result) mustBe NOT_FOUND

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML), any[Long]())(any(), any())
          verify(mockValidationService, times(1)).validateXml(eqTo(MessageType.ArrivalNotification), any())(any(), any())
          verify(mockPersistenceService, times(1)).createMovement(EORINumber(any()), eqTo(MovementType.Arrival), any())(any(), any())
          verify(mockRouterService, times(1)).send(
            eqTo(MessageType.ArrivalNotification),
            EORINumber(any()),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            any()
          )(
            any(),
            any()
          )
          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Arrival),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateSuccess)
          )(
            any(),
            any()
          )
          verify(mockPushNotificationService, times(1))
            .associate(MovementId(eqTo(movementResponse.movementId.value)), eqTo(MovementType.Arrival), any())(any(), any())
      }

      "must return Bad Request when body is an XML document that would fail schema validation" in {
        val ControllerAndMocks(
          sut,
          mockValidationService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
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
        val ControllerAndMocks(
          sut,
          mockValidationService,
          mockPersistenceService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
        when(mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
          .thenAnswer(
            _ => EitherT.rightT(())
          )

        when(
          mockPersistenceService
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
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            _,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()

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

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Arrival),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateFailure)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(())
            }

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML), any[Long]())(any(), any())).thenReturn(Future.successful(()))

          when(
            mockPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, MovementResponse](movementResponse)))

          when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(boxResponse)
            )

          val request  = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007C.mkString), MovementType.Arrival)
          val response = sut.createMovement(MovementType.Arrival)(request)

          status(response) mustBe INTERNAL_SERVER_ERROR
          contentAsJson(response) mustBe Json.obj(
            "code"    -> "INTERNAL_SERVER_ERROR",
            "message" -> "Internal server error"
          )

          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Arrival),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateFailure)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
      }
    }

    "with content type set to application/json" - {
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON, HeaderNames.CONTENT_LENGTH -> "1000")
      )

      "must return Accepted when body length is within limits and is considered valid" in forAll(
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            mockConversionService,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()
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
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())).thenReturn(Future.successful(()))

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
            _ => EitherT.rightT(SubmissionRoute.ViaEIS)
          )

          when(
            mockPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Arrival),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateSuccess)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(())
            }

          when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(boxResponse)
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007Cjson), MovementType.Arrival)
          val result  = sut.createMovement(MovementType.Arrival)(request)

          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse.movementId, Some(boxResponse), None, MovementType.Arrival))

          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Arrival),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateSuccess)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
      }

      "must return Payload Too Large when converted JSON in XML is greater in limit" in forAll(
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            mockConversionService,
            _,
            _,
            mockPushNotificationService,
            _,
            mockAppConfig
          ) = createControllerAndMocks()

          when(
            mockValidationService
              .validateJson(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          ).thenAnswer {
            invocation =>
              jsonValidationMockAnswer(MovementType.Arrival)(invocation)
          }
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())).thenReturn(Future.successful(()))

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
          when(mockAppConfig.smallMessageSizeLimit).thenReturn(40)

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007Cjson), MovementType.Arrival)
          val result  = sut.createMovement(MovementType.Arrival)(request)
          status(result) mustBe REQUEST_ENTITY_TOO_LARGE

          contentAsJson(result) mustBe Json.obj(
            "code"    -> "REQUEST_ENTITY_TOO_LARGE",
            "message" -> "Request Entity Too Large"
          )
          verify(mockConversionService, times(1)).jsonToXml(eqTo(MessageType.ArrivalNotification), any())(any(), any(), any())
          verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.ArrivalNotification), any())(any(), any())
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())
      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryMovementResponse().arbitrary
      ) {
        movementResponse =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            mockConversionService,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()
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
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())).thenReturn(Future.successful(()))

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
            _ => EitherT.rightT(SubmissionRoute.ViaEIS)
          )

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Arrival),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateSuccess)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(())
            }

          when(
            mockPersistenceService
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

          contentAsJson(result) mustBe Json.toJson(HateoasNewMovementResponse(movementResponse.movementId, None, None, MovementType.Arrival))

          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Arrival),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateSuccess)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
      }

      "must return Bad Request when body is not an JSON document" in {
        val ControllerAndMocks(
          sut,
          mockValidationService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
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
        val ControllerAndMocks(
          sut,
          mockValidationService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
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
        val ControllerAndMocks(
          sut,
          mockValidationService,
          _,
          _,
          _,
          mockConversionService,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
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
        val ControllerAndMocks(
          sut,
          mockValidationService,
          _,
          _,
          _,
          mockConversionService,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
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
        val ControllerAndMocks(
          sut,
          mockValidationService,
          mockPersistenceService,
          _,
          _,
          mockConversionService,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
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
          mockPersistenceService
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
        verify(mockPersistenceService).createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any(), any())
      }

      "must return Internal Service Error if the router service reports an error" in forAll(
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            _,
            mockConversionService,
            _,
            _,
            mockPushNotificationService,
            _,
            _
          ) = createControllerAndMocks()
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
            mockPersistenceService
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
              _ => EitherT.rightT(boxResponse)
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

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(MovementType.Arrival),
                MovementId(eqTo(movementResponse.movementId.value)),
                MessageId(eqTo(movementResponse.messageId.value)),
                eqTo(messageUpdateFailure)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => EitherT.rightT(())
            }

          val request = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007Cjson), MovementType.Arrival)

          val result = sut.createMovement(MovementType.Arrival)(request)
          status(result) mustBe INTERNAL_SERVER_ERROR

          verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.ArrivalNotification), any())(any(), any())
          verify(mockConversionService).jsonToXml(eqTo(MessageType.ArrivalNotification), any())(any(), any(), any())
          verify(mockPersistenceService).createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any(), any())
          verify(mockRouterService).send(
            eqTo(MessageType.ArrivalNotification),
            any[String].asInstanceOf[EORINumber],
            any[String].asInstanceOf[MovementId],
            any[String].asInstanceOf[MessageId],
            any[Source[ByteString, _]]
          )(any[ExecutionContext], any[HeaderCarrier])
          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(MovementType.Arrival),
            MovementId(eqTo(movementResponse.movementId.value)),
            MessageId(eqTo(movementResponse.messageId.value)),
            eqTo(messageUpdateFailure)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
      }

    }

    "when the content type is not present" - {

      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json")
      )

      "must return Accepted if the call to upscan and the persistence service succeeds" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary,
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (upscanResponse, movementResponse, boxResponse) =>
          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            mockAuditService,
            _,
            _,
            _,
            mockPushNotificationService,
            mockUpscanService,
            _
          ) = createControllerAndMocks()

          when(
            mockUpscanService
              .upscanInitiate(
                any[String].asInstanceOf[EORINumber],
                any[String].asInstanceOf[MovementType],
                any[String].asInstanceOf[MovementId],
                any[String].asInstanceOf[MessageId]
              )(any(), any())
          )
            .thenAnswer {
              _ => EitherT.rightT(upscanResponse)
            }

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())).thenReturn(Future.successful(()))

          when(
            mockPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(mockPushNotificationService.associate(MovementId(anyString()), any(), any())(any(), any()))
            .thenAnswer(
              _ => EitherT.rightT(boxResponse)
            )

          val request = fakeCreateMovementRequest("POST", standardHeaders, Source.empty[ByteString], MovementType.Arrival)
          val result  = sut.createMovement(MovementType.Arrival)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(
            HateoasNewMovementResponse(movementResponse.movementId, Some(boxResponse), Some(upscanResponse), MovementType.Arrival)
          )

          verify(mockUpscanService, times(1)).upscanInitiate(EORINumber(any()), eqTo(MovementType.Arrival), MovementId(any()), MessageId(any()))(any(), any())
          verify(mockPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Arrival), any())(any(), any())
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.LargeMessageSubmissionRequested), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())
      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary,
        arbitraryMovementResponse().arbitrary
      ) {
        (upscanResponse, movementResponse) =>
          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            _,
            _,
            _,
            _,
            mockPushNotificationService,
            mockUpscanService,
            _
          ) = createControllerAndMocks()

          when(
            mockUpscanService
              .upscanInitiate(
                any[String].asInstanceOf[EORINumber],
                any[String].asInstanceOf[MovementType],
                any[String].asInstanceOf[MovementId],
                any[String].asInstanceOf[MessageId]
              )(any(), any())
          )
            .thenAnswer {
              _ => EitherT.rightT(upscanResponse)
            }

          when(
            mockPersistenceService
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
          val result  = sut.createMovement(MovementType.Arrival)(request)
          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(
            HateoasNewMovementResponse(movementResponse.movementId, None, Some(upscanResponse), MovementType.Arrival)
          )

          verify(mockUpscanService, times(1)).upscanInitiate(EORINumber(any()), eqTo(MovementType.Arrival), MovementId(any()), MessageId(any()))(any(), any())
          verify(mockPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Arrival), any())(any(), any())
      }

      "must return Internal Service Error if the persistence service reports an error" in {
        val ControllerAndMocks(
          sut,
          _,
          mockPersistenceService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
        when(
          mockPersistenceService
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

      "must return Internal Service Error if the upscan service reports an error" in forAll(
        arbitraryMovementResponse().arbitrary
      ) {
        movementResponse =>
          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            _,
            _,
            _,
            _,
            _,
            mockUpscanService,
            _
          ) = createControllerAndMocks()
          when(
            mockPersistenceService
              .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer {
              _ => EitherT.rightT(movementResponse)
            }

          when(
            mockUpscanService
              .upscanInitiate(
                any[String].asInstanceOf[EORINumber],
                any[String].asInstanceOf[MovementType],
                any[String].asInstanceOf[MovementId],
                any[String].asInstanceOf[MessageId]
              )(any(), any())
          )
            .thenAnswer {
              _ => EitherT.leftT(UpscanError.UnexpectedError(None))
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
      val ControllerAndMocks(
        sut,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _
      ) = createControllerAndMocks()
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

    s"must return NOT_ACCEPTABLE when the content type is ${VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN}" in {
      val ControllerAndMocks(
        sut,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _
      ) = createControllerAndMocks(
        new AcceptHeaderActionProviderImpl()
      )
      val standardHeaders = FakeHeaders(
        Seq(
          HeaderNames.ACCEPT         -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN,
          HeaderNames.CONTENT_TYPE   -> MimeTypes.JSON,
          HeaderNames.CONTENT_LENGTH -> "1000"
        )
      )

      val json    = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
      val request = fakeCreateMovementRequest("POST", standardHeaders, Source.single(json), MovementType.Arrival)

      val result = sut.createMovement(MovementType.Arrival)(request)
      status(result) mustBe NOT_ACCEPTABLE
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "NOT_ACCEPTABLE",
        "message" -> "The Accept header is missing or invalid."
      )
    }

    "must return NOT_ACCEPTABLE when the content type is invalid" in {
      val ControllerAndMocks(
        sut,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _,
        _
      ) = createControllerAndMocks(
        new AcceptHeaderActionProviderImpl()
      )
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON, HeaderNames.CONTENT_LENGTH -> "1000")
      )

      val json    = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
      val request = fakeCreateMovementRequest("POST", standardHeaders, Source.single(json), MovementType.Arrival)

      val result = sut.createMovement(MovementType.Arrival)(request)
      status(result) mustBe NOT_ACCEPTABLE
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "NOT_ACCEPTABLE",
        "message" -> "The Accept header is missing or invalid."
      )
    }

    "must return Internal Service Error if the router service reports an error" in forAll(
      arbitraryMovementResponse().arbitrary,
      arbitraryBoxResponse.arbitrary
    ) {
      (movementResponse, boxResponse) =>
        val ControllerAndMocks(
          sut,
          mockValidationService,
          mockPersistenceService,
          mockRouterService,
          _,
          _,
          _,
          _,
          mockPushNotificationService,
          _,
          _
        ) = createControllerAndMocks()

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
          mockPersistenceService
            .createMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[Option[Source[ByteString, _]]]())(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
        ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, MovementResponse](movementResponse)))

        when(
          mockPersistenceService
            .updateMessage(
              EORINumber(any()),
              eqTo(MovementType.Arrival),
              MovementId(eqTo(movementResponse.movementId.value)),
              MessageId(eqTo(movementResponse.messageId.value)),
              eqTo(messageUpdateFailure)
            )(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
        )
          .thenAnswer {
            _ => EitherT.rightT(())
          }

        when(mockPushNotificationService.associate(any[String].asInstanceOf[MovementId], any[MovementType], any())(any(), any()))
          .thenAnswer(
            _ => EitherT.rightT(boxResponse)
          )

        val request  = fakeCreateMovementRequest("POST", standardHeaders, singleUseStringSource(CC007C.mkString), MovementType.Arrival)
        val response = sut.createMovement(MovementType.Arrival)(request)
        status(response) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(response) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )

        verify(mockPersistenceService, times(1)).updateMessage(
          EORINumber(any()),
          eqTo(MovementType.Arrival),
          MovementId(eqTo(movementResponse.movementId.value)),
          MessageId(eqTo(movementResponse.messageId.value)),
          eqTo(messageUpdateFailure)
        )(
          any[HeaderCarrier],
          any[ExecutionContext]
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
            .getOrElse("without")} a date filter" in forAll(
            arbitraryMovementId.arbitrary,
            Gen.listOfN(3, arbitraryMessageSummaryXml.arbitrary.sample.head),
            arbitraryPageNumber.arbitrary,
            arbitraryItemCount.arbitrary
          ) {
            (movementId, messageResponse, pageNumber, itemCount) =>
              val summaries = PaginationMessageSummary(TotalCount(messageResponse.length), messageResponse)

              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                _,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessages(
                  EORINumber(any()),
                  any[MovementType],
                  MovementId(any()),
                  any(),
                  any[Option[PageNumber]],
                  any[Option[ItemCount]],
                  any[Option[OffsetDateTime]]
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(summaries)
                )

              val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
              val result  = sut.getMessageIds(movementType, movementId, dateTime, Some(pageNumber), Some(itemCount), dateTime)(request)

              status(result) mustBe OK
              contentAsJson(result) mustBe Json.toJson(
                HateoasMovementMessageIdsResponse(
                  movementId,
                  summaries,
                  dateTime,
                  movementType,
                  Some(pageNumber),
                  Some(itemCount),
                  dateTime
                )
              )
          }
      }

      "when no movement is found and page is 1" in forAll(arbitraryMovementId.arbitrary, arbitraryItemCount.arbitrary) {
        (movementId, itemCount) =>
          val page = Some(PageNumber(1))

          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = createControllerAndMocks()
          when(
            mockPersistenceService.getMessages(
              EORINumber(any()),
              any[MovementType],
              MovementId(any()),
              any(),
              eqTo(page),
              any[Option[ItemCount]],
              any[Option[OffsetDateTime]]
            )(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
          )
            .thenAnswer(
              _ => EitherT.leftT(PersistenceError.MovementNotFound(movementId, movementType))
            )

          val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
          val result  = sut.getMessageIds(movementType, movementId, None, page, Some(itemCount), None)(request)

          status(result) mustBe NOT_FOUND
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "NOT_FOUND",
            "message" -> s"${movementType.movementType.capitalize} movement with ID ${movementId.value} was not found"
          )
      }

      "when no movement is found and page is greater than 1" in forAll(
        arbitraryMovementId.arbitrary,
        arbitraryItemCount.arbitrary,
        arbitraryPageNumber.arbitrary
      ) {
        (movementId, itemCount, pageNumber) =>
          // ensure it's not page 1
          val page = if (pageNumber == PageNumber(1)) PageNumber(2) else pageNumber

          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = createControllerAndMocks()

          // We request page 2 which has no messages and therefore should get a page not found.
          when(
            mockPersistenceService.getMessages(
              EORINumber(any()),
              any[MovementType],
              MovementId(any()),
              any(),
              eqTo(Some(page)),
              any[Option[ItemCount]],
              any[Option[OffsetDateTime]]
            )(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
          )
            .thenAnswer(
              _ => EitherT.leftT(PersistenceError.PageNotFound)
            )

          val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
          val result  = sut.getMessageIds(movementType, movementId, None, Some(page), Some(itemCount), None)(request)

          status(result) mustBe NOT_FOUND
          contentAsJson(result) mustBe Json.obj(
            "message" -> "The requested page does not exist",
            "code"    -> "NOT_FOUND"
          )
      }

      "when an unknown error occurs" in forAll(arbitraryMovementId.arbitrary, arbitraryPageNumber.arbitrary, arbitraryItemCount.arbitrary) {
        (movementId, pageNumber, itemCount) =>
          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = createControllerAndMocks()
          when(
            mockPersistenceService.getMessages(
              EORINumber(any()),
              any[MovementType],
              MovementId(any()),
              any(),
              any[Option[PageNumber]],
              any[Option[ItemCount]],
              any[Option[OffsetDateTime]]
            )(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
          )
            .thenAnswer(
              _ => EitherT.leftT(PersistenceError.UnexpectedError(thr = None))
            )

          val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
          val result  = sut.getMessageIds(movementType, movementId, None, Some(pageNumber), Some(itemCount), None)(request)

          status(result) mustBe INTERNAL_SERVER_ERROR
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "INTERNAL_SERVER_ERROR",
            "message" -> "Internal server error"
          )
      }

      "must return NOT_ACCEPTABLE when the accept type is invalid" in forAll(
        arbitraryMovementId.arbitrary,
        arbitraryPageNumber.arbitrary,
        arbitraryItemCount.arbitrary
      ) {
        (movementId, pageNumber, itemCount) =>
          val ControllerAndMocks(
            sut,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = createControllerAndMocks(
            new AcceptHeaderActionProviderImpl()
          )
          val standardHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
          )

          val request  = FakeRequest("GET", "/", standardHeaders, Source.empty[ByteString])
          val response = sut.getMessageIds(movementType, movementId, None, Some(pageNumber), Some(itemCount), None)(request)

          status(response) mustBe NOT_ACCEPTABLE
          contentAsJson(response) mustBe Json.obj(
            "code"    -> "NOT_ACCEPTABLE",
            "message" -> "The Accept header is missing or invalid."
          )
      }

      "must return NOT_ACCEPTABLE when the accept type is application/vnd.hmrc.2.0+json-xml" in forAll(
        arbitraryMovementId.arbitrary,
        arbitraryPageNumber.arbitrary,
        arbitraryItemCount.arbitrary
      ) {
        (movementId, pageNumber, itemCount) =>
          val ControllerAndMocks(
            sut,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = createControllerAndMocks(
            new AcceptHeaderActionProviderImpl()
          )
          val standardHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json-xml", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
          )

          val request  = FakeRequest("GET", "/", standardHeaders, Source.empty[ByteString])
          val response = sut.getMessageIds(movementType, movementId, None, Some(pageNumber), Some(itemCount), None)(request)

          status(response) mustBe NOT_ACCEPTABLE
          contentAsJson(response) mustBe Json.obj(
            "code"    -> "NOT_ACCEPTABLE",
            "message" -> "The Accept header is missing or invalid."
          )
      }

    }

    s"GET /movements/${movementType.urlFragment}/:movementId/messages/:messageId" - {
      val movementId                       = arbitraryMovementId.arbitrary.sample.value
      val messageId                        = arbitraryMessageId.arbitrary.sample.value
      val objectStoreUri                   = arbitraryObjectStoreURI.arbitrary.sample.value
      val xml                              = "<test>ABC</test>"
      val smallMessageSummaryXml           = arbitraryMessageSummaryXml.arbitrary.sample.value.copy(id = messageId, body = Some(XmlPayload(xml)), uri = None)
      val smallMessageSummaryInObjectStore = smallMessageSummaryXml.copy(body = None, uri = Some(objectStoreUri))
      val smallMessageSummaryJson          = smallMessageSummaryXml.copy(body = Some(JsonPayload("""{"test": "ABC"}""")), uri = None)
      val largeMessageSummaryXml =
        arbitraryMessageSummaryXml.arbitrary.sample.value
          .copy(id = messageId, body = None, uri = Some(objectStoreUri))

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

          s"for a small message, when the accept header equals $acceptHeaderValue" - {

            "when the message is stored in mongo and is found" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                mockConversionService,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(smallMessageSummaryXml)
                )

              if (acceptHeaderValue == VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON) {
                when(
                  mockConversionService.xmlToJson(eqTo(smallMessageSummaryXml.messageType.get), any())(any(), any(), any())
                ).thenReturn(EitherT.rightT(Source.single(ByteString(smallMessageSummaryJson.body.get.value))))
              }

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              status(result) mustBe OK
              acceptHeaderValue match {
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
                  contentAsJson(result) mustBe Json.toJson(
                    HateoasMovementMessageResponse(
                      movementId,
                      messageId,
                      smallMessageSummaryJson,
                      movementType
                    )
                  )
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML | VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN =>
                  contentAsJson(result) mustBe Json.toJson(
                    HateoasMovementMessageResponse(
                      movementId,
                      messageId,
                      smallMessageSummaryXml,
                      movementType
                    )
                  )
              }

            }

            "when the message is stored in object store and is found" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                mockConversionService,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(smallMessageSummaryInObjectStore)
                )

              when(
                mockPersistenceService.getMessageBody(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any(),
                  any(),
                  any()
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(Source.single(ByteString(xml)))
                )

              if (acceptHeaderValue == VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON) {
                when(
                  mockConversionService.xmlToJson(eqTo(smallMessageSummaryXml.messageType.get), any())(any(), any(), any())
                ).thenReturn(EitherT.rightT(Source.single(ByteString(smallMessageSummaryJson.body.get.value))))
              }

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              status(result) mustBe OK
              acceptHeaderValue match {
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
                  contentAsJson(result) mustBe Json.toJson(
                    HateoasMovementMessageResponse(
                      movementId,
                      messageId,
                      smallMessageSummaryJson,
                      movementType
                    )
                  )
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML | VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN =>
                  contentAsJson(result) mustBe Json.toJson(
                    HateoasMovementMessageResponse(
                      movementId,
                      messageId,
                      smallMessageSummaryXml,
                      movementType
                    )
                  )
                case _ => fail("This should not be reached")
              }

            }

            "when the message metadata is stored but the message body has not been stored" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                _,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(
                  EORINumber(any[String]),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value))
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(smallMessageSummaryInObjectStore)
                )

              when(
                mockPersistenceService.getMessageBody(
                  EORINumber(any[String]),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value))
                )(
                  any(),
                  any(),
                  any()
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(PersistenceError.MessageNotFound(movementId, messageId))
                )

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              status(result) mustBe OK
              contentAsJson(result) mustBe HateoasMovementMessageResponse(movementId, messageId, smallMessageSummaryInObjectStore, movementType)

            }

            "when the message is stored in object store but call to retrieve the message fails" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                _,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(smallMessageSummaryInObjectStore)
                )

              when(
                mockPersistenceService.getMessageBody(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any(),
                  any(),
                  any()
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(PersistenceError.UnexpectedError(None))
                )

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              status(result) mustBe INTERNAL_SERVER_ERROR

            }

            "when no message is found" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                _,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(PersistenceError.MessageNotFound(movementId, messageId))
                )

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              status(result) mustBe NOT_FOUND
              contentType(result).get mustBe MimeTypes.JSON
              contentAsJson(result) mustBe Json.obj(
                "code"    -> "NOT_FOUND",
                "message" -> s"Message with ID ${messageId.value} for movement ${movementId.value} was not found"
              )
            }

            "when an unknown error occurs" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                _,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(PersistenceError.UnexpectedError(thr = None))
                )

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              status(result) mustBe INTERNAL_SERVER_ERROR
              contentType(result).get mustBe MimeTypes.JSON
              contentAsJson(result) mustBe Json.obj(
                "code"    -> "INTERNAL_SERVER_ERROR",
                "message" -> "Internal server error"
              )
            }

          }

          s"for a large message,when the accept header equals $acceptHeaderValue" - {

            "when the message is found" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                _,
                _,
                _,
                _,
                _,
                mockAppConfig
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(largeMessageSummaryXml)
                )

              when(mockAppConfig.smallMessageSizeLimit).thenReturn(1)

              when(
                mockPersistenceService.getMessageBody(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any(),
                  any(),
                  any()
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(Source.single(ByteString(xml)))
                )

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              acceptHeaderValue match {
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
                  status(result) mustBe NOT_ACCEPTABLE
                  contentType(result).get mustBe MimeTypes.JSON
                  contentAsJson(result) mustBe Json.obj(
                    "code"    -> "NOT_ACCEPTABLE",
                    "message" -> "Large messages cannot be returned as json"
                  )
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML | VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN =>
                  status(result) mustBe OK
                  contentType(result).get mustBe MimeTypes.JSON
                  contentAsJson(result) mustBe Json.toJson(
                    HateoasMovementMessageResponse(
                      movementId,
                      messageId,
                      largeMessageSummaryXml.copy(body = Some(XmlPayload(xml))),
                      movementType
                    )
                  )
                case _ => fail("This should not be reached")
              }

            }

            "when no message is found" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                _,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(PersistenceError.MessageNotFound(movementId, messageId))
                )

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              status(result) mustBe NOT_FOUND
              contentType(result).get mustBe MimeTypes.JSON
              contentAsJson(result) mustBe Json.obj(
                "code"    -> "NOT_FOUND",
                "message" -> s"Message with ID ${messageId.value} for movement ${movementId.value} was not found"
              )
            }

            "when an unknown error occurs" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                _,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(PersistenceError.UnexpectedError(thr = None))
                )

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              status(result) mustBe INTERNAL_SERVER_ERROR
              contentType(result).get mustBe MimeTypes.JSON
              contentAsJson(result) mustBe Json.obj(
                "code"    -> "INTERNAL_SERVER_ERROR",
                "message" -> "Internal server error"
              )
            }

          }
      }

      "must return NOT_ACCEPTABLE when the accept type is invalid" in forAll(
        arbitraryMovementId.arbitrary
      ) {
        movementId =>
          val ControllerAndMocks(
            sut,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = createControllerAndMocks(
            new AcceptHeaderActionProviderImpl()
          )
          val standardHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
          )

          val request = fakeAttachMessageRequest("POST", standardHeaders, Source.single(ByteString(contentXml.mkString, StandardCharsets.UTF_8)), movementType)

          val result = sut.getMessage(movementType, movementId, messageId)(request)

          status(result) mustBe NOT_ACCEPTABLE
          contentType(result).get mustBe MimeTypes.JSON

      }
    }

    s"GET /movements/${movementType.urlFragment}/:movementId/messages/:messageId/body" - {
      val movementId             = arbitraryMovementId.arbitrary.sample.value
      val messageId              = arbitraryMessageId.arbitrary.sample.value
      val xml                    = "<test>ABC</test>"
      val json                   = Json.obj("test" -> "ABC")
      val smallMessageSummaryXml = arbitraryMessageSummaryXml.arbitrary.sample.value.copy(id = messageId, body = Some(XmlPayload(xml)), uri = None)
      val largeMessageSummaryXml =
        arbitraryMessageSummaryXml.arbitrary.sample.value
          .copy(id = messageId, body = None, uri = Some(ObjectStoreURI("common-transit-convention-traders/movements/123.xml")))
      val smallMessageSummaryJson = smallMessageSummaryXml.copy(body = Some(JsonPayload("""{"test": "ABC"}""")), uri = None)
      val smallMessageSummaryInObjectStore =
        smallMessageSummaryXml.copy(body = None, uri = Some(ObjectStoreURI("common-transit-convention-traders/movements/123.xml")))
      Seq(
        VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON,
        VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_XML
      ).foreach {
        acceptHeaderValue =>
          val headers =
            FakeHeaders(Seq(HeaderNames.ACCEPT -> acceptHeaderValue))
          val request =
            FakeRequest(
              "GET",
              v2.controllers.routes.V2MovementsController.getMessageBody(movementType, movementId, messageId).url,
              headers,
              Source.empty[ByteString]
            )
          s"for a small message, when the accept header equals $acceptHeaderValue" - {

            "when the message is stored in mongo and is found" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                mockConversionService,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(smallMessageSummaryXml)
                )
              if (acceptHeaderValue == VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON) {
                when(
                  mockConversionService.xmlToJson(eqTo(smallMessageSummaryXml.messageType.get), any())(any(), any(), any())
                ).thenReturn(EitherT.rightT(Source.single(ByteString(smallMessageSummaryJson.body.get.value))))
              }
              val result = sut.getMessageBody(movementType, movementId, messageId)(request)

              status(result) mustBe OK
              acceptHeaderValue match {
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
                  contentAsJson(result) mustBe Json.toJson(json)
                  contentType(result).get mustBe MimeTypes.JSON
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_XML =>
                  contentAsString(result) mustBe xml
                  contentType(result).get mustBe MimeTypes.XML
              }

            }

            "when the message is stored in object store and is found" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                mockConversionService,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(smallMessageSummaryInObjectStore)
                )

              when(
                mockPersistenceService.getMessageBody(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any(),
                  any(),
                  any()
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(Source.single(ByteString(xml)))
                )

              if (acceptHeaderValue == VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON) {
                when(
                  mockConversionService.xmlToJson(eqTo(smallMessageSummaryXml.messageType.get), any())(any(), any(), any())
                ).thenReturn(EitherT.rightT(Source.single(ByteString(smallMessageSummaryJson.body.get.value))))
              }

              val result = sut.getMessageBody(movementType, movementId, messageId)(request)

              status(result) mustBe OK
              acceptHeaderValue match {
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
                  contentAsJson(result) mustBe Json.toJson(json)
                  contentType(result).get mustBe MimeTypes.JSON
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_XML =>
                  contentAsString(result) mustBe xml
                  contentType(result).get mustBe MimeTypes.XML
                case _ => fail("This should not be reached")
              }

            }

            "when no message is found" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                _,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(PersistenceError.MessageNotFound(movementId, messageId))
                )

              val result = sut.getMessageBody(movementType, movementId, messageId)(request)

              status(result) mustBe NOT_FOUND
              contentType(result).get mustBe MimeTypes.JSON
              contentAsJson(result) mustBe Json.obj(
                "code"    -> "NOT_FOUND",
                "message" -> s"Message with ID ${messageId.value} for movement ${movementId.value} was not found"
              )
            }

            "when an unknown error occurs" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                _,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(PersistenceError.UnexpectedError(thr = None))
                )

              val result = sut.getMessageBody(movementType, movementId, messageId)(request)

              status(result) mustBe INTERNAL_SERVER_ERROR
              contentType(result).get mustBe MimeTypes.JSON
              contentAsJson(result) mustBe Json.obj(
                "code"    -> "INTERNAL_SERVER_ERROR",
                "message" -> "Internal server error"
              )
            }
          }

          s"for a large message,when the accept header equals $acceptHeaderValue" - {

            "when the message is found but greater than small message limit" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                _,
                _,
                _,
                _,
                _,
                mockAppConfig
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(largeMessageSummaryXml)
                )
              when(mockAppConfig.smallMessageSizeLimit).thenReturn(1)
              when(
                mockPersistenceService.getMessageBody(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any(),
                  any(),
                  any()
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(Source.single(ByteString(xml)))
                )

              val result = sut.getMessageBody(movementType, movementId, messageId)(request)

              acceptHeaderValue match {
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
                  status(result) mustBe NOT_ACCEPTABLE
                  contentType(result).get mustBe MimeTypes.JSON
                  contentAsJson(result) mustBe Json.obj(
                    "code"    -> "NOT_ACCEPTABLE",
                    "message" -> "Messages larger than 1 bytes cannot be retrieved in JSON"
                  )
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_XML =>
                  status(result) mustBe OK
                  contentType(result).get mustBe MimeTypes.XML
                  contentAsString(result) mustBe xml
                case _ => fail("This should not be reached")
              }

            }

            "when the message is found and within the small message limit " in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                mockConversionService,
                _,
                _,
                _,
                _,
                mockAppConfig
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(largeMessageSummaryXml)
                )
              when(mockAppConfig.smallMessageSizeLimit).thenReturn(50000)
              when(
                mockPersistenceService.getMessageBody(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any(),
                  any(),
                  any()
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(Source.single(ByteString(xml)))
                )

              if (acceptHeaderValue == VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON) {
                when(
                  mockConversionService.xmlToJson(eqTo(largeMessageSummaryXml.messageType.get), any())(any(), any(), any())
                ).thenReturn(EitherT.rightT(Source.single(ByteString(Json.stringify(json)))))
              }

              val result = sut.getMessageBody(movementType, movementId, messageId)(request)

              status(result) mustBe OK

              acceptHeaderValue match {
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
                  contentType(result).get mustBe MimeTypes.JSON
                  contentAsJson(result) mustBe Json.toJson(json)
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_XML =>
                  contentType(result).get mustBe MimeTypes.XML
                  contentAsString(result) mustBe xml
                case _ => fail("This should not be reached")
              }

            }

            "when no message is found in database" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                _,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(PersistenceError.MessageNotFound(movementId, messageId))
                )

              val result = sut.getMessageBody(movementType, movementId, messageId)(request)

              status(result) mustBe NOT_FOUND
              contentType(result).get mustBe MimeTypes.JSON
              contentAsJson(result) mustBe Json.obj(
                "code"    -> "NOT_FOUND",
                "message" -> s"Message with ID ${messageId.value} for movement ${movementId.value} was not found"
              )
            }

            "when an unknown error occurs due to service failure" in {
              val ControllerAndMocks(
                sut,
                _,
                mockPersistenceService,
                _,
                _,
                _,
                _,
                _,
                _,
                _,
                _
              ) = createControllerAndMocks()
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(PersistenceError.UnexpectedError(thr = None))
                )

              val result = sut.getMessageBody(movementType, movementId, messageId)(request)

              status(result) mustBe INTERNAL_SERVER_ERROR
              contentType(result).get mustBe MimeTypes.JSON
              contentAsJson(result) mustBe Json.obj(
                "code"    -> "INTERNAL_SERVER_ERROR",
                "message" -> "Internal server error"
              )
            }
          }
      }

      "must return NOT_ACCEPTABLE when the accept header is invalid apart from xml or json" in forAll(
        arbitraryMovementId.arbitrary
      ) {
        movementId =>
          val ControllerAndMocks(
            sut,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = createControllerAndMocks(
            new AcceptHeaderActionProviderImpl()
          )
          val standardHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
          )

          val request = fakeAttachMessageRequest("POST", standardHeaders, Source.single(ByteString(contentXml.mkString, StandardCharsets.UTF_8)), movementType)

          val result = sut.getMessageBody(movementType, movementId, messageId)(request)

          status(result) mustBe NOT_ACCEPTABLE
          contentType(result).get mustBe MimeTypes.JSON
      }

    }

    s"GET /movements/${movementType.movementType}" - {
      val url =
        if (movementType == MovementType.Departure) routing.routes.DeparturesRouter.getDeparturesForEori().url
        else routing.routes.ArrivalsRouter.getArrivalsForEori().url

      "should return ok with json body for movements" in {
        val ControllerAndMocks(
          sut,
          _,
          mockPersistenceService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()

        val enrolmentEORINumber = arbitrary[EORINumber].sample.value
        val dateTime            = OffsetDateTime.of(2022, 8, 4, 11, 34, 42, 0, ZoneOffset.UTC)

        val departureResponse1 = MovementSummary(
          _id = arbitrary[MovementId].sample.value,
          enrollmentEORINumber = enrolmentEORINumber,
          movementEORINumber = Some(arbitrary[EORINumber].sample.get),
          movementReferenceNumber = Some(arbitrary[MovementReferenceNumber].sample.value),
          localReferenceNumber = Some(arbitrary[LocalReferenceNumber].sample.value),
          created = dateTime,
          updated = dateTime.plusHours(1)
        )

        val departureResponse2 = MovementSummary(
          _id = arbitrary[MovementId].sample.value,
          enrollmentEORINumber = enrolmentEORINumber,
          movementEORINumber = Some(arbitrary[EORINumber].sample.get),
          movementReferenceNumber = Some(arbitrary[MovementReferenceNumber].sample.value),
          localReferenceNumber = Some(arbitrary[LocalReferenceNumber].sample.value),
          created = dateTime.plusHours(2),
          updated = dateTime.plusHours(3)
        )
        val departureResponses        = Seq(departureResponse1, departureResponse2)
        val paginationMovementSummary = PaginationMovementSummary(TotalCount(departureResponses.length), departureResponses)

        when(
          mockPersistenceService.getMovements(
            EORINumber(any()),
            any[MovementType],
            any[Option[OffsetDateTime]],
            any[Option[EORINumber]],
            any[Option[MovementReferenceNumber]],
            any[Option[PageNumber]],
            any[Option[ItemCount]],
            any[Option[OffsetDateTime]],
            any[Option[LocalReferenceNumber]]
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        )
          .thenAnswer(
            _ => EitherT.rightT(paginationMovementSummary)
          )
        val request = FakeRequest(
          GET,
          url,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON)),
          AnyContentAsEmpty
        )
        val result = sut.getMovements(movementType, None, None, None, None, None, None, None)(request)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(
          HateoasMovementIdsResponse(
            paginationMovementSummary,
            movementType,
            None,
            None,
            None,
            None,
            None,
            None,
            None
          )
        )
      }

      "should return Ok if persistence service returns empty list for page 1" in {

        val page = Some(PageNumber(1))

        val ControllerAndMocks(
          sut,
          _,
          mockPersistenceService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()

        val summary = PaginationMovementSummary(TotalCount(0), List.empty[MovementSummary])

        when(
          mockPersistenceService.getMovements(
            EORINumber(any()),
            any[MovementType],
            any[Option[OffsetDateTime]],
            any[Option[EORINumber]],
            any[Option[MovementReferenceNumber]],
            eqTo(page),
            any[Option[ItemCount]],
            any[Option[OffsetDateTime]],
            any[Option[LocalReferenceNumber]]
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        )
          .thenAnswer(
            _ => EitherT.rightT(summary)
          )

        val request = FakeRequest(
          GET,
          url,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON)),
          AnyContentAsEmpty
        )
        val result = sut.getMovements(movementType, None, None, None, page, None, None, None)(request)

        status(result) mustBe OK
        contentAsJson(result) mustBe Json.toJson(
          HateoasMovementIdsResponse(
            summary,
            movementType,
            None,
            None,
            None,
            page,
            None,
            None,
            None
          )
        )
      }

      "should return not found if the persistence service returns an empty list for a page number other than 1" in {
        val ControllerAndMocks(
          sut,
          _,
          mockPersistenceService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()

        // We request page 2 which has no movements
        val page = Some(PageNumber(2))

        when(
          mockPersistenceService.getMovements(
            EORINumber(any()),
            any[MovementType],
            any[Option[OffsetDateTime]],
            any[Option[EORINumber]],
            any[Option[MovementReferenceNumber]],
            eqTo(page),
            any[Option[ItemCount]],
            any[Option[OffsetDateTime]],
            any[Option[LocalReferenceNumber]]
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        )
          .thenAnswer(
            _ => EitherT.leftT(PersistenceError.PageNotFound)
          )

        val request = FakeRequest(
          GET,
          url,
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON)),
          AnyContentAsEmpty
        )

        val result = sut.getMovements(movementType, None, None, None, page, None, None, None)(request)

        status(result) mustBe NOT_FOUND

        contentAsJson(result) mustBe Json.obj(
          "message" -> "The requested page does not exist",
          "code"    -> "NOT_FOUND"
        )
      }

      "should return unexpected error for all other errors" in {
        val ControllerAndMocks(
          sut,
          _,
          mockPersistenceService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks()
        when(
          mockPersistenceService.getMovements(
            EORINumber(any()),
            any[MovementType],
            any[Option[OffsetDateTime]],
            any[Option[EORINumber]],
            any[Option[MovementReferenceNumber]],
            any[Option[PageNumber]],
            any[Option[ItemCount]],
            any[Option[OffsetDateTime]],
            any[Option[LocalReferenceNumber]]
          )(
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
        val result = sut.getMovements(movementType, None, None, None, None, None, None, None)(request)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "INTERNAL_SERVER_ERROR",
          "message" -> "Internal server error"
        )
      }

      s"must return NOT_ACCEPTABLE when the accept type is ${VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN}" in {
        val ControllerAndMocks(
          sut,
          _,
          mockPersistenceService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks(
          new AcceptHeaderActionProviderImpl()
        )
        when(
          mockPersistenceService.getMovements(
            EORINumber(any()),
            any[MovementType],
            any[Option[OffsetDateTime]],
            any[Option[EORINumber]],
            any[Option[MovementReferenceNumber]],
            any[Option[PageNumber]],
            any[Option[ItemCount]],
            any[Option[OffsetDateTime]],
            any[Option[LocalReferenceNumber]]
          )(
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
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN)),
          AnyContentAsEmpty
        )
        val result = sut.getMovements(movementType, None, None, None, None, None, None, None)(request)

        status(result) mustBe NOT_ACCEPTABLE
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "NOT_ACCEPTABLE",
          "message" -> "The Accept header is missing or invalid."
        )
      }

      "must return NOT_ACCEPTABLE when the accept type is invalid" in {
        val ControllerAndMocks(
          sut,
          _,
          mockPersistenceService,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks(
          new AcceptHeaderActionProviderImpl()
        )
        when(
          mockPersistenceService.getMovements(
            EORINumber(any()),
            any[MovementType],
            any[Option[OffsetDateTime]],
            any[Option[EORINumber]],
            any[Option[MovementReferenceNumber]],
            any[Option[PageNumber]],
            any[Option[ItemCount]],
            any[Option[OffsetDateTime]],
            any[Option[LocalReferenceNumber]]
          )(
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
          headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123")),
          AnyContentAsEmpty
        )
        val result = sut.getMovements(movementType, None, None, None, None, None, None, None)(request)

        status(result) mustBe NOT_ACCEPTABLE
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "NOT_ACCEPTABLE",
          "message" -> "The Accept header is missing or invalid."
        )
      }

    }

    s"GET  /movements/${movementType.urlFragment}/:movementId" - {

      "should return ok with json body of movement" in forAll(
        arbitrary[EORINumber],
        arbitrary[EORINumber],
        arbitrary[MovementId],
        arbitrary[MovementReferenceNumber],
        arbitrary[LocalReferenceNumber]
      ) {
        (enrollmentEori, movementEori, movementId, mrn, lrn) =>
          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          )               = createControllerAndMocks()
          val createdTime = OffsetDateTime.now()
          val departureResponse = MovementSummary(
            movementId,
            enrollmentEori,
            Some(movementEori),
            Some(mrn),
            Some(lrn),
            createdTime,
            createdTime
          )

          when(mockPersistenceService.getMovement(EORINumber(any()), any[MovementType], MovementId(any()))(any(), any()))
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
                Some(movementEori),
                Some(mrn),
                Some(lrn),
                createdTime,
                createdTime
              ),
              movementType
            )
          )
      }

      "should return movement not found if persistence service returns 404" in forAll(arbitraryMovementId.arbitrary) {
        movementId =>
          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = createControllerAndMocks()
          when(
            mockPersistenceService
              .getMovement(any[String].asInstanceOf[EORINumber], any[MovementType], any[String].asInstanceOf[MovementId])(any(), any())
          )
            .thenAnswer {
              _ =>
                EitherT.leftT(PersistenceError.MovementNotFound(movementId, movementType))
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

          status(result) mustBe NOT_FOUND
      }

      "should return unexpected error for all other errors" in forAll(arbitraryMovementId.arbitrary) {
        movementId =>
          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = createControllerAndMocks()
          when(mockPersistenceService.getMovement(EORINumber(any()), any[MovementType], MovementId(any()))(any(), any()))
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

      "must return NOT_ACCEPTABLE when the accept type is invalid" in forAll(arbitraryMovementId.arbitrary) {
        movementId =>
          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = createControllerAndMocks(
            new AcceptHeaderActionProviderImpl()
          )
          when(mockPersistenceService.getMovement(EORINumber(any()), any[MovementType], MovementId(any()))(any(), any()))
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
            headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123")),
            AnyContentAsEmpty
          )
          val result = sut.getMovement(movementType, movementId)(request)

          status(result) mustBe NOT_ACCEPTABLE
      }

      s"must return NOT_ACCEPTABLE when the accept type is ${VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN}" in forAll(
        arbitraryMovementId.arbitrary
      ) {
        movementId =>
          val ControllerAndMocks(
            sut,
            _,
            mockPersistenceService,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = createControllerAndMocks(
            new AcceptHeaderActionProviderImpl()
          )
          when(mockPersistenceService.getMovement(EORINumber(any()), any[MovementType], MovementId(any()))(any(), any()))
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
            headers = FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN)),
            AnyContentAsEmpty
          )
          val result = sut.getMovement(movementType, movementId)(request)

          status(result) mustBe NOT_ACCEPTABLE
      }
    }

    s"POST /movements/${movementType.urlFragment}/:movementId/messages" - {

      val messageType =
        if (movementType == MovementType.Departure) MessageType.DeclarationAmendment
        else MessageType.UnloadingRemarks

      lazy val messageDataEither: EitherT[Future, ExtractionError, MessageType] =
        EitherT.rightT(messageType)

      "with content type set to application/xml" - {

        val standardHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
        )

        "must return Accepted when body length is within limits and is considered valid" in forAll(
          arbitraryUpdateMovementResponse.arbitrary
        ) {
          updateMovementResponse =>
            val ControllerAndMocks(
              sut,
              mockValidationService,
              mockPersistenceService,
              mockRouterService,
              mockAuditService,
              _,
              mockXmlParsingService,
              _,
              mockPushNotificationService,
              _,
              _
            ) = createControllerAndMocks()

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
              _ => EitherT.rightT(SubmissionRoute.ViaEIS)
            )

            when(
              mockPersistenceService
                .addMessage(any[String].asInstanceOf[MovementId], any[MovementType], any[Option[MessageType]], any[Option[Source[ByteString, _]]]())(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
            ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, UpdateMovementResponse](updateMovementResponse)))

            when(
              mockPersistenceService
                .updateMessage(
                  EORINumber(any()),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(updateMovementResponse.messageId.value)),
                  eqTo(messageUpdateSuccess)
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
            )
              .thenAnswer {
                _ => EitherT.rightT(())
              }

            val request = fakeAttachMessageRequest("POST", standardHeaders, singleUseStringSource(contentXml.mkString), movementType)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe ACCEPTED
            contentAsJson(result) mustBe Json.toJson(HateoasMovementUpdateResponse(movementId, updateMovementResponse.messageId, movementType, None))

            verify(mockAuditService, times(1)).audit(any(), any(), eqTo(MimeTypes.XML), any[Long]())(any(), any())
            verify(mockValidationService, times(1)).validateXml(eqTo(messageType), any())(any(), any())
            verify(mockPersistenceService, times(1)).addMessage(MovementId(any()), any(), any(), any())(any(), any())
            verify(mockRouterService, times(1)).send(eqTo(messageType), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
              any(),
              any()
            )
            verify(mockPushNotificationService, times(1)).update(MovementId(eqTo(movementId.value)))(any(), any())
            verify(mockPersistenceService, times(1)).updateMessage(
              EORINumber(any()),
              eqTo(movementType),
              MovementId(eqTo(movementId.value)),
              MessageId(eqTo(updateMovementResponse.messageId.value)),
              eqTo(messageUpdateSuccess)
            )(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
        }

        "must return Bad Request when body is not an XML document" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              _,
              _,
              _,
              _,
              mockXmlParsingService,
              _,
              _,
              _,
              _
            ) = createControllerAndMocks()
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

        "must return Internal Service Error if the persistence service reports an error" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              mockValidationService,
              mockPersistenceService,
              _,
              _,
              _,
              mockXmlParsingService,
              _,
              _,
              _,
              _
            ) = createControllerAndMocks()
            when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any(), any()))
              .thenReturn(messageDataEither)
            when(
              mockValidationService.validateXml(eqTo(messageType), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
            )
              .thenAnswer(
                _ => EitherT.rightT(())
              )
            when(
              mockPersistenceService
                .addMessage(any[String].asInstanceOf[MovementId], any[MovementType], any[Option[MessageType]], any[Option[Source[ByteString, _]]])(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
            ).thenReturn(EitherT.fromEither[Future](Left[PersistenceError, UpdateMovementResponse](PersistenceError.UnexpectedError(None))))

            val request =
              fakeAttachMessageRequest("POST", standardHeaders, Source.single(ByteString(contentXml.mkString, StandardCharsets.UTF_8)), movementType)
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
          persistence: EitherT[Future, PersistenceError, UpdateMovementResponse] = EitherT.rightT(UpdateMovementResponse(messageId)),
          router: EitherT[Future, RouterError, SubmissionRoute] = EitherT.rightT(SubmissionRoute.ViaEIS),
          persistenceStatus: EitherT[Future, PersistenceError, Unit] = EitherT.rightT(())
        ): ControllerAndMocks = {

          val cam: ControllerAndMocks = createControllerAndMocks()
          val ControllerAndMocks(
            _,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            mockConversionService,
            mockXmlParsingService,
            mockJsonParsingService,
            _,
            _,
            _
          ) = cam
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

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON), any[Long]())(any(), any())).thenReturn(Future.successful(()))

          when(mockConversionService.jsonToXml(any(), any())(any(), any(), any())).thenReturn(conversion)

          when(
            mockPersistenceService
              .addMessage(
                any[String].asInstanceOf[MovementId],
                any[MovementType],
                any[Option[MessageType]],
                any[Option[Source[ByteString, _]]]()
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

          when(
            mockPersistenceService
              .updateMessage(
                EORINumber(any()),
                eqTo(movementType),
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value)),
                eqTo(MessageUpdate(MessageStatus.Success, None, None))
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => persistenceStatus
            }

          cam
        }

        val standardHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        )

        def fakeJsonAttachRequest(content: String): Request[Source[ByteString, _]] =
          fakeAttachMessageRequest("POST", standardHeaders, singleUseStringSource(content), movementType)

        "must return Accepted when body length is within limits and is considered valid" in {
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            mockConversionService,
            _,
            _,
            _,
            _,
            _
          ) = setup()

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(HateoasMovementUpdateResponse(movementId, messageId, movementType, None))

          verify(mockValidationService, times(1)).validateJson(any(), any())(any(), any())
          verify(mockAuditService, times(1)).audit(any(), any(), eqTo(MimeTypes.JSON), any[Long])(any(), any())
          verify(mockConversionService, times(1)).jsonToXml(any(), any())(any(), any(), any())
          verify(mockValidationService, times(1)).validateXml(any(), any())(any(), any())
          verify(mockPersistenceService, times(1)).addMessage(MovementId(any()), any(), any(), any())(any(), any())
          verify(mockRouterService, times(1)).send(any(), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
            any(),
            any()
          )
          verify(mockPersistenceService, times(1)).updateMessage(
            EORINumber(any()),
            eqTo(movementType),
            MovementId(eqTo(movementId.value)),
            MessageId(eqTo(messageId.value)),
            eqTo(messageUpdateSuccess)
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        }

        "must return Payload Too Large when JSON converted XML is not in limit" in {
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            mockConversionService,
            _,
            _,
            _,
            _,
            mockAppConfig
          ) = setup()
          when(mockAppConfig.smallMessageSizeLimit).thenReturn(26)
          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe REQUEST_ENTITY_TOO_LARGE

        }

        "must return Bad Request when unable to find a IE141 message type " in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = setup(extractMessageTypeJson = EitherT.leftT(MessageTypeNotFound("IE141")))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)
            status(result) mustBe BAD_REQUEST
            contentAsJson(result) mustBe Json.obj(
              "code"    -> "BAD_REQUEST",
              "message" -> "IE141 is not a valid message type"
            )
        }

        "must return Bad Request when body is not an JSON document" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = setup(extractMessageTypeJson = EitherT.leftT(ExtractionError.MalformedInput))

            val request = fakeJsonAttachRequest("notJson")
            val result  = sut.attachMessage(movementType, movementId)(request)
            status(result) mustBe BAD_REQUEST
            contentAsJson(result) mustBe Json.obj(
              "code"    -> "BAD_REQUEST",
              "message" -> "Input was malformed"
            )
        }

        "must return Bad Request when unable to find a message type " in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = setup(extractMessageTypeJson = EitherT.leftT(MessageTypeNotFound("contentXml")))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)
            status(result) mustBe BAD_REQUEST
            contentAsJson(result) mustBe Json.obj(
              "code"    -> "BAD_REQUEST",
              "message" -> "contentXml is not a valid message type"
            )
        }

        "must return BadRequest when JsonValidation service doesn't recognise message type" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = setup(validateJson = EitherT.leftT(InvalidMessageTypeError("contentXml")))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)
            status(result) mustBe BAD_REQUEST
            contentAsJson(result) mustBe Json.obj(
              "code"    -> "BAD_REQUEST",
              "message" -> "contentXml is not a valid message type"
            )
        }

        "must return BadRequest when json fails to validate" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = setup(validateJson = EitherT.leftT(JsonSchemaFailedToValidateError(NonEmptyList.one(JsonValidationError("sample", "message")))))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)
            status(result) mustBe BAD_REQUEST
            contentAsJson(result) mustBe Json.obj(
              "message"          -> "Request failed schema validation",
              "code"             -> ErrorCode.SchemaValidation.code,
              "validationErrors" -> Json.arr(Json.obj("schemaPath" -> "sample", "message" -> "message"))
            )
        }

        "must return InternalServerError when Unexpected error validating the json" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = setup(validateJson = EitherT.leftT(FailedToValidateError.UnexpectedError(None)))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return InternalServerError when Unexpected error converting the json to xml" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = setup(conversion = EitherT.leftT(ConversionError.UnexpectedError(None)))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return InternalServerError when xml failed validation" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = setup(validateXml = EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList.one(XmlValidationError(1, 1, "message")))))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return BadRequest when message type not recognised by xml validator" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = setup(validateXml = EitherT.leftT(FailedToValidateError.InvalidMessageTypeError("test")))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe BAD_REQUEST
        }

        "must return InternalServerError when unexpected error from xml validator" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = setup(validateXml = EitherT.leftT(FailedToValidateError.UnexpectedError(None)))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return NotFound when movement not found by Persistence" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = setup(persistence = EitherT.leftT(PersistenceError.MovementNotFound(movementId, movementType)))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe NOT_FOUND
        }

        "must return InternalServerError when Persistence return Unexpected Error" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = setup(persistence = EitherT.leftT(PersistenceError.UnexpectedError(None)))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return InternalServerError when router throws unexpected error" in {
          val ControllerAndMocks(
            sut,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = setup(router = EitherT.leftT(RouterError.UnexpectedError(None)))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return BadRequest when router returns BadRequest" in {
          val ControllerAndMocks(
            sut,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = setup(router = EitherT.leftT(RouterError.UnrecognisedOffice("AB012345", "field")))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe BAD_REQUEST
        }

        // TODO: Fix the intention of this test... or the code behind it
        "must return Accepted when persistence message status is not updated" ignore {
          val ControllerAndMocks(
            sut,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = setup(persistenceStatus = EitherT.leftT(PersistenceError.MovementNotFound(movementId, movementType)))

          val request = fakeJsonAttachRequest(contentJson)
          val result  = sut.attachMessage(movementType, movementId)(request)

          status(result) mustBe ACCEPTED
        }

      }

      "without content type" - {
        val standardHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json")
        )

        val source = Source.empty[ByteString]

        val request: Request[Source[ByteString, _]] =
          fakeAttachMessageRequest("POST", standardHeaders, source, movementType)

        "must return Accepted when body length is within limits and is considered valid" in forAll(
          arbitraryMovementId.arbitrary,
          arbitraryMessageId.arbitrary,
          arbitraryUpscanInitiateResponse.arbitrary
        ) {
          (movementId, messageId, upscanInitiateResponse) =>
            val ControllerAndMocks(
              sut,
              _,
              mockPersistenceService,
              _,
              mockAuditService,
              _,
              _,
              _,
              _,
              mockUpscanService,
              _
            ) = createControllerAndMocks()

            val createdTime = OffsetDateTime.now()
            val movementResponse = MovementSummary(
              movementId,
              arbitrary[EORINumber].sample.value,
              Some(arbitrary[EORINumber].sample.get),
              Some(arbitrary[MovementReferenceNumber].sample.value),
              Some(arbitrary[LocalReferenceNumber].sample.value),
              createdTime,
              createdTime
            )

            when(mockPersistenceService.getMovement(EORINumber(any()), eqTo(movementType), MovementId(eqTo(movementId.value)))(any(), any()))
              .thenAnswer(
                _ => EitherT.rightT(movementResponse)
              )

            when(
              mockPersistenceService
                .addMessage(
                  MovementId(eqTo(movementId.value)),
                  eqTo(movementType),
                  eqTo(None),
                  eqTo(None)
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
            ).thenReturn(EitherT.rightT(UpdateMovementResponse(messageId)))

            when(
              mockUpscanService
                .upscanInitiate(
                  any[String].asInstanceOf[EORINumber],
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value))
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
            )
              .thenAnswer {
                _ => EitherT.rightT(upscanInitiateResponse)
              }

            when(
              mockAuditService
                .audit(
                  eqTo(AuditType.LargeMessageSubmissionRequested),
                  any[Source[ByteString, _]],
                  eqTo(MimeTypes.JSON),
                  any[Long]()
                )(any(), any())
            )
              .thenAnswer {
                _ => Future.successful(())
              }

            val result = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe ACCEPTED
            contentAsJson(result) mustBe Json.toJson(HateoasMovementUpdateResponse(movementId, messageId, movementType, Some(upscanInitiateResponse)))
        }

        "must return NotFound when movement not found by Persistence" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              mockPersistenceService,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = createControllerAndMocks()

            val createdTime = OffsetDateTime.now()
            val movementResponse = MovementSummary(
              movementId,
              arbitrary[EORINumber].sample.value,
              Some(arbitrary[EORINumber].sample.get),
              Some(arbitrary[MovementReferenceNumber].sample.value),
              Some(arbitrary[LocalReferenceNumber].sample.value),
              createdTime,
              createdTime
            )

            when(mockPersistenceService.getMovement(EORINumber(any()), eqTo(movementType), MovementId(eqTo(movementId.value)))(any(), any()))
              .thenAnswer(
                _ => EitherT.rightT(movementResponse)
              )

            when(
              mockPersistenceService
                .addMessage(
                  MovementId(eqTo(movementId.value)),
                  eqTo(movementType),
                  eqTo(None),
                  eqTo(None)
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
            ).thenReturn(EitherT.leftT(PersistenceError.MovementNotFound(movementId, movementType)))

            val result = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe NOT_FOUND
            contentAsJson(result) mustBe Json.obj(
              "message" -> s"${movementType.movementType.capitalize} movement with ID ${movementId.value} was not found",
              "code"    -> "NOT_FOUND"
            )
        }

        "must return InternalServerError when Persistence return Unexpected Error" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              mockPersistenceService,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            )               = createControllerAndMocks()
            val createdTime = OffsetDateTime.now()
            val movementResponse = MovementSummary(
              movementId,
              arbitrary[EORINumber].sample.value,
              Some(arbitrary[EORINumber].sample.get),
              Some(arbitrary[MovementReferenceNumber].sample.value),
              Some(arbitrary[LocalReferenceNumber].sample.value),
              createdTime,
              createdTime
            )

            when(mockPersistenceService.getMovement(EORINumber(any()), eqTo(movementType), MovementId(eqTo(movementId.value)))(any(), any()))
              .thenAnswer(
                _ => EitherT.rightT(movementResponse)
              )
            when(
              mockPersistenceService
                .addMessage(
                  MovementId(eqTo(movementId.value)),
                  eqTo(movementType),
                  eqTo(None),
                  eqTo(None)
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
            ).thenReturn(EitherT.leftT(PersistenceError.UnexpectedError(None)))

            val result = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
            contentAsJson(result) mustBe Json.obj(
              "message" -> "Internal server error",
              "code"    -> "INTERNAL_SERVER_ERROR"
            )
        }

        "must return InternalServerError when Upscan return Unexpected Error" in forAll(
          arbitraryMovementId.arbitrary,
          arbitraryMessageId.arbitrary
        ) {
          (movementId, messageId) =>
            val ControllerAndMocks(
              sut,
              _,
              mockPersistenceService,
              _,
              _,
              _,
              _,
              _,
              _,
              mockUpscanService,
              _
            ) = createControllerAndMocks()

            val createdTime = OffsetDateTime.now()
            val movementResponse = MovementSummary(
              movementId,
              arbitrary[EORINumber].sample.value,
              Some(arbitrary[EORINumber].sample.get),
              Some(arbitrary[MovementReferenceNumber].sample.value),
              Some(arbitrary[LocalReferenceNumber].sample.value),
              createdTime,
              createdTime
            )

            when(mockPersistenceService.getMovement(EORINumber(any()), eqTo(movementType), MovementId(eqTo(movementId.value)))(any(), any()))
              .thenAnswer(
                _ => EitherT.rightT(movementResponse)
              )

            when(
              mockPersistenceService
                .addMessage(
                  MovementId(eqTo(movementId.value)),
                  eqTo(movementType),
                  eqTo(None),
                  eqTo(None)
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
            ).thenReturn(EitherT.rightT(UpdateMovementResponse(messageId)))

            when(
              mockUpscanService
                .upscanInitiate(
                  any[String].asInstanceOf[EORINumber],
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value))
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
            )
              .thenAnswer {
                _ => EitherT.leftT(UpscanError.UnexpectedError(None))
              }

            val result = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
            contentAsJson(result) mustBe Json.obj(
              "message" -> "Internal server error",
              "code"    -> "INTERNAL_SERVER_ERROR"
            )
        }

        "must return NotFound when movement is not found by Persistence getMovement" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            val ControllerAndMocks(
              sut,
              _,
              mockPersistenceService,
              _,
              _,
              _,
              _,
              _,
              _,
              _,
              _
            ) = createControllerAndMocks()

            when(mockPersistenceService.getMovement(EORINumber(any()), eqTo(movementType), MovementId(eqTo(movementId.value)))(any(), any()))
              .thenAnswer(
                _ => EitherT.leftT(PersistenceError.MovementNotFound(movementId, movementType))
              )

            val result = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe NOT_FOUND
            contentAsJson(result) mustBe Json.obj(
              "message" -> s"${movementType.movementType.capitalize} movement with ID ${movementId.value} was not found",
              "code"    -> "NOT_FOUND"
            )
        }

      }

      "must return UNSUPPORTED_MEDIA_TYPE when the content type is invalid" in forAll(
        arbitraryMovementId.arbitrary
      ) {
        movementId =>
          val ControllerAndMocks(
            sut,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = createControllerAndMocks()
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

      "must return NOT_ACCEPTABLE when the accept type is invalid" in {
        val ControllerAndMocks(
          sut,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _,
          _
        ) = createControllerAndMocks(
          new AcceptHeaderActionProviderImpl()
        )
        val standardHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
        )

        val request  = fakeAttachMessageRequest("POST", standardHeaders, Source.single(ByteString(contentXml.mkString, StandardCharsets.UTF_8)), movementType)
        val response = sut.createMovement(movementType)(request)
        status(response) mustBe NOT_ACCEPTABLE
        contentAsJson(response) mustBe Json.obj(
          "code"    -> "NOT_ACCEPTABLE",
          "message" -> "The Accept header is missing or invalid."
        )
      }

      s"must return NOT_ACCEPTABLE when the accept type is ${VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN}" in forAll(
        arbitraryMovementId.arbitrary
      ) {
        _ =>
          val ControllerAndMocks(
            sut,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _,
            _
          ) = createControllerAndMocks(
            new AcceptHeaderActionProviderImpl()
          )
          val standardHeaders = FakeHeaders(
            Seq(
              HeaderNames.ACCEPT         -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN,
              HeaderNames.CONTENT_TYPE   -> MimeTypes.XML,
              HeaderNames.CONTENT_LENGTH -> "1000"
            )
          )

          val request  = fakeAttachMessageRequest("POST", standardHeaders, Source.single(ByteString(contentXml.mkString, StandardCharsets.UTF_8)), movementType)
          val response = sut.createMovement(movementType)(request)
          status(response) mustBe NOT_ACCEPTABLE
          contentAsJson(response) mustBe Json.obj(
            "code"    -> "NOT_ACCEPTABLE",
            "message" -> "The Accept header is missing or invalid."
          )
      }
    }
  }

  "POST /movements/:movementId/messages/:messageId" - {

    "when a success response is received from Upscan" - {

      "if the file can't be downloaded from Upscan, mark as failure and return Ok" in forAll(
        arbitrary[EORINumber],
        arbitrary[MovementType],
        arbitrary[MovementId],
        arbitrary[MessageId]
      ) { // TODO: This is upscan's fault, we should consider a failure response here
        (eori, movementType, movementId, messageId) =>
          val ControllerAndMocks(
            sut,
            mockValidationService,
            mockPersistenceService,
            mockRouterService,
            mockAuditService,
            _,
            mockXmlParsingService,
            _,
            mockPushNotificationService,
            mockUpscanService,
            _
          ) = createControllerAndMocks(
            new AcceptHeaderActionProviderImpl()
          )

          when(mockUpscanService.upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
            .thenReturn(EitherT.leftT(UpscanError.UnexpectedError(None)))

          when(
            mockPushNotificationService.postPpnsNotification(
              MovementId(eqTo(movementId.value)),
              MessageId(eqTo(messageId.value)),
              any[JsValue]
            )(
              any(),
              any()
            )
          )
            .thenReturn(EitherT.rightT(()): EitherT[Future, PushNotificationError, Unit])

          val request                  = FakeRequest[UpscanResponse]("POST", "/", FakeHeaders(), upscanSuccess)
          val response: Future[Result] = sut.attachMessageFromUpscan(eori, movementType, movementId, messageId, None)(request)

          whenReady(response) {
            _ =>
              status(response) mustBe OK

              // common
              verify(mockUpscanService, times(1))
                .upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(any[HeaderCarrier], any[ExecutionContext], any[Materializer])
              verify(mockXmlParsingService, times(0))
                .extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any[HeaderCarrier], any[ExecutionContext])
              verify(mockAuditService, times(0)).audit(any[AuditType], any[Source[ByteString, _]], anyString(), eqTo(upscanSuccess.uploadDetails.size))(
                any[HeaderCarrier],
                any[ExecutionContext]
              )

              verify(mockPersistenceService, times(0)).updateMessageBody(
                any[MessageType],
                EORINumber(eqTo(eori.value)),
                eqTo(movementType),
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value)),
                any[Source[ByteString, _]]
              )(any[HeaderCarrier], any[ExecutionContext])
              verify(mockValidationService, times(0)).validateXml(any[MessageType], any[Source[ByteString, _]])(any(), any())

              // large messages: TODO: hopefully will disappear
              verify(mockPersistenceService, times(0)).getMessage(
                EORINumber(eqTo(eori.value)),
                eqTo(movementType),
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value))
              )(any[HeaderCarrier], any[ExecutionContext])

              // small messages
              verify(mockRouterService, times(0)).send(
                any[MessageType],
                EORINumber(eqTo(eori.value)),
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value)),
                any[Source[ByteString, _]]
              )(any[ExecutionContext], any[HeaderCarrier])

              // failed status
              verify(mockPersistenceService, times(1)).updateMessage(
                EORINumber(eqTo(eori.value)),
                eqTo(movementType),
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value)),
                eqTo(MessageUpdate(MessageStatus.Failed, None, None))
              )(any[HeaderCarrier], any[ExecutionContext])

              // Verify that postPpnsNotification was called
              verify(mockPushNotificationService, times(1)).postPpnsNotification(
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value)),
                any[JsValue]
              )(
                any(),
                any()
              )
          }
      }

      "if the file can be downloaded from Upscan" - {

        "if the message type could not be extracted, mark as failure (bad request) and return Ok" in forAll(
          arbitrary[EORINumber],
          arbitrary[MovementType],
          arbitrary[MovementId],
          arbitrary[MessageId]
        ) {
          (eori, movementType, movementId, messageId) =>
            val ControllerAndMocks(
              sut,
              mockValidationService,
              mockPersistenceService,
              mockRouterService,
              mockAuditService,
              _,
              mockXmlParsingService,
              _,
              mockPushNotificationService,
              mockUpscanService,
              _
            ) = createControllerAndMocks(
              new AcceptHeaderActionProviderImpl()
            )

            val allowedTypes =
              if (movementType == MovementType.Arrival) MessageType.messageTypesSentByArrivalTrader else MessageType.messageTypesSentByDepartureTrader

            when(mockUpscanService.upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
              .thenReturn(EitherT.rightT(singleUseStringSource("<test></test>")))
            when(mockXmlParsingService.extractMessageType(any(), eqTo(allowedTypes))(any(), any())).thenReturn(EitherT.leftT(ExtractionError.MalformedInput))

            when(
              mockPushNotificationService.postPpnsNotification(
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value)),
                any[JsValue]
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
            )
              .thenReturn(EitherT.rightT(()): EitherT[Future, PushNotificationError, Unit])

            val request                  = FakeRequest[UpscanResponse]("POST", "/", FakeHeaders(), upscanSuccess)
            val response: Future[Result] = sut.attachMessageFromUpscan(eori, movementType, movementId, messageId, None)(request)

            whenReady(response) {
              _ =>
                status(response) mustBe OK

                // common
                verify(mockUpscanService, times(1))
                  .upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(any[HeaderCarrier], any[ExecutionContext], any[Materializer])
                verify(mockXmlParsingService, times(1))
                  .extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any[HeaderCarrier], any[ExecutionContext])
                verify(mockAuditService, times(0)).audit(any[AuditType], any[Source[ByteString, _]], anyString(), eqTo(upscanSuccess.uploadDetails.size))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
                verify(mockPersistenceService, times(0)).updateMessageBody(
                  any[MessageType],
                  EORINumber(eqTo(eori.value)),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[Source[ByteString, _]]
                )(any[HeaderCarrier], any[ExecutionContext])
                verify(mockValidationService, times(0)).validateXml(any[MessageType], any[Source[ByteString, _]])(any(), any())

                // large messages: TODO: hopefully will disappear
                verify(mockPersistenceService, times(0)).getMessage(
                  EORINumber(eqTo(eori.value)),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value))
                )(any[HeaderCarrier], any[ExecutionContext])

                // small messages
                verify(mockRouterService, times(0)).send(
                  any[MessageType],
                  EORINumber(eqTo(eori.value)),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[Source[ByteString, _]]
                )(any[ExecutionContext], any[HeaderCarrier])

                // Verify that postPpnsNotification was  called
                verify(mockPushNotificationService, times(1)).postPpnsNotification(
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[JsValue]
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )

                // failed status
                verify(mockPersistenceService, times(1)).updateMessage(
                  EORINumber(eqTo(eori.value)),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  eqTo(MessageUpdate(MessageStatus.Failed, None, None))
                )(any[HeaderCarrier], any[ExecutionContext])
            }
        }

        "if the message could not be validated, mark as failure (schema validation) and return Ok" in forAll(
          arbitrary[EORINumber],
          arbitrary[MovementType],
          arbitrary[MovementId],
          arbitrary[MessageId]
        ) {
          (eori, movementType, movementId, messageId) =>
            val ControllerAndMocks(
              sut,
              mockValidationService,
              mockPersistenceService,
              mockRouterService,
              mockAuditService,
              _,
              mockXmlParsingService,
              _,
              mockPushNotificationService,
              mockUpscanService,
              _
            ) = createControllerAndMocks(
              new AcceptHeaderActionProviderImpl()
            )

            val allowedTypes =
              if (movementType == MovementType.Arrival) MessageType.messageTypesSentByArrivalTrader else MessageType.messageTypesSentByDepartureTrader

            val messageType = Gen.oneOf(allowedTypes).sample.value

            when(mockUpscanService.upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
              .thenReturn(EitherT.rightT(singleUseStringSource("<test></test>")))
            when(mockXmlParsingService.extractMessageType(any(), eqTo(allowedTypes))(any(), any())).thenReturn(EitherT.rightT(messageType))
            // Audit service is ignored so no need to mock. We should verify though, which we do below.
            when(
              mockPersistenceService.updateMessageBody(
                eqTo(messageType),
                EORINumber(eqTo(eori.value)),
                eqTo(movementType),
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value)),
                any[Source[ByteString, _]]
              )(any[HeaderCarrier], any[ExecutionContext])
            )
              .thenReturn(EitherT.rightT((): Unit))
            when(mockValidationService.validateXml(eqTo(messageType), any[Source[ByteString, _]])(any(), any()))
              .thenReturn(EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList.one(XmlValidationError(1, 1, "nope")))))

            when(
              mockPushNotificationService.postPpnsNotification(
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value)),
                any[JsValue]
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
            )
              .thenReturn(EitherT.rightT(()): EitherT[Future, PushNotificationError, Unit])

            val request                  = FakeRequest[UpscanResponse]("POST", "/", FakeHeaders(), upscanSuccess)
            val response: Future[Result] = sut.attachMessageFromUpscan(eori, movementType, movementId, messageId, None)(request)

            whenReady(response) {
              _ =>
                status(response) mustBe OK

                // common
                verify(mockUpscanService, times(1))
                  .upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(any[HeaderCarrier], any[ExecutionContext], any[Materializer])
                verify(mockXmlParsingService, times(1))
                  .extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any[HeaderCarrier], any[ExecutionContext])
                verify(mockAuditService, times(1)).audit(
                  eqTo(messageType.auditType),
                  any[Source[ByteString, _]],
                  anyString(),
                  eqTo(upscanSuccess.uploadDetails.size)
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
                verify(mockValidationService, times(1)).validateXml(eqTo(messageType), any[Source[ByteString, _]])(any(), any())
                verify(mockPersistenceService, times(0)).updateMessageBody(
                  eqTo(messageType),
                  EORINumber(eqTo(eori.value)),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[Source[ByteString, _]]
                )(any[HeaderCarrier], any[ExecutionContext])

                // Verify that postPpnsNotification was not  called
                verify(mockPushNotificationService, times(1)).postPpnsNotification(
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[JsValue]
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )

                // large messages: TODO: hopefully will disappear
                verify(mockPersistenceService, times(0)).getMessage(
                  EORINumber(eqTo(eori.value)),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value))
                )(any[HeaderCarrier], any[ExecutionContext])

                // small messages
                verify(mockRouterService, times(0)).send(
                  any[MessageType],
                  EORINumber(eqTo(eori.value)),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[Source[ByteString, _]]
                )(any[ExecutionContext], any[HeaderCarrier])

                // failed status
                verify(mockPersistenceService, times(1)).updateMessage(
                  EORINumber(eqTo(eori.value)),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  eqTo(MessageUpdate(MessageStatus.Failed, None, None))
                )(any[HeaderCarrier], any[ExecutionContext])

            }

        }

        "if the message could not be stored, mark as failure (internal server error) and return Ok" in forAll(
          arbitrary[EORINumber],
          arbitrary[MovementType],
          arbitrary[MovementId],
          arbitrary[MessageId]
        ) {
          (eori, movementType, movementId, messageId) =>
            val ControllerAndMocks(
              sut,
              mockValidationService,
              mockPersistenceService,
              mockRouterService,
              mockAuditService,
              _,
              mockXmlParsingService,
              _,
              mockPushNotificationService,
              mockUpscanService,
              _
            ) = createControllerAndMocks(
              new AcceptHeaderActionProviderImpl()
            )

            val allowedTypes =
              if (movementType == MovementType.Arrival) MessageType.messageTypesSentByArrivalTrader else MessageType.messageTypesSentByDepartureTrader

            val messageType = Gen.oneOf(allowedTypes).sample.value

            when(mockUpscanService.upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
              .thenReturn(EitherT.rightT(singleUseStringSource("<test></test>")))
            when(mockXmlParsingService.extractMessageType(any(), eqTo(allowedTypes))(any(), any())).thenReturn(EitherT.rightT(messageType))
            // Audit service is ignored so no need to mock. We should verify though, which we do below.
            when(mockValidationService.validateXml(eqTo(messageType), any[Source[ByteString, _]])(any(), any()))
              .thenReturn(EitherT.rightT((): Unit))
            when(
              mockPersistenceService.updateMessageBody(
                eqTo(messageType),
                EORINumber(eqTo(eori.value)),
                eqTo(movementType),
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value)),
                any[Source[ByteString, _]]
              )(any[HeaderCarrier], any[ExecutionContext])
            )
              .thenReturn(EitherT.leftT(PersistenceError.MessageNotFound(movementId, messageId))) // it doesn't matter what the error is really.

            when(
              mockPushNotificationService.postPpnsNotification(
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value)),
                any[JsValue]
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
            )
              .thenReturn(EitherT.rightT(()): EitherT[Future, PushNotificationError, Unit])

            val request                  = FakeRequest[UpscanResponse]("POST", "/", FakeHeaders(), upscanSuccess)
            val response: Future[Result] = sut.attachMessageFromUpscan(eori, movementType, movementId, messageId, None)(request)

            whenReady(response) {
              _ =>
                status(response) mustBe OK

                // common
                verify(mockUpscanService, times(1))
                  .upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(any[HeaderCarrier], any[ExecutionContext], any[Materializer])
                verify(mockXmlParsingService, times(1))
                  .extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any[HeaderCarrier], any[ExecutionContext])
                verify(mockAuditService, times(1)).audit(
                  eqTo(messageType.auditType),
                  any[Source[ByteString, _]],
                  anyString(),
                  eqTo(upscanSuccess.uploadDetails.size)
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
                verify(mockValidationService, times(1)).validateXml(any[MessageType], any[Source[ByteString, _]])(any(), any())
                verify(mockPersistenceService, times(1)).updateMessageBody(
                  eqTo(messageType),
                  EORINumber(eqTo(eori.value)),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[Source[ByteString, _]]
                )(any[HeaderCarrier], any[ExecutionContext])

                // large messages: TODO: hopefully will disappear
                verify(mockPersistenceService, times(0)).getMessage(
                  EORINumber(eqTo(eori.value)),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value))
                )(any[HeaderCarrier], any[ExecutionContext])

                // small messages
                verify(mockRouterService, times(0)).send(
                  any[MessageType],
                  EORINumber(eqTo(eori.value)),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[Source[ByteString, _]]
                )(any[ExecutionContext], any[HeaderCarrier])

                // failed status
                verify(mockPersistenceService, times(1)).updateMessage(
                  EORINumber(eqTo(eori.value)),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  eqTo(MessageUpdate(MessageStatus.Failed, None, None))
                )(any[HeaderCarrier], any[ExecutionContext])

                // Verify that postPpnsNotification was called
                verify(mockPushNotificationService, times(1)).postPpnsNotification(
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[JsValue]
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )

            }
        }

        // TODO: The below can be consolidated when the router logic handles small vs large.
        "if a small message" - {

          "could not be routed, mark as failure (internal server error) and return Ok" in forAll(
            arbitrary[EORINumber],
            arbitrary[MovementType],
            arbitrary[MovementId],
            arbitrary[MessageId]
          ) {
            (eori, movementType, movementId, messageId) =>
              val ControllerAndMocks(
                sut,
                mockValidationService,
                mockPersistenceService,
                mockRouterService,
                mockAuditService,
                _,
                mockXmlParsingService,
                _,
                mockPushNotificationService,
                mockUpscanService,
                mockAppConfig
              ) = createControllerAndMocks(
                new AcceptHeaderActionProviderImpl()
              )

              val allowedTypes =
                if (movementType == MovementType.Arrival) MessageType.messageTypesSentByArrivalTrader else MessageType.messageTypesSentByDepartureTrader

              val messageType = Gen.oneOf(allowedTypes).sample.value

              when(mockUpscanService.upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
                .thenReturn(EitherT.rightT(singleUseStringSource("<test></test>")))
              when(mockXmlParsingService.extractMessageType(any(), eqTo(allowedTypes))(any(), any())).thenReturn(EitherT.rightT(messageType))
              // Audit service is ignored so no need to mock. We should verify though, which we do below.
              when(
                mockPersistenceService.updateMessageBody(
                  eqTo(messageType),
                  EORINumber(eqTo(eori.value)),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[Source[ByteString, _]]
                )(any[HeaderCarrier], any[ExecutionContext])
              )
                .thenReturn(EitherT.rightT((): Unit))
              when(mockValidationService.validateXml(eqTo(messageType), any[Source[ByteString, _]])(any(), any()))
                .thenReturn(EitherT.rightT((): Unit))

              // large message
              when(mockAppConfig.smallMessageSizeLimit).thenReturn(Int.MaxValue)
              when(
                mockRouterService.send(
                  eqTo(messageType),
                  EORINumber(eqTo(eori.value)),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[Source[ByteString, _]]
                )(any[ExecutionContext], any[HeaderCarrier])
              )
                .thenReturn(EitherT.leftT(RouterError.UnrecognisedOffice("office", "office")))

              when(
                mockPushNotificationService.postPpnsNotification(
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[JsValue]
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenReturn(EitherT.rightT(()): EitherT[Future, PushNotificationError, Unit])

              val request                  = FakeRequest[UpscanResponse]("POST", "/", FakeHeaders(), upscanSuccess)
              val response: Future[Result] = sut.attachMessageFromUpscan(eori, movementType, movementId, messageId, None)(request)

              whenReady(response) {
                _ =>
                  status(response) mustBe OK

                  // common
                  verify(mockUpscanService, times(1))
                    .upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(any[HeaderCarrier], any[ExecutionContext], any[Materializer])
                  verify(mockXmlParsingService, times(1))
                    .extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any[HeaderCarrier], any[ExecutionContext])
                  verify(mockAuditService, times(1)).audit(
                    eqTo(messageType.auditType),
                    any[Source[ByteString, _]],
                    anyString(),
                    eqTo(upscanSuccess.uploadDetails.size)
                  )(
                    any[HeaderCarrier],
                    any[ExecutionContext]
                  )
                  verify(mockPersistenceService, times(1)).updateMessageBody(
                    eqTo(messageType),
                    EORINumber(eqTo(eori.value)),
                    eqTo(movementType),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    any[Source[ByteString, _]]
                  )(any[HeaderCarrier], any[ExecutionContext])
                  verify(mockValidationService, times(1)).validateXml(eqTo(messageType), any[Source[ByteString, _]])(any(), any())

                  // large messages: TODO: hopefully will disappear
                  verify(mockPersistenceService, times(0)).getMessage(
                    EORINumber(eqTo(eori.value)),
                    eqTo(movementType),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value))
                  )(any[HeaderCarrier], any[ExecutionContext])

                  // small messages
                  verify(mockRouterService, times(1)).send(
                    any[MessageType],
                    EORINumber(eqTo(eori.value)),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    any[Source[ByteString, _]]
                  )(any[ExecutionContext], any[HeaderCarrier])

                  // success status
                  verify(mockPersistenceService, times(0)).updateMessage(
                    EORINumber(eqTo(eori.value)),
                    eqTo(movementType),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    eqTo(MessageUpdate(MessageStatus.Success, None, None))
                  )(any[HeaderCarrier], any[ExecutionContext])

                  verify(mockPushNotificationService, times(1)).postPpnsNotification(
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    any[JsValue]
                  )(
                    any[HeaderCarrier],
                    any[ExecutionContext]
                  )

                  // failed status
                  verify(mockPersistenceService, times(1)).updateMessage(
                    EORINumber(eqTo(eori.value)),
                    eqTo(movementType),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    eqTo(MessageUpdate(MessageStatus.Failed, None, None))
                  )(any[HeaderCarrier], any[ExecutionContext])

              }

          }

          "could be routed as a small message, update status, push a notification, then return Ok" in forAll(
            arbitrary[EORINumber],
            arbitrary[MovementType],
            arbitrary[MovementId],
            arbitrary[MessageId],
            arbitrary[ClientId]
          ) {
            (eori, movementType, movementId, messageId, clientId) =>
              val ControllerAndMocks(
                sut,
                mockValidationService,
                mockPersistenceService,
                mockRouterService,
                mockAuditService,
                _,
                mockXmlParsingService,
                _,
                mockPushNotificationService,
                mockUpscanService,
                mockAppConfig
              ) = createControllerAndMocks(
                new AcceptHeaderActionProviderImpl()
              )

              val ppnsMessage = Json.toJson(
                Json.obj(
                  "code" -> "SUCCESS",
                  "message" ->
                    s"The message ${messageId.value} for movement ${movementId.value} was successfully processed"
                )
              )

              val allowedTypes =
                if (movementType == MovementType.Arrival) MessageType.messageTypesSentByArrivalTrader else MessageType.messageTypesSentByDepartureTrader

              val messageType = Gen.oneOf(allowedTypes).sample.value

              when(mockUpscanService.upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
                .thenReturn(EitherT.rightT(singleUseStringSource("<test></test>")))
              when(mockXmlParsingService.extractMessageType(any(), eqTo(allowedTypes))(any(), any())).thenReturn(EitherT.rightT(messageType))
              // Audit service is ignored so no need to mock. We should verify though, which we do below.
              when(
                mockPersistenceService.updateMessageBody(
                  eqTo(messageType),
                  EORINumber(eqTo(eori.value)),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[Source[ByteString, _]]
                )(any[HeaderCarrier], any[ExecutionContext])
              )
                .thenReturn(EitherT.rightT((): Unit))
              when(mockValidationService.validateXml(eqTo(messageType), any[Source[ByteString, _]])(any(), any()))
                .thenReturn(EitherT.rightT((): Unit))

              // large message
              when(mockAppConfig.smallMessageSizeLimit).thenReturn(Int.MaxValue)
              when(
                mockRouterService.send(
                  eqTo(messageType),
                  EORINumber(eqTo(eori.value)),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[Source[ByteString, _]]
                )(any[ExecutionContext], any[HeaderCarrier])
              )
                .thenReturn(EitherT.rightT(SubmissionRoute.ViaEIS))

              when(
                mockPushNotificationService.postPpnsNotification(
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  eqTo(ppnsMessage)
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenReturn(EitherT.rightT(()): EitherT[Future, PushNotificationError, Unit])

              val request                  = FakeRequest[UpscanResponse]("POST", "/", FakeHeaders(), upscanSuccess)
              val response: Future[Result] = sut.attachMessageFromUpscan(eori, movementType, movementId, messageId, Some(clientId))(request)

              whenReady(response) {
                _ =>
                  status(response) mustBe OK

                  // common
                  verify(mockUpscanService, times(1))
                    .upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(
                      argThat(HeaderCarrierMatcher.clientId(clientId)),
                      any[ExecutionContext],
                      any[Materializer]
                    )
                  verify(mockXmlParsingService, times(1))
                    .extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(
                      argThat(HeaderCarrierMatcher.clientId(clientId)),
                      any[ExecutionContext]
                    )
                  verify(mockAuditService, times(1)).audit(
                    eqTo(messageType.auditType),
                    any[Source[ByteString, _]],
                    anyString(),
                    eqTo(upscanSuccess.uploadDetails.size)
                  )(
                    argThat(HeaderCarrierMatcher.clientId(clientId)),
                    any[ExecutionContext]
                  )
                  verify(mockPersistenceService, times(1)).updateMessageBody(
                    eqTo(messageType),
                    EORINumber(eqTo(eori.value)),
                    eqTo(movementType),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    any[Source[ByteString, _]]
                  )(argThat(HeaderCarrierMatcher.clientId(clientId)), any[ExecutionContext])

                  verify(mockValidationService, times(1))
                    .validateXml(eqTo(messageType), any[Source[ByteString, _]])(argThat(HeaderCarrierMatcher.clientId(clientId)), any())

                  verify(mockPersistenceService, times(0)).getMessage(
                    EORINumber(eqTo(eori.value)),
                    eqTo(movementType),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value))
                  )(any[HeaderCarrier], any[ExecutionContext])

                  // small messages
                  verify(mockRouterService, times(1)).send(
                    any[MessageType],
                    EORINumber(eqTo(eori.value)),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    any[Source[ByteString, _]]
                  )(any[ExecutionContext], argThat(HeaderCarrierMatcher.clientId(clientId)))

                  // success status
                  verify(mockPersistenceService, times(1)).updateMessage(
                    EORINumber(eqTo(eori.value)),
                    eqTo(movementType),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    eqTo(MessageUpdate(MessageStatus.Success, None, None))
                  )(argThat(HeaderCarrierMatcher.clientId(clientId)), any[ExecutionContext])

                  verify(mockPushNotificationService, times(1)).postPpnsNotification(
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    eqTo(ppnsMessage)
                  )(
                    argThat(HeaderCarrierMatcher.clientId(clientId)),
                    any[ExecutionContext]
                  )

                  // failed status
                  verify(mockPersistenceService, times(0)).updateMessage(
                    EORINumber(eqTo(eori.value)),
                    eqTo(movementType),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    eqTo(MessageUpdate(MessageStatus.Failed, None, None))
                  )(any[HeaderCarrier], any[ExecutionContext])

              }

          }

          "could be routed as a large message, don't update status, don't push a notification, then return Ok" in forAll(
            arbitrary[EORINumber],
            arbitrary[MovementType],
            arbitrary[MovementId],
            arbitrary[MessageId]
          ) {
            (eori, movementType, movementId, messageId) =>
              val ControllerAndMocks(
                sut,
                mockValidationService,
                mockPersistenceService,
                mockRouterService,
                mockAuditService,
                _,
                mockXmlParsingService,
                _,
                mockPushNotificationService,
                mockUpscanService,
                mockAppConfig
              ) = createControllerAndMocks(
                new AcceptHeaderActionProviderImpl()
              )

              val allowedTypes =
                if (movementType == MovementType.Arrival) MessageType.messageTypesSentByArrivalTrader else MessageType.messageTypesSentByDepartureTrader

              val messageType = Gen.oneOf(allowedTypes).sample.value

              when(mockUpscanService.upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(any[HeaderCarrier], any[ExecutionContext], any[Materializer]))
                .thenReturn(EitherT.rightT(singleUseStringSource("<test></test>")))
              when(mockXmlParsingService.extractMessageType(any(), eqTo(allowedTypes))(any(), any())).thenReturn(EitherT.rightT(messageType))
              // Audit service is ignored so no need to mock. We should verify though, which we do below.
              when(
                mockPersistenceService.updateMessageBody(
                  eqTo(messageType),
                  EORINumber(eqTo(eori.value)),
                  eqTo(movementType),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[Source[ByteString, _]]
                )(any[HeaderCarrier], any[ExecutionContext])
              )
                .thenReturn(EitherT.rightT((): Unit))
              when(mockValidationService.validateXml(eqTo(messageType), any[Source[ByteString, _]])(any(), any()))
                .thenReturn(EitherT.rightT((): Unit))

              // large message
              when(mockAppConfig.smallMessageSizeLimit).thenReturn(Int.MaxValue)
              when(
                mockRouterService.send(
                  eqTo(messageType),
                  EORINumber(eqTo(eori.value)),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[Source[ByteString, _]]
                )(any[ExecutionContext], any[HeaderCarrier])
              )
                .thenReturn(EitherT.rightT(SubmissionRoute.ViaSDES))

              when(
                mockPushNotificationService.postPpnsNotification(
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any[JsValue]
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenReturn(EitherT.rightT(()): EitherT[Future, PushNotificationError, Unit])

              val request                  = FakeRequest[UpscanResponse]("POST", "/", FakeHeaders(), upscanSuccess)
              val response: Future[Result] = sut.attachMessageFromUpscan(eori, movementType, movementId, messageId, None)(request)

              whenReady(response) {
                _ =>
                  status(response) mustBe OK

                  // common
                  verify(mockUpscanService, times(1))
                    .upscanGetFile(DownloadUrl(eqTo(upscanDownloadUrl.value)))(any[HeaderCarrier], any[ExecutionContext], any[Materializer])
                  verify(mockXmlParsingService, times(1))
                    .extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any[HeaderCarrier], any[ExecutionContext])
                  verify(mockAuditService, times(1)).audit(
                    eqTo(messageType.auditType),
                    any[Source[ByteString, _]],
                    anyString(),
                    eqTo(upscanSuccess.uploadDetails.size)
                  )(
                    any[HeaderCarrier],
                    any[ExecutionContext]
                  )
                  verify(mockPersistenceService, times(1)).updateMessageBody(
                    eqTo(messageType),
                    EORINumber(eqTo(eori.value)),
                    eqTo(movementType),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    any[Source[ByteString, _]]
                  )(any[HeaderCarrier], any[ExecutionContext])
                  verify(mockValidationService, times(1)).validateXml(eqTo(messageType), any[Source[ByteString, _]])(any(), any())

                  verify(mockPersistenceService, times(0)).getMessage(
                    EORINumber(eqTo(eori.value)),
                    eqTo(movementType),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value))
                  )(any[HeaderCarrier], any[ExecutionContext])

                  verify(mockRouterService, times(1)).send(
                    any[MessageType],
                    EORINumber(eqTo(eori.value)),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    any[Source[ByteString, _]]
                  )(any[ExecutionContext], any[HeaderCarrier])

                  // success status
                  verify(mockPersistenceService, times(0)).updateMessage(
                    EORINumber(eqTo(eori.value)),
                    eqTo(movementType),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    eqTo(MessageUpdate(MessageStatus.Success, None, None))
                  )(any[HeaderCarrier], any[ExecutionContext])

                  // Verify that postPpnsNotification was not  called
                  verify(mockPushNotificationService, times(0)).postPpnsNotification(
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    any[JsValue]
                  )(
                    any[HeaderCarrier],
                    any[ExecutionContext]
                  )

                  // failed status
                  verify(mockPersistenceService, times(0)).updateMessage(
                    EORINumber(eqTo(eori.value)),
                    eqTo(movementType),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    eqTo(MessageUpdate(MessageStatus.Failed, None, None))
                  )(any[HeaderCarrier], any[ExecutionContext])

              }

          }

        }
      }
    }

    "should return Ok when a failure response is received from upscan" in forAll(
      arbitraryEORINumber.arbitrary,
      arbitraryMovementType.arbitrary,
      arbitraryMovementId.arbitrary,
      arbitraryMessageId.arbitrary
    ) {
      (eoriNumber, movementType, movementId, messageId) =>
        val ControllerAndMocks(
          sut,
          _,
          _,
          _,
          mockAuditService,
          _,
          _,
          _,
          mockPushNotificationService,
          _,
          _
        ) = createControllerAndMocks()

        when(
          mockPushNotificationService.postPpnsNotification(
            MovementId(eqTo(movementId.value)),
            MessageId(eqTo(messageId.value)),
            any[JsValue]
          )(
            any[HeaderCarrier],
            any[ExecutionContext]
          )
        )
          .thenReturn(EitherT.rightT(()): EitherT[Future, PushNotificationError, Unit])

        val request = FakeRequest(
          POST,
          v2.controllers.routes.V2MovementsController.attachMessageFromUpscan(eoriNumber, movementType, movementId, messageId, None).url,
          headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)),
          upscanFailed
        )

        val result: Future[Result] = sut.attachMessageFromUpscan(eoriNumber, movementType, movementId, messageId, None)(request)

        status(result) mustBe OK

        def matchEvent(in: String): Boolean =
          Json.parse(in).validate[TraderFailedUploadAuditRequest] match {
            case JsSuccess(value, _) =>
              value mustBe TraderFailedUploadAuditRequest(
                movementId,
                messageId,
                value.enrollmentEORINumber,
                movementType
              )
              true
            case JsError(_) =>
              false
          }

        // failure audit should fire
        verify(mockAuditService, times(1)).audit(
          eqTo(AuditType.TraderFailedUploadEvent),
          argThat(SourceMatcher(matchEvent)),
          eqTo(MimeTypes.JSON),
          eqTo(0L)
        )(
          any[HeaderCarrier],
          any[ExecutionContext]
        )

        // verify(mockAuditService, times(1)).audit(eqTo(AuditType.TraderFailedUploadEvent), any(), eqTo(MimeTypes.JSON), eqTo(0L))(any(), any())

        // Verify that postPpnsNotification was not  called
        verify(mockPushNotificationService, times(1)).postPpnsNotification(
          MovementId(eqTo(movementId.value)),
          MessageId(eqTo(messageId.value)),
          any[JsValue]
        )(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
    }
  }

  "getMovements method" - {
    "when page parameter is zero" - {
      "must return a BAD_REQUEST response" in forAll(
        arbitrary[MovementType]
      ) {
        movementType =>
          val controllerAndMocks = createControllerAndMocks()
          val result: Future[Result] = controllerAndMocks.sut.getMovements(
            movementType = movementType,
            updatedSince = None,
            movementEORI = None,
            movementReferenceNumber = None,
            page = Some(PageNumber(0)),
            count = None,
            receivedUntil = None,
            localReferenceNumber = None
          )(FakeRequest())

          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "BAD_REQUEST",
            "message" -> "The page parameter must be a positive number"
          )
      }
    }

    "when count parameter is zero" - {
      "must return a BAD_REQUEST response" in forAll(
        arbitrary[MovementType]
      ) {
        movementType =>
          val controllerAndMocks = createControllerAndMocks()
          val result: Future[Result] = controllerAndMocks.sut.getMovements(
            movementType = movementType,
            updatedSince = None,
            movementEORI = None,
            movementReferenceNumber = None,
            page = None,
            count = Some(ItemCount(0)),
            receivedUntil = None,
            localReferenceNumber = None
          )(FakeRequest())

          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "BAD_REQUEST",
            "message" -> "The count parameter must be a positive number"
          )
      }
    }
  }

  "getMessageIds method" - {
    "when provided with valid parameters" - {
      "should return OK status" in forAll(
        arbitrary[MovementType],
        arbitrary[MovementId],
        Gen.listOfN(3, arbitraryMessageSummaryXml.arbitrary.sample.head)
      ) {
        (movementType, movementId, messageResponse) =>
          val summaries = PaginationMessageSummary(TotalCount(messageResponse.length), messageResponse)

          val controllerAndMocks = createControllerAndMocks()

          when(
            controllerAndMocks.mockPersistenceService.getMessages(
              EORINumber(any()),
              any[MovementType],
              MovementId(any()),
              any(),
              any[Option[PageNumber]],
              any[Option[ItemCount]],
              any[Option[OffsetDateTime]]
            )(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
          )
            .thenAnswer(
              _ => EitherT.rightT(summaries)
            )

          val result: Future[Result] = controllerAndMocks.sut.getMessageIds(
            movementType = movementType,
            movementId = movementId,
            receivedSince = Some(OffsetDateTime.now),
            page = Some(PageNumber(1)),
            count = Some(ItemCount(10)),
            receivedUntil = Some(OffsetDateTime.now)
          )(FakeRequest())

          status(result) mustBe OK
      }
    }

    "when page parameter is negative" - {
      "must return a BAD_REQUEST response" in forAll(
        arbitrary[MovementType],
        arbitrary[MovementId],
        Gen.listOfN(3, arbitraryMessageSummaryXml.arbitrary.sample.head)
      ) {
        (movementType, movementId, messageResponse) =>
          val summaries = PaginationMessageSummary(TotalCount(messageResponse.length), messageResponse)

          val controllerAndMocks = createControllerAndMocks()

          when(
            controllerAndMocks.mockPersistenceService.getMessages(
              EORINumber(any()),
              any[MovementType],
              MovementId(any()),
              any(),
              any[Option[PageNumber]],
              any[Option[ItemCount]],
              any[Option[OffsetDateTime]]
            )(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
          )
            .thenAnswer(
              _ => EitherT.rightT(summaries)
            )

          val result: Future[Result] = controllerAndMocks.sut.getMessageIds(
            movementType = movementType,
            movementId = movementId,
            receivedSince = Some(OffsetDateTime.now),
            page = Some(PageNumber(-1)),
            count = Some(ItemCount(10)),
            receivedUntil = Some(OffsetDateTime.now)
          )(FakeRequest())

          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "BAD_REQUEST",
            "message" -> "The page parameter must be a positive number"
          )
      }
    }

    "when count parameter is negative" - {
      "must return a BAD_REQUEST response" in forAll(
        arbitrary[MovementType],
        arbitrary[MovementId]
      ) {
        (movementType, movementId) =>
          val controllerAndMocks = createControllerAndMocks()

          val result: Future[Result] = controllerAndMocks.sut.getMessageIds(
            movementType = movementType,
            movementId = movementId,
            receivedSince = Some(OffsetDateTime.now),
            page = Some(PageNumber(1)),
            count = Some(ItemCount(-10)),
            receivedUntil = Some(OffsetDateTime.now)
          )(FakeRequest())

          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "BAD_REQUEST",
            "message" -> "The count parameter must be a positive number"
          )
      }
    }
  }

}
