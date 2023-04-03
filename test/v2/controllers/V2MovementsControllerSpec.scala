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
import play.api.test.Helpers.POST
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.contentAsString
import play.api.test.Helpers.contentType
import play.api.test.Helpers.status
import routing.VersionedRouting
import uk.gov.hmrc.http.HeaderCarrier
import utils.TestMetrics
import v2.base.TestActorSystem
import v2.base.TestCommonGenerators
import v2.base.TestSourceProvider
import v2.controllers.actions.providers.AcceptHeaderActionProviderImpl
import v2.fakes.controllers.actions.FakeAcceptHeaderActionProvider
import v2.fakes.controllers.actions.FakeAuthNewEnrolmentOnlyAction
import v2.models._
import v2.models.errors.ExtractionError.MessageTypeNotFound
import v2.models.errors.FailedToValidateError.InvalidMessageTypeError
import v2.models.errors.FailedToValidateError.JsonSchemaFailedToValidateError
import v2.models.errors._
import v2.models.request.MessageType
import v2.models.request.MessageUpdate
import v2.models.responses.MessageSummary
import v2.models.responses.MovementResponse
import v2.models.responses.MovementSummary
import v2.models.responses.UpdateMovementResponse
import v2.models.responses.UpscanResponse.DownloadUrl
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
import scala.annotation.nowarn

@nowarn("msg=dead code following this construct")
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

  val CC015Cjson = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
  val CC007Cjson = Json.stringify(Json.obj("CC007" -> Json.obj("SynIdeMES1" -> "UNOC")))
  val CC013Cjson = Json.stringify(Json.obj("CC013" -> Json.obj("field" -> "value")))
  val CC044Cjson = Json.stringify(Json.obj("CC044" -> Json.obj("field" -> "value")))

  val jsonSuccessUpscanResponse = Json.obj(
    "reference"   -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "downloadUrl" -> "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
    "fileStatus"  -> "READY",
    "uploadDetails" -> Json.obj(
      "fileName"        -> "test.pdf",
      "fileMimeType"    -> "application/pdf",
      "uploadTimestamp" -> "2018-04-24T09:30:00Z",
      "checksum"        -> "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
      "size"            -> 987
    )
  )

  val jsonFailedUpscanResponse = Json.obj(
    "reference"  -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "fileStatus" -> "FAILED",
    "failureDetails" -> Json.obj(
      "failureReason" -> "QUARANTINE",
      "message"       -> "e.g. This file has a virus"
    )
  )

  val jsonInvalidUpscanResponse = Json.obj(
    "reference"   -> "11370e18-6e24-453e-b45a-76d3e32ea33d",
    "downloadUrl" -> "https://bucketName.s3.eu-west-2.amazonaws.com?1235676",
    "uploadDetails" -> Json.obj(
      "fileName"        -> "test.pdf",
      "fileMimeType"    -> "application/pdf",
      "uploadTimestamp" -> "2018-04-24T09:30:00Z",
      "checksum"        -> "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
      "size"            -> 987
    )
  )

  val mockValidationService         = mock[ValidationService]
  val mockPersistenceService        = mock[PersistenceService]
  val mockRouterService             = mock[RouterService]
  val mockAuditService              = mock[AuditingService]
  val mockConversionService         = mock[ConversionService]
  val mockXmlParsingService         = mock[XmlMessageParsingService]
  val mockJsonParsingService        = mock[JsonMessageParsingService]
  val mockResponseFormatterService  = mock[ResponseFormatterService]
  val mockObjectStoreService        = mock[ObjectStoreService]
  val mockPushNotificationService   = mock[PushNotificationsService]
  val mockUpscanService             = mock[UpscanService]
  val mockAppConfig                 = mock[AppConfig]
  implicit val temporaryFileCreator = SingletonTemporaryFileCreator

  lazy val movementId: MovementId = arbitraryMovementId.arbitrary.sample.get
  lazy val messageId: MessageId   = arbitraryMessageId.arbitrary.sample.get

  lazy val sut: V2MovementsController = new V2MovementsControllerImpl(
    Helpers.stubControllerComponents(),
    FakeAuthNewEnrolmentOnlyAction(),
    mockValidationService,
    mockConversionService,
    mockPersistenceService,
    mockRouterService,
    mockAuditService,
    mockPushNotificationService,
    FakeAcceptHeaderActionProvider,
    new TestMetrics(),
    mockXmlParsingService,
    mockJsonParsingService,
    mockResponseFormatterService,
    mockUpscanService,
    mockObjectStoreService,
    mockAppConfig
  )

  lazy val sutWithAcceptHeader: V2MovementsController = new V2MovementsControllerImpl(
    Helpers.stubControllerComponents(),
    FakeAuthNewEnrolmentOnlyAction(),
    mockValidationService,
    mockConversionService,
    mockPersistenceService,
    mockRouterService,
    mockAuditService,
    mockPushNotificationService,
    new AcceptHeaderActionProviderImpl(),
    new TestMetrics(),
    mockXmlParsingService,
    mockJsonParsingService,
    mockResponseFormatterService,
    mockUpscanService,
    mockObjectStoreService,
    mockAppConfig
  )

  implicit val timeout: Timeout = 5.seconds

  lazy val messageUpdateSuccess =
    MessageUpdate(MessageStatus.Success, None)

  lazy val messageUpdateFailure =
    MessageUpdate(MessageStatus.Failed, None)

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
    when(mockAppConfig.smallMessageSizeLimit).thenReturn(500000)
    reset(mockValidationService)
    reset(mockConversionService)
    reset(mockPersistenceService)
    reset(mockRouterService)
    reset(mockAuditService)
    reset(mockXmlParsingService)
    reset(mockJsonParsingService)
    reset(mockResponseFormatterService)
    reset(mockPushNotificationService)
    reset(mockUpscanService)
    reset(mockObjectStoreService)
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
        arbitraryMovementResponse().arbitrary,
        arbitraryBoxResponse.arbitrary
      ) {
        (movementResponse, boxResponse) =>
          beforeEach()

          when(mockValidationService.validateXml(any[MessageType], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(any(), any(), any())(any(), any())).thenReturn(Future.successful(()))

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
            _ => EitherT.rightT(())
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

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML))(any(), any())
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
          beforeEach()
          when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

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
            _ => EitherT.rightT(())
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

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML))(any(), any())
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
          beforeEach()

          when(
            mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

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
            _ => EitherT.rightT(())
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

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.XML))(any(), any())
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
          beforeEach()

          when(mockValidationService.validateXml(eqTo(MessageType.DeclarationData), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON))(any(), any())).thenReturn(Future.successful(()))

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
            _ => EitherT.rightT(())
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
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.JSON))(any(), any())
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

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryMovementResponse().arbitrary
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
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.JSON))(any(), any())
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
          when(mockAuditService.audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.JSON))(any(), any())).thenReturn(Future.successful(()))

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
            _ => EitherT.rightT(())
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
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.DeclarationData), any(), eqTo(MimeTypes.JSON))(any(), any())
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
          beforeEach()

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

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON))(any(), any())).thenReturn(Future.successful(()))

          val request = fakeCreateMovementRequest("POST", standardHeaders, Source.empty[ByteString], MovementType.Departure)
          val result  = sut.createMovement(MovementType.Departure)(request)

          status(result) mustBe ACCEPTED

          contentAsJson(result) mustBe Json.toJson(
            HateoasNewMovementResponse(movementResponse.movementId, Some(boxResponse), Some(upscanResponse), MovementType.Departure)
          )

          verify(mockUpscanService, times(1)).upscanInitiate(EORINumber(any()), eqTo(MovementType.Departure), MovementId(any()), MessageId(any()))(any(), any())
          verify(mockPersistenceService, times(1)).createMovement(EORINumber(any()), any[MovementType], any())(any(), any())
          verify(mockPushNotificationService, times(1)).associate(MovementId(anyString()), eqTo(MovementType.Departure), any())(any(), any())
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.LargeMessageSubmissionRequested), any(), eqTo(MimeTypes.JSON))(any(), any())
      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary,
        arbitraryMovementResponse().arbitrary
      ) {
        (upscanResponse, movementResponse) =>
          beforeEach()

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
        (movementResponse, upscanResponse) =>
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

    s"must return NOT_ACCEPTABLE when the accept type is ${VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN}" in {

      val standardHeaders = FakeHeaders(
        Seq(
          HeaderNames.ACCEPT         -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN,
          HeaderNames.CONTENT_TYPE   -> MimeTypes.JSON,
          HeaderNames.CONTENT_LENGTH -> "1000"
        )
      )

      val json     = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
      val request  = fakeCreateMovementRequest("POST", standardHeaders, Source.single(json), MovementType.Departure)
      val response = sutWithAcceptHeader.createMovement(MovementType.Departure)(request)
      status(response) mustBe NOT_ACCEPTABLE
      contentAsJson(response) mustBe Json.obj(
        "code"    -> "NOT_ACCEPTABLE",
        "message" -> "The Accept header is missing or invalid."
      )
    }

    "must return NOT_ACCEPTABLE when the accept type is invalid" in forAll(
      arbitraryMovementId.arbitrary
    ) {
      movementId =>
        val standardHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON, HeaderNames.CONTENT_LENGTH -> "1000")
        )

        val json     = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
        val request  = fakeCreateMovementRequest("POST", standardHeaders, Source.single(json), MovementType.Departure)
        val response = sutWithAcceptHeader.createMovement(MovementType.Departure)(request)
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
          beforeEach()
          when(
            mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

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
            _ => EitherT.rightT(())
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

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML))(any(), any())
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
          beforeEach()

          when(
            mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => EitherT.rightT(())
            )
          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

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
            _ => EitherT.rightT(())
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

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML))(any(), any())
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
          beforeEach()

          when(
            mockValidationService.validateXml(eqTo(MessageType.ArrivalNotification), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
          )
            .thenAnswer(
              _ => EitherT.rightT(())
            )

          when(mockAuditService.audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

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
            _ => EitherT.rightT(())
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

          verify(mockAuditService, times(1)).audit(eqTo(AuditType.ArrivalNotification), any(), eqTo(MimeTypes.XML))(any(), any())
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

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())).thenReturn(Future.successful(()))

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

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryMovementResponse().arbitrary
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
          beforeEach()

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

          when(mockAuditService.audit(any(), any(), eqTo(MimeTypes.JSON))(any(), any())).thenReturn(Future.successful(()))

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
          verify(mockAuditService, times(1)).audit(eqTo(AuditType.LargeMessageSubmissionRequested), any(), eqTo(MimeTypes.JSON))(any(), any())
      }

      "must return Accepted if the Push Notification Service reports an error" in forAll(
        arbitraryUpscanInitiateResponse.arbitrary,
        arbitraryMovementResponse().arbitrary
      ) {
        (upscanResponse, movementResponse) =>
          beforeEach()

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

    s"must return NOT_ACCEPTABLE when the content type is ${VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN}" in {
      val standardHeaders = FakeHeaders(
        Seq(
          HeaderNames.ACCEPT         -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN,
          HeaderNames.CONTENT_TYPE   -> MimeTypes.JSON,
          HeaderNames.CONTENT_LENGTH -> "1000"
        )
      )

      val json    = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
      val request = fakeCreateMovementRequest("POST", standardHeaders, Source.single(json), MovementType.Arrival)

      val result = sutWithAcceptHeader.createMovement(MovementType.Arrival)(request)
      status(result) mustBe NOT_ACCEPTABLE
      contentAsJson(result) mustBe Json.obj(
        "code"    -> "NOT_ACCEPTABLE",
        "message" -> "The Accept header is missing or invalid."
      )
    }

    "must return NOT_ACCEPTABLE when the content type is invalid" in {
      val standardHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON, HeaderNames.CONTENT_LENGTH -> "1000")
      )

      val json    = Json.stringify(Json.obj("CC015" -> Json.obj("SynIdeMES1" -> "UNOC")))
      val request = fakeCreateMovementRequest("POST", standardHeaders, Source.single(json), MovementType.Arrival)

      val result = sutWithAcceptHeader.createMovement(MovementType.Arrival)(request)
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
            .getOrElse("without")} a date filter" in forAll(arbitraryMovementId.arbitrary, Gen.listOfN(3, arbitraryMessageSummaryXml.arbitrary.sample.head)) {
            (movementId, messageResponse) =>
              when(
                mockPersistenceService.getMessages(EORINumber(any()), any[MovementType], MovementId(any()), any())(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(messageResponse)
                )

              val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
              val result  = sut.getMessageIds(movementType, movementId, dateTime)(request)

              status(result) mustBe OK
              contentAsJson(result) mustBe Json.toJson(
                HateoasMovementMessageIdsResponse(movementId, messageResponse, dateTime, movementType)
              )
          }
      }

      "when no movement is found" in forAll(arbitraryMovementId.arbitrary) {
        movementId =>
          when(
            mockPersistenceService.getMessages(EORINumber(any()), any[MovementType], MovementId(any()), any())(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
          )
            .thenAnswer(
              _ => EitherT.leftT(PersistenceError.MovementNotFound(movementId, movementType))
            )

          val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
          val result  = sut.getMessageIds(movementType, movementId, None)(request)

          status(result) mustBe NOT_FOUND
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "NOT_FOUND",
            "message" -> s"${movementType.movementType.capitalize} movement with ID ${movementId.value} was not found"
          )
      }

      "when an unknown error occurs" in forAll(arbitraryMovementId.arbitrary) {
        movementId =>
          when(
            mockPersistenceService.getMessages(EORINumber(any()), any[MovementType], MovementId(any()), any())(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
          )
            .thenAnswer(
              _ => EitherT.leftT(PersistenceError.UnexpectedError(thr = None))
            )

          val request = FakeRequest("GET", "/", FakeHeaders(), Source.empty[ByteString])
          val result  = sut.getMessageIds(movementType, movementId, None)(request)

          status(result) mustBe INTERNAL_SERVER_ERROR
          contentAsJson(result) mustBe Json.obj(
            "code"    -> "INTERNAL_SERVER_ERROR",
            "message" -> "Internal server error"
          )
      }

      "must return NOT_ACCEPTABLE when the accept type is invalid" in forAll(
        arbitraryMovementId.arbitrary
      ) {
        movementId =>
          val standardHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
          )

          val request  = FakeRequest("GET", "/", standardHeaders, Source.empty[ByteString])
          val response = sutWithAcceptHeader.getMessageIds(movementType, movementId, None)(request)

          status(response) mustBe NOT_ACCEPTABLE
          contentAsJson(response) mustBe Json.obj(
            "code"    -> "NOT_ACCEPTABLE",
            "message" -> "The Accept header is missing or invalid."
          )
      }

      "must return NOT_ACCEPTABLE when the accept type is application/vnd.hmrc.2.0+json-xml" in forAll(
        arbitraryMovementId.arbitrary
      ) {
        movementId =>
          val standardHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json-xml", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
          )

          val request  = FakeRequest("GET", "/", standardHeaders, Source.empty[ByteString])
          val response = sutWithAcceptHeader.getMessageIds(movementType, movementId, None)(request)

          status(response) mustBe NOT_ACCEPTABLE
          contentAsJson(response) mustBe Json.obj(
            "code"    -> "NOT_ACCEPTABLE",
            "message" -> "The Accept header is missing or invalid."
          )
      }

    }

    s"/movements/${movementType.urlFragment}/:movementId/messages/:messageId" - {
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
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(smallMessageSummaryXml)
                )

              when(
                mockResponseFormatterService.formatMessageSummary(any[MessageSummary], eqTo(acceptHeaderValue))(
                  any[ExecutionContext],
                  any[HeaderCarrier],
                  any[Materializer]
                )
              )
                .thenAnswer(
                  _ =>
                    acceptHeaderValue match {
                      case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON => EitherT.rightT(smallMessageSummaryJson)
                      case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML | VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN =>
                        EitherT.rightT(smallMessageSummaryXml)

                    }
                )

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
                mockObjectStoreService.getMessage(ObjectStoreResourceLocation(any()))(
                  any[ExecutionContext],
                  any[HeaderCarrier]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(Source.single(ByteString(xml)))
                )

              when(
                mockResponseFormatterService.formatMessageSummary(any[MessageSummary], eqTo(acceptHeaderValue))(
                  any[ExecutionContext],
                  any[HeaderCarrier],
                  any[Materializer]
                )
              )
                .thenAnswer(
                  _ =>
                    acceptHeaderValue match {
                      case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON => EitherT.rightT(smallMessageSummaryJson)
                      case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML | VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN =>
                        EitherT.rightT(smallMessageSummaryInObjectStore)

                    }
                )

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

            "when the message is stored in object store but call to retrieve the message fails" in {
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
                mockObjectStoreService.getMessage(ObjectStoreResourceLocation(any()))(
                  any[ExecutionContext],
                  any[HeaderCarrier]
                )
              )
                .thenAnswer(
                  _ => EitherT.leftT(ObjectStoreError.FileNotFound(objectStoreUri.asResourceLocation.get))
                )

              val result = sut.getMessage(movementType, movementId, messageId)(request)

              status(result) mustBe INTERNAL_SERVER_ERROR

            }

            "when no message is found" in {
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

            "when formatter service fails" in {
              when(
                mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
              )
                .thenAnswer(
                  _ => EitherT.rightT(smallMessageSummaryXml)
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

              acceptHeaderValue match {
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON =>
                  status(result) mustBe INTERNAL_SERVER_ERROR
                  contentType(result).get mustBe MimeTypes.JSON
                  contentAsJson(result) mustBe Json.obj(
                    "code"    -> "INTERNAL_SERVER_ERROR",
                    "message" -> "Internal server error"
                  )
                case VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML | VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN =>
                  status(result) mustBe OK
                  contentType(result).get mustBe MimeTypes.JSON
              }
            }

            "when an unknown error occurs" in {
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
                mockObjectStoreService.getMessage(ObjectStoreResourceLocation(any()))(
                  any[ExecutionContext],
                  any[HeaderCarrier]
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
              }

            }

            "when no message is found" in {
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
          val standardHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
          )

          val request = fakeAttachMessageRequest("POST", standardHeaders, Source.single(ByteString(contentXml.mkString, StandardCharsets.UTF_8)), movementType)

          val result = sutWithAcceptHeader.getMessage(movementType, movementId, messageId)(request)

          status(result) mustBe NOT_ACCEPTABLE
          contentType(result).get mustBe MimeTypes.JSON

      }
    }

    s"/movements/${movementType.urlFragment}/:movementId/messages/:messageId/body" - {
      val movementId             = arbitraryMovementId.arbitrary.sample.value
      val messageId              = arbitraryMessageId.arbitrary.sample.value
      val xml                    = "<test>ABC</test>"
      val smallMessageSummaryXml = arbitraryMessageSummaryXml.arbitrary.sample.value.copy(id = messageId, body = Some(XmlPayload(xml)), uri = None)
      val largeMessageSummaryXml =
        arbitraryMessageSummaryXml.arbitrary.sample.value
          .copy(id = messageId, body = None, uri = Some(ObjectStoreURI("common-transit-convention-traders/movements/123.xml")))

      val headers =
        FakeHeaders(Seq(HeaderNames.ACCEPT -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_XML))
      val request =
        FakeRequest(
          "GET",
          v2.controllers.routes.V2MovementsController.getMessageBody(movementType, movementId, messageId).url,
          headers,
          Source.empty[ByteString]
        )

      "for a small message - accept header set to application/vnd.hmrc.2.0+xml" - {

        "when the message is found" in {
          when(
            mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
          )
            .thenAnswer(
              _ => EitherT.rightT(smallMessageSummaryXml)
            )

          when(
            mockResponseFormatterService.formatMessageSummary(any[MessageSummary], eqTo(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_XML))(
              any[ExecutionContext],
              any[HeaderCarrier],
              any[Materializer]
            )
          )
            .thenAnswer(
              _ => EitherT.rightT(xml)
            )

          val result = sut.getMessageBody(movementType, movementId, messageId)(request)

          status(result) mustBe OK
          contentType(result).get mustBe MimeTypes.XML
          contentAsString(result) mustBe xml
        }

        "when no message is found" in {
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

        "when formatter service fails" in {
          when(
            mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
          )
            .thenAnswer(
              _ => EitherT.rightT(smallMessageSummaryXml)
            )

          when(
            mockResponseFormatterService.formatMessageSummary(any[MessageSummary], eqTo(VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_XML))(
              any[ExecutionContext],
              any[HeaderCarrier],
              any[Materializer]
            )
          )
            .thenAnswer(
              _ => EitherT.leftT(PresentationError.internalServiceError())
            )

          val result = sut.getMessageBody(movementType, movementId, messageId)(request)

          status(result) mustBe OK
          contentType(result).get mustBe MimeTypes.XML
          contentAsString(result) mustBe xml
        }

        "when an unknown error occurs" in {
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

      "for a large message - accept header set to application/vnd.hmrc.2.0+xml" - {

        "when the message is found" in {
          when(
            mockPersistenceService.getMessage(EORINumber(any()), any[MovementType], MovementId(any()), MessageId(any()))(
              any[HeaderCarrier],
              any[ExecutionContext]
            )
          )
            .thenAnswer(
              _ => EitherT.rightT(largeMessageSummaryXml)
            )

          when(
            mockObjectStoreService.getMessage(ObjectStoreResourceLocation(any()))(
              any[ExecutionContext],
              any[HeaderCarrier]
            )
          )
            .thenAnswer(
              _ => EitherT.rightT(Source.single(ByteString(xml)))
            )

          val result = sut.getMessageBody(movementType, movementId, messageId)(request)

          status(result) mustBe OK
          contentType(result).get mustBe MimeTypes.XML
          contentAsString(result) mustBe xml
        }

        "when no message is found" in {
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

      "must return NOT_ACCEPTABLE when the accept type is invalid" in forAll(
        arbitraryMovementId.arbitrary
      ) {
        movementId =>
          val standardHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
          )

          val request = fakeAttachMessageRequest("POST", standardHeaders, Source.single(ByteString(contentXml.mkString, StandardCharsets.UTF_8)), movementType)

          val result = sutWithAcceptHeader.getMessageBody(movementType, movementId, messageId)(request)

          status(result) mustBe NOT_ACCEPTABLE
          contentType(result).get mustBe MimeTypes.JSON
      }
    }

    s"GET /movements/${movementType.movementType}" - {
      val url =
        if (movementType == MovementType.Departure) routing.routes.DeparturesRouter.getDeparturesForEori().url
        else routing.routes.ArrivalsRouter.getArrivalsForEori().url

      "should return ok with json body for movements" in {

        val enrolmentEORINumber = arbitrary[EORINumber].sample.value
        val dateTime            = OffsetDateTime.of(2022, 8, 4, 11, 34, 42, 0, ZoneOffset.UTC)

        val departureResponse1 = MovementSummary(
          _id = arbitrary[MovementId].sample.value,
          enrollmentEORINumber = enrolmentEORINumber,
          movementEORINumber = Some(arbitrary[EORINumber].sample.get),
          movementReferenceNumber = Some(arbitrary[MovementReferenceNumber].sample.value),
          created = dateTime,
          updated = dateTime.plusHours(1)
        )

        val departureResponse2 = MovementSummary(
          _id = arbitrary[MovementId].sample.value,
          enrollmentEORINumber = enrolmentEORINumber,
          movementEORINumber = Some(arbitrary[EORINumber].sample.get),
          movementReferenceNumber = Some(arbitrary[MovementReferenceNumber].sample.value),
          created = dateTime.plusHours(2),
          updated = dateTime.plusHours(3)
        )
        val departureResponses = Seq(departureResponse1, departureResponse2)

        when(
          mockPersistenceService.getMovements(EORINumber(any()), any[MovementType], any[Option[OffsetDateTime]], any[Option[EORINumber]])(
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
          mockPersistenceService.getMovements(EORINumber(any()), any[MovementType], any[Option[OffsetDateTime]], any[Option[EORINumber]])(
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
          mockPersistenceService.getMovements(EORINumber(any()), any[MovementType], any[Option[OffsetDateTime]], any[Option[EORINumber]])(
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

      s"must return NOT_ACCEPTABLE when the accept type is ${VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN}" in {
        when(
          mockPersistenceService.getMovements(EORINumber(any()), any[MovementType], any[Option[OffsetDateTime]], any[Option[EORINumber]])(
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
        val result = sutWithAcceptHeader.getMovements(movementType, None, None)(request)

        status(result) mustBe NOT_ACCEPTABLE
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "NOT_ACCEPTABLE",
          "message" -> "The Accept header is missing or invalid."
        )
      }

      "must return NOT_ACCEPTABLE when the accept type is invalid" in {
        when(
          mockPersistenceService.getMovements(EORINumber(any()), any[MovementType], any[Option[OffsetDateTime]], any[Option[EORINumber]])(
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
        val result = sutWithAcceptHeader.getMovements(movementType, None, None)(request)

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
        arbitrary[MovementReferenceNumber]
      ) {
        (enrollmentEori, movementEori, movementId, mrn) =>
          val createdTime = OffsetDateTime.now()
          val departureResponse = MovementSummary(
            movementId,
            enrollmentEori,
            Some(movementEori),
            Some(mrn),
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
                createdTime,
                createdTime
              ),
              movementType
            )
          )
      }

      "should return movement not found if persistence service returns 404" in forAll(arbitraryMovementId.arbitrary) {
        movementId =>
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
          val result = sutWithAcceptHeader.getMovement(movementType, movementId)(request)

          status(result) mustBe NOT_ACCEPTABLE
      }

      s"must return NOT_ACCEPTABLE when the accept type is ${VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN}" in forAll(
        arbitraryMovementId.arbitrary
      ) {
        movementId =>
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
          val result = sutWithAcceptHeader.getMovement(movementType, movementId)(request)

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
          arbitraryMovementId.arbitrary,
          arbitraryUpdateMovementResponse.arbitrary
        ) {
          (movementId, updateMovementResponse) =>
            beforeEach()

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

            verify(mockAuditService, times(1)).audit(any(), any(), eqTo(MimeTypes.XML))(any(), any())
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
          persistence: EitherT[Future, PersistenceError, UpdateMovementResponse] = EitherT.rightT(UpdateMovementResponse(MessageId("456"))),
          router: EitherT[Future, RouterError, Unit] = EitherT.rightT(()),
          persistenceStatus: EitherT[Future, PersistenceError, Unit] = EitherT.rightT(())
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
                eqTo(messageUpdateSuccess)
              )(
                any[HeaderCarrier],
                any[ExecutionContext]
              )
          )
            .thenAnswer {
              _ => persistenceStatus
            }

        }

        val standardHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        )

        def fakeJsonAttachRequest(content: String): Request[Source[ByteString, _]] =
          fakeAttachMessageRequest("POST", standardHeaders, singleUseStringSource(contentJson), movementType)

        "must return Accepted when body length is within limits and is considered valid" - {
          () =>
            beforeEach()

            setup()

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe ACCEPTED

            contentAsJson(result) mustBe Json.toJson(HateoasMovementUpdateResponse(movementId, MessageId("456"), movementType, None))

            verify(mockValidationService, times(1)).validateJson(any(), any())(any(), any())
            verify(mockAuditService, times(1)).audit(any(), any(), eqTo(MimeTypes.JSON))(any(), any())
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

        "must return Bad Request when body is not an JSON document" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            beforeEach()

            setup(extractMessageTypeJson = EitherT.leftT(ExtractionError.MalformedInput))

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
            setup(extractMessageTypeJson = EitherT.leftT(MessageTypeNotFound("contentXml")))

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
            setup(validateJson = EitherT.leftT(InvalidMessageTypeError("contentXml")))

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

        "must return InternalServerError when Unexpected error validating the json" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            setup(validateJson = EitherT.leftT(FailedToValidateError.UnexpectedError(None)))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return InternalServerError when Unexpected error converting the json to xml" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            setup(conversion = EitherT.leftT(ConversionError.UnexpectedError(None)))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return InternalServerError when xml failed validation" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            setup(validateXml = EitherT.leftT(FailedToValidateError.XmlSchemaFailedToValidateError(NonEmptyList.one(XmlValidationError(1, 1, "message")))))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return BadRequest when message type not recognised by xml validator" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            setup(validateXml = EitherT.leftT(FailedToValidateError.InvalidMessageTypeError("test")))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe BAD_REQUEST
        }

        "must return InternalServerError when unexpected error from xml validator" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            setup(validateXml = EitherT.leftT(FailedToValidateError.UnexpectedError(None)))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return NotFound when movement not found by Persistence" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            setup(persistence = EitherT.leftT(PersistenceError.MovementNotFound(movementId, movementType)))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe NOT_FOUND
        }

        "must return InternalServerError when Persistence return Unexpected Error" in forAll(
          arbitraryMovementId.arbitrary
        ) {
          movementId =>
            setup(persistence = EitherT.leftT(PersistenceError.UnexpectedError(None)))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return InternalServerError when router throws unexpected error" - {
          () =>
            setup(router = EitherT.leftT(RouterError.UnexpectedError(None)))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
        }

        "must return BadRequest when router returns BadRequest" - {
          () =>
            setup(router = EitherT.leftT(RouterError.UnrecognisedOffice("AB012345", "field")))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe BAD_REQUEST
        }

        "must return Accepted when persistence message status not updated" - {
          () =>
            setup(persistenceStatus = EitherT.leftT(PersistenceError.MovementNotFound(movementId, movementType)))

            val request = fakeJsonAttachRequest(contentJson)
            val result  = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe ACCEPTED
        }

      }

      "without content type" - {
        val standardHeaders = FakeHeaders(
          Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json")
        )

        val request: Request[Source[ByteString, _]] =
          fakeAttachMessageRequest("POST", standardHeaders, Source.empty[ByteString], movementType)

        "must return Accepted when body length is within limits and is considered valid" in forAll(
          arbitraryMovementId.arbitrary,
          arbitraryMessageId.arbitrary,
          arbitraryUpscanInitiateResponse.arbitrary
        ) {
          (movementId, messageId, upscanInitiateResponse) =>
            beforeEach()

            when(
              mockPersistenceService
                .addMessage(
                  any[String].asInstanceOf[MovementId],
                  any[MovementType],
                  any[Option[MessageType]],
                  any[Option[Source[ByteString, _]]]
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
            ).thenReturn(EitherT.rightT(UpdateMovementResponse(messageId)))

            when(
              mockUpscanService
                .upscanInitiate(
                  any[String].asInstanceOf[EORINumber],
                  any[MovementType],
                  any[String].asInstanceOf[MovementId],
                  any[String].asInstanceOf[MessageId]
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
                  any[AuditType],
                  any[Source[ByteString, _]],
                  anyString()
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
            when(
              mockPersistenceService
                .addMessage(
                  any[String].asInstanceOf[MovementId],
                  any[MovementType],
                  any[Option[MessageType]],
                  any[Option[Source[ByteString, _]]]
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
            when(
              mockPersistenceService
                .addMessage(
                  any[String].asInstanceOf[MovementId],
                  any[MovementType],
                  any[Option[MessageType]],
                  any[Option[Source[ByteString, _]]]
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
            when(
              mockPersistenceService
                .addMessage(
                  any[String].asInstanceOf[MovementId],
                  any[MovementType],
                  any[Option[MessageType]],
                  any[Option[Source[ByteString, _]]]
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
            ).thenReturn(EitherT.rightT(UpdateMovementResponse(messageId)))

            when(
              mockUpscanService
                .upscanInitiate(
                  any[String].asInstanceOf[EORINumber],
                  any[MovementType],
                  any[String].asInstanceOf[MovementId],
                  any[String].asInstanceOf[MessageId]
                )(
                  any[HeaderCarrier],
                  any[ExecutionContext]
                )
            )
              .thenAnswer {
                _ => EitherT.leftT(UpscanInitiateError.UnexpectedError(None))
              }

            val result = sut.attachMessage(movementType, movementId)(request)

            status(result) mustBe INTERNAL_SERVER_ERROR
            contentAsJson(result) mustBe Json.obj(
              "message" -> "Internal server error",
              "code"    -> "INTERNAL_SERVER_ERROR"
            )
        }

      }

      "must return UNSUPPORTED_MEDIA_TYPE when the content type is invalid" in forAll(
        arbitraryMovementId.arbitrary
      ) {
        movementId =>
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

      "must return NOT_ACCEPTABLE when the accept type is invalid" in forAll(
        arbitraryMovementId.arbitrary
      ) {
        movementId =>
          val standardHeaders = FakeHeaders(
            Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json123", HeaderNames.CONTENT_TYPE -> MimeTypes.XML, HeaderNames.CONTENT_LENGTH -> "1000")
          )

          val request  = fakeAttachMessageRequest("POST", standardHeaders, Source.single(ByteString(contentXml.mkString, StandardCharsets.UTF_8)), movementType)
          val response = sutWithAcceptHeader.createMovement(movementType)(request)
          status(response) mustBe NOT_ACCEPTABLE
          contentAsJson(response) mustBe Json.obj(
            "code"    -> "NOT_ACCEPTABLE",
            "message" -> "The Accept header is missing or invalid."
          )
      }

      s"must return NOT_ACCEPTABLE when the accept type is ${VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN}" in forAll(
        arbitraryMovementId.arbitrary
      ) {
        movementId =>
          val standardHeaders = FakeHeaders(
            Seq(
              HeaderNames.ACCEPT         -> VersionedRouting.VERSION_2_ACCEPT_HEADER_VALUE_JSON_XML_HYPHEN,
              HeaderNames.CONTENT_TYPE   -> MimeTypes.XML,
              HeaderNames.CONTENT_LENGTH -> "1000"
            )
          )

          val request  = fakeAttachMessageRequest("POST", standardHeaders, Source.single(ByteString(contentXml.mkString, StandardCharsets.UTF_8)), movementType)
          val response = sutWithAcceptHeader.createMovement(movementType)(request)
          status(response) mustBe NOT_ACCEPTABLE
          contentAsJson(response) mustBe Json.obj(
            "code"    -> "NOT_ACCEPTABLE",
            "message" -> "The Accept header is missing or invalid."
          )
      }
    }
  }

  "POST /movements/:movementId/messages/:messageId" - {

    "should return Ok when response from upscan is valid" - {

      "and uploading to object-store succeeds" - {

        "and retrieving the source from the object store succeeds" - {

          "and extracting the message type succeeds" - {

            "and persisting the object store url succeeds" - {

              "and validation succeeds" - {

                "and sending the message to router succeeds" - {

                  "and sending the audit message succeeds" in forAll(
                    arbitraryEORINumber.arbitrary,
                    arbitraryMovementType.arbitrary,
                    arbitraryMovementId.arbitrary,
                    arbitraryMessageId.arbitrary,
                    arbitraryObjectSummaryWithMd5.arbitrary
                  ) {
                    (eoriNumber, movementType, movementId, messageId, objectSummary) =>
                      beforeEach()

                      // get the url without the surrounding " " quotes
                      val upscanUrl = jsonSuccessUpscanResponse.value("downloadUrl").toString().stripPrefix("\"").stripSuffix("\"")

                      when(
                        mockObjectStoreService.addMessage(
                          DownloadUrl(eqTo(upscanUrl)),
                          MovementId(eqTo(movementId.value)),
                          MessageId(eqTo(messageId.value))
                        )(
                          any[HeaderCarrier],
                          any[ExecutionContext]
                        )
                      ).thenReturn(EitherT.rightT(objectSummary))

                      when(
                        mockObjectStoreService.getMessage(
                          any[String].asInstanceOf[ObjectStoreResourceLocation]
                        )(
                          any(),
                          any()
                        )
                      ).thenReturn(EitherT.rightT[Future, ObjectStoreError](singleUseStringSource("a source response")))

                      when(
                        mockPersistenceService.updateMessage(
                          EORINumber(eqTo(eoriNumber.value)),
                          any[String].asInstanceOf[MovementType],
                          MovementId(eqTo(movementId.value)),
                          MessageId(eqTo(messageId.value)),
                          any[MessageUpdate]
                        )(
                          any(),
                          any()
                        )
                      ).thenReturn(EitherT.rightT(()))

                      when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any(), any()))
                        .thenReturn(EitherT.rightT(MessageType.DeclarationAmendment))

                      when(
                        mockValidationService.validateLargeMessage(
                          eqTo(MessageType.DeclarationAmendment),
                          any[String].asInstanceOf[ObjectStoreResourceLocation]
                        )(
                          any[HeaderCarrier],
                          any[ExecutionContext]
                        )
                      )
                        .thenAnswer {
                          _ => EitherT.rightT(())
                        }

                      when(
                        mockRouterService.sendLargeMessage(
                          any[String].asInstanceOf[MessageType],
                          EORINumber(eqTo(eoriNumber.value)),
                          MovementId(eqTo(movementId.value)),
                          MessageId(eqTo(messageId.value)),
                          any[String].asInstanceOf[ObjectStoreURI]
                        )(any[ExecutionContext], any[HeaderCarrier])
                      ).thenAnswer(
                        _ => EitherT.rightT(())
                      )

                      when(
                        mockAuditService.audit(
                          eqTo(AuditType.DeclarationData),
                          any[String].asInstanceOf[ObjectStoreResourceLocation]
                        )(
                          any(),
                          any()
                        )
                      ).thenReturn(Future.successful(()))

                      val request = FakeRequest(
                        POST,
                        routes.V2MovementsController.attachLargeMessage(eoriNumber, movementType, movementId, messageId).url,
                        headers = FakeHeaders(),
                        jsonSuccessUpscanResponse
                      )
                      val result = sut.attachLargeMessage(eoriNumber, movementType, movementId, messageId)(request)

                      status(result) mustBe OK

                      verify(mockObjectStoreService, times(1)).addMessage(
                        DownloadUrl(eqTo(upscanUrl)),
                        MovementId(eqTo(movementId.value)),
                        MessageId(eqTo(messageId.value))
                      )(any(), any())

                      verify(mockObjectStoreService, times(1)).getMessage(ObjectStoreResourceLocation(any()))(any(), any())

                      verify(mockXmlParsingService, times(1)).extractMessageType(any(), any())(any(), any())

                      verify(mockValidationService, times(1)).validateLargeMessage(any(), any())(any(), any())

                      verify(mockPersistenceService, times(1)).updateMessage(
                        EORINumber(eqTo(eoriNumber.value)),
                        any[MovementType],
                        MovementId(eqTo(movementId.value)),
                        MessageId(eqTo(messageId.value)),
                        any()
                      )(
                        any(),
                        any()
                      )
                      verify(mockRouterService, times(1))
                        .sendLargeMessage(
                          any[MessageType],
                          EORINumber(eqTo(eoriNumber.value)),
                          MovementId(eqTo(movementId.value)),
                          MessageId(eqTo(messageId.value)),
                          ObjectStoreURI(any())
                        )(any(), any())

                      verify(mockAuditService, times(1))
                        .audit(
                          eqTo(AuditType.DeclarationAmendment),
                          ObjectStoreResourceLocation(any())
                        )(any(), any())
                  }

                  "and sending the audit message fails" in forAll(
                    arbitraryEORINumber.arbitrary,
                    arbitraryMovementType.arbitrary,
                    arbitraryMovementId.arbitrary,
                    arbitraryMessageId.arbitrary,
                    arbitraryObjectSummaryWithMd5.arbitrary
                  ) {
                    (eoriNumber, movementType, movementId, messageId, objectSummary) =>
                      beforeEach()

                      // get the url without the " quotes
                      val upscanUrl = jsonSuccessUpscanResponse.value("downloadUrl").toString().stripPrefix("\"").stripSuffix("\"")

                      when(
                        mockObjectStoreService.addMessage(
                          DownloadUrl(eqTo(upscanUrl)),
                          MovementId(eqTo(movementId.value)),
                          MessageId(eqTo(messageId.value))
                        )(
                          any[HeaderCarrier],
                          any[ExecutionContext]
                        )
                      ).thenReturn(EitherT.rightT(objectSummary))

                      when(
                        mockObjectStoreService.getMessage(
                          any[String].asInstanceOf[ObjectStoreResourceLocation]
                        )(
                          any(),
                          any()
                        )
                      ).thenReturn(EitherT.rightT[Future, ObjectStoreError](singleUseStringSource("a source response")))

                      when(
                        mockPersistenceService.updateMessage(
                          EORINumber(eqTo(eoriNumber.value)),
                          any[String].asInstanceOf[MovementType],
                          MovementId(eqTo(movementId.value)),
                          MessageId(eqTo(messageId.value)),
                          any[MessageUpdate]
                        )(
                          any(),
                          any()
                        )
                      ).thenReturn(EitherT.rightT(()))

                      when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any(), any()))
                        .thenReturn(EitherT.rightT(MessageType.DeclarationAmendment))

                      when(
                        mockValidationService.validateLargeMessage(
                          eqTo(MessageType.DeclarationAmendment),
                          any[String].asInstanceOf[ObjectStoreResourceLocation]
                        )(
                          any[HeaderCarrier],
                          any[ExecutionContext]
                        )
                      )
                        .thenAnswer {
                          _ => EitherT.rightT(())
                        }

                      when(
                        mockRouterService.sendLargeMessage(
                          any[String].asInstanceOf[MessageType],
                          EORINumber(eqTo(eoriNumber.value)),
                          MovementId(eqTo(movementId.value)),
                          MessageId(eqTo(messageId.value)),
                          any[String].asInstanceOf[ObjectStoreURI]
                        )(any[ExecutionContext], any[HeaderCarrier])
                      ).thenAnswer(
                        _ => EitherT.rightT(())
                      )

                      when(
                        mockAuditService.audit(
                          eqTo(AuditType.DeclarationData),
                          any[String].asInstanceOf[ObjectStoreResourceLocation]
                        )(
                          any(),
                          any()
                        )
                      ).thenReturn(Future.failed((new Throwable("test"))))

                      val request = FakeRequest(
                        POST,
                        routes.V2MovementsController.attachLargeMessage(eoriNumber, movementType, movementId, messageId).url,
                        headers = FakeHeaders(),
                        jsonSuccessUpscanResponse
                      )
                      val result = sut.attachLargeMessage(eoriNumber, movementType, movementId, messageId)(request)

                      status(result) mustBe OK

                      verify(mockObjectStoreService, times(1)).addMessage(
                        DownloadUrl(eqTo(upscanUrl)),
                        MovementId(eqTo(movementId.value)),
                        MessageId(eqTo(messageId.value))
                      )(any(), any())

                      verify(mockObjectStoreService, times(1)).getMessage(ObjectStoreResourceLocation(any()))(any(), any())

                      verify(mockXmlParsingService, times(1)).extractMessageType(any(), any())(any(), any())

                      verify(mockValidationService, times(1)).validateLargeMessage(any(), any())(any(), any())

                      verify(mockPersistenceService, times(1)).updateMessage(
                        EORINumber(eqTo(eoriNumber.value)),
                        any[MovementType],
                        MovementId(eqTo(movementId.value)),
                        MessageId(eqTo(messageId.value)),
                        any()
                      )(
                        any(),
                        any()
                      )
                      verify(mockRouterService, times(1))
                        .sendLargeMessage(
                          any[MessageType],
                          EORINumber(eqTo(eoriNumber.value)),
                          MovementId(eqTo(movementId.value)),
                          MessageId(eqTo(messageId.value)),
                          ObjectStoreURI(any())
                        )(any(), any())

                      verify(mockAuditService, times(1))
                        .audit(
                          eqTo(AuditType.DeclarationAmendment),
                          ObjectStoreResourceLocation(any())
                        )(any(), any())
                  }
                }

                "and sending the message to router fails" in forAll(
                  arbitraryEORINumber.arbitrary,
                  arbitraryMovementType.arbitrary,
                  arbitraryMovementId.arbitrary,
                  arbitraryMessageId.arbitrary,
                  arbitraryObjectSummaryWithMd5.arbitrary
                ) {
                  (eoriNumber, movementType, movementId, messageId, objectSummary) =>
                    beforeEach()
                    when(
                      mockObjectStoreService.addMessage(
                        any[String].asInstanceOf[DownloadUrl],
                        MovementId(eqTo(movementId.value)),
                        MessageId(eqTo(messageId.value))
                      )(
                        any(),
                        any()
                      )
                    ).thenReturn(EitherT.rightT(objectSummary))

                    when(
                      mockObjectStoreService.getMessage(
                        any[String].asInstanceOf[ObjectStoreResourceLocation]
                      )(
                        any(),
                        any()
                      )
                    ).thenReturn(EitherT.rightT[Future, ObjectStoreError](singleUseStringSource("a source response")))

                    when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any(), any()))
                      .thenReturn(EitherT.rightT(MessageType.DeclarationAmendment))

                    when(
                      mockPersistenceService.updateMessage(
                        EORINumber(eqTo(eoriNumber.value)),
                        any[String].asInstanceOf[MovementType],
                        MovementId(eqTo(movementId.value)),
                        MessageId(eqTo(messageId.value)),
                        any[MessageUpdate]
                      )(
                        any(),
                        any()
                      )
                    ).thenReturn(EitherT.rightT(()))

                    when(
                      mockValidationService.validateLargeMessage(eqTo(MessageType.DeclarationAmendment), any[String].asInstanceOf[ObjectStoreResourceLocation])(
                        any[HeaderCarrier],
                        any[ExecutionContext]
                      )
                    )
                      .thenAnswer {
                        _ => EitherT.rightT(())
                      }

                    when(
                      mockRouterService.sendLargeMessage(
                        any[String].asInstanceOf[MessageType],
                        EORINumber(eqTo(eoriNumber.value)),
                        MovementId(eqTo(movementId.value)),
                        MessageId(eqTo(messageId.value)),
                        any[String].asInstanceOf[ObjectStoreURI]
                      )(any[ExecutionContext], any[HeaderCarrier])
                    ).thenAnswer(
                      _ => EitherT.leftT(RouterError.UnexpectedError(None))
                    )

                    val request = FakeRequest(
                      POST,
                      routes.V2MovementsController.attachLargeMessage(eoriNumber, movementType, movementId, messageId).url,
                      headers = FakeHeaders(),
                      jsonSuccessUpscanResponse
                    )

                    val result = sut.attachLargeMessage(eoriNumber, movementType, movementId, messageId)(request)

                    status(result) mustBe OK //TODO: modify once PPNS implemented

                    verify(mockObjectStoreService, times(1)).addMessage(
                      DownloadUrl(any()),
                      MovementId(eqTo(movementId.value)),
                      MessageId(eqTo(messageId.value))
                    )(any(), any())

                    verify(mockObjectStoreService, times(1)).getMessage(ObjectStoreResourceLocation(any()))(any(), any())

                    verify(mockXmlParsingService, times(1)).extractMessageType(any(), any())(any(), any())

                    verify(mockValidationService, times(1)).validateLargeMessage(eqTo(MessageType.DeclarationAmendment), any())(any(), any())

                    verify(mockPersistenceService, times(1)).updateMessage(
                      EORINumber(any()),
                      any[MovementType],
                      MovementId(eqTo(movementId.value)),
                      MessageId(eqTo(messageId.value)),
                      any()
                    )(
                      any(),
                      any()
                    )
                    verify(mockRouterService, times(1))
                      .sendLargeMessage(
                        any[MessageType],
                        EORINumber(eqTo(eoriNumber.value)),
                        MovementId(eqTo(movementId.value)),
                        MessageId(eqTo(messageId.value)),
                        ObjectStoreURI(any())
                      )(any(), any())
                }

              }

              "and validation fails" in forAll(
                arbitraryEORINumber.arbitrary,
                arbitraryMovementType.arbitrary,
                arbitraryMovementId.arbitrary,
                arbitraryMessageId.arbitrary,
                arbitraryObjectSummaryWithMd5.arbitrary
              ) {
                (eoriNumber, movementType, movementId, messageId, objectSummary) =>
                  beforeEach()
                  when(
                    mockObjectStoreService.addMessage(
                      any[String].asInstanceOf[DownloadUrl],
                      any[String].asInstanceOf[MovementId],
                      any[String].asInstanceOf[MessageId]
                    )(
                      any(),
                      any()
                    )
                  ).thenReturn(EitherT.rightT(objectSummary))

                  when(
                    mockObjectStoreService.getMessage(
                      any[String].asInstanceOf[ObjectStoreResourceLocation]
                    )(
                      any(),
                      any()
                    )
                  ).thenReturn(EitherT.rightT[Future, ObjectStoreError](singleUseStringSource("a source response")))

                  when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any(), any()))
                    .thenReturn(EitherT.rightT(MessageType.DeclarationAmendment))

                  when(
                    mockPersistenceService.updateMessage(
                      EORINumber(eqTo(eoriNumber.value)),
                      any[String].asInstanceOf[MovementType],
                      any[String].asInstanceOf[MovementId],
                      any[String].asInstanceOf[MessageId],
                      any[MessageUpdate]
                    )(
                      any(),
                      any()
                    )
                  ).thenReturn(EitherT.rightT(()))

                  when(
                    mockValidationService.validateLargeMessage(eqTo(MessageType.DeclarationAmendment), any[String].asInstanceOf[ObjectStoreResourceLocation])(
                      any[HeaderCarrier],
                      any[ExecutionContext]
                    )
                  )
                    .thenAnswer {
                      _ => EitherT.leftT(FailedToValidateError.ParsingError("A parsing error occurred"))
                    }

                  val request = FakeRequest(
                    POST,
                    routes.V2MovementsController.attachLargeMessage(eoriNumber, movementType, movementId, messageId).url,
                    headers = FakeHeaders(),
                    jsonSuccessUpscanResponse
                  )

                  val result = sut.attachLargeMessage(eoriNumber, movementType, movementId, messageId)(request)

                  status(result) mustBe OK //TODO: modify once PPNS implemented

                  verify(mockObjectStoreService, times(1)).addMessage(
                    DownloadUrl(any()),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value))
                  )(any(), any())

                  verify(mockObjectStoreService, times(1)).getMessage(ObjectStoreResourceLocation(any()))(any(), any())

                  verify(mockXmlParsingService, times(1)).extractMessageType(any(), any())(any(), any())

                  verify(mockValidationService, times(1)).validateLargeMessage(eqTo(MessageType.DeclarationAmendment), any())(any(), any())

                  verify(mockPersistenceService, times(1)).updateMessage(
                    EORINumber(any()),
                    any[MovementType],
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    any()
                  )(
                    any(),
                    any()
                  )
                  verify(mockRouterService, times(0))
                    .sendLargeMessage(
                      any[MessageType],
                      EORINumber(eqTo(eoriNumber.value)),
                      MovementId(eqTo(movementId.value)),
                      MessageId(eqTo(messageId.value)),
                      ObjectStoreURI(any())
                    )(any(), any())
              }

            }

            "and persisting the object store url fails" in forAll(
              arbitraryEORINumber.arbitrary,
              arbitraryMovementType.arbitrary,
              arbitraryMovementId.arbitrary,
              arbitraryMessageId.arbitrary,
              arbitraryObjectSummaryWithMd5.arbitrary
            ) {
              (eoriNumber, movementType, movementId, messageId, objectSummary) =>
                beforeEach()
                when(
                  mockObjectStoreService.addMessage(
                    any[String].asInstanceOf[DownloadUrl],
                    any[String].asInstanceOf[MovementId],
                    any[String].asInstanceOf[MessageId]
                  )(
                    any(),
                    any()
                  )
                ).thenReturn(EitherT.rightT(objectSummary))

                when(
                  mockObjectStoreService.getMessage(
                    any[String].asInstanceOf[ObjectStoreResourceLocation]
                  )(
                    any(),
                    any()
                  )
                ).thenReturn(EitherT.rightT[Future, ObjectStoreError](singleUseStringSource("a source response")))

                when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any(), any()))
                  .thenReturn(EitherT.rightT(MessageType.DeclarationAmendment))

                when(
                  mockValidationService.validateLargeMessage(eqTo(MessageType.DeclarationAmendment), any[String].asInstanceOf[ObjectStoreResourceLocation])(
                    any[HeaderCarrier],
                    any[ExecutionContext]
                  )
                )
                  .thenAnswer {
                    _ => EitherT.leftT(FailedToValidateError.ParsingError("A parsing error occurred"))
                  }

                when(
                  mockPersistenceService.updateMessage(
                    EORINumber(eqTo(eoriNumber.value)),
                    any[String].asInstanceOf[MovementType],
                    any[String].asInstanceOf[MovementId],
                    any[String].asInstanceOf[MessageId],
                    any[MessageUpdate]
                  )(
                    any(),
                    any()
                  )
                ).thenReturn(EitherT.leftT(PersistenceError.MessageNotFound(movementId, messageId)))

                val request = FakeRequest(
                  POST,
                  routes.V2MovementsController.attachLargeMessage(eoriNumber, movementType, movementId, messageId).url,
                  headers = FakeHeaders(),
                  jsonSuccessUpscanResponse
                )

                val result = sut.attachLargeMessage(eoriNumber, movementType, movementId, messageId)(request)

                status(result) mustBe OK //TODO: modify once PPNS implemented

                verify(mockObjectStoreService, times(1)).addMessage(
                  DownloadUrl(any()),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value))
                )(any(), any())

                verify(mockObjectStoreService, times(1)).getMessage(ObjectStoreResourceLocation(any()))(any(), any())

                verify(mockXmlParsingService, times(1)).extractMessageType(any(), any())(any(), any())

                verify(mockValidationService, times(1)).validateLargeMessage(eqTo(MessageType.DeclarationAmendment), any())(any(), any())

                verify(mockPersistenceService, times(1)).updateMessage(
                  EORINumber(any()),
                  any[MovementType],
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  any()
                )(
                  any(),
                  any()
                )

                verify(mockRouterService, times(0))
                  .sendLargeMessage(
                    any[MessageType],
                    EORINumber(eqTo(eoriNumber.value)),
                    MovementId(eqTo(movementId.value)),
                    MessageId(eqTo(messageId.value)),
                    ObjectStoreURI(any())
                  )(any(), any())
            }

          }

          "and extracting the message type fails" in forAll(
            arbitraryEORINumber.arbitrary,
            arbitraryMovementType.arbitrary,
            arbitraryMovementId.arbitrary,
            arbitraryMessageId.arbitrary,
            arbitraryObjectSummaryWithMd5.arbitrary
          ) {
            (eoriNumber, movementType, movementId, messageId, objectSummary) =>
              beforeEach()
              when(
                mockObjectStoreService.addMessage(
                  any[String].asInstanceOf[DownloadUrl],
                  any[String].asInstanceOf[MovementId],
                  any[String].asInstanceOf[MessageId]
                )(
                  any(),
                  any()
                )
              ).thenReturn(EitherT.rightT(objectSummary))

              when(
                mockObjectStoreService.getMessage(
                  any[String].asInstanceOf[ObjectStoreResourceLocation]
                )(
                  any(),
                  any()
                )
              ).thenReturn(EitherT.rightT[Future, ObjectStoreError](singleUseStringSource("a source response")))

              when(mockXmlParsingService.extractMessageType(any[Source[ByteString, _]], any[Seq[MessageType]])(any(), any()))
                .thenReturn(EitherT.leftT(ExtractionError.MalformedInput))

              val request = FakeRequest(
                POST,
                routes.V2MovementsController.attachLargeMessage(eoriNumber, movementType, movementId, messageId).url,
                headers = FakeHeaders(),
                jsonSuccessUpscanResponse
              )

              val result = sut.attachLargeMessage(eoriNumber, movementType, movementId, messageId)(request)

              status(result) mustBe OK //TODO: modify once PPNS implemented

              verify(mockObjectStoreService, times(1)).addMessage(
                DownloadUrl(any()),
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value))
              )(any(), any())

              verify(mockObjectStoreService, times(1)).getMessage(ObjectStoreResourceLocation(any()))(any(), any())

              verify(mockXmlParsingService, times(1)).extractMessageType(any(), any())(any(), any())

              verify(mockValidationService, times(0)).validateLargeMessage(eqTo(MessageType.DeclarationAmendment), any())(any(), any())

              verify(mockPersistenceService, times(0)).updateMessage(
                EORINumber(any()),
                any[MovementType],
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value)),
                any()
              )(
                any(),
                any()
              )

              verify(mockRouterService, times(0))
                .sendLargeMessage(
                  any[MessageType],
                  EORINumber(eqTo(eoriNumber.value)),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  ObjectStoreURI(any())
                )(any(), any())
          }

          "and and retrieving the source from the object store fails" in forAll(
            arbitraryEORINumber.arbitrary,
            arbitraryMovementType.arbitrary,
            arbitraryMovementId.arbitrary,
            arbitraryMessageId.arbitrary,
            arbitraryObjectSummaryWithMd5.arbitrary
          ) {
            (eoriNumber, movementType, movementId, messageId, objectSummary) =>
              beforeEach()

              when(
                mockObjectStoreService.addMessage(
                  any[String].asInstanceOf[DownloadUrl],
                  any[String].asInstanceOf[MovementId],
                  any[String].asInstanceOf[MessageId]
                )(
                  any(),
                  any()
                )
              ).thenReturn(EitherT.rightT(objectSummary))

              when(
                mockObjectStoreService.getMessage(
                  any[String].asInstanceOf[ObjectStoreResourceLocation]
                )(
                  any(),
                  any()
                )
              ).thenReturn(EitherT.leftT(ObjectStoreError.UnexpectedError(None)))

              val request = FakeRequest(
                POST,
                routes.V2MovementsController.attachLargeMessage(eoriNumber, movementType, movementId, messageId).url,
                headers = FakeHeaders(),
                jsonSuccessUpscanResponse
              )

              val result = sut.attachLargeMessage(eoriNumber, movementType, movementId, messageId)(request)

              status(result) mustBe OK //TODO: modify once PPNS implemented

              verify(mockObjectStoreService, times(1)).addMessage(
                DownloadUrl(any()),
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value))
              )(any(), any())

              verify(mockObjectStoreService, times(1)).getMessage(ObjectStoreResourceLocation(any()))(any(), any())

              verify(mockXmlParsingService, times(0)).extractMessageType(any(), any())(any(), any())

              verify(mockValidationService, times(0)).validateLargeMessage(eqTo(MessageType.DeclarationAmendment), any())(any(), any())

              verify(mockPersistenceService, times(0)).updateMessage(
                EORINumber(any()),
                any[MovementType],
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value)),
                any()
              )(
                any(),
                any()
              )

              verify(mockRouterService, times(0))
                .sendLargeMessage(
                  any[MessageType],
                  EORINumber(eqTo(eoriNumber.value)),
                  MovementId(eqTo(movementId.value)),
                  MessageId(eqTo(messageId.value)),
                  ObjectStoreURI(any())
                )(any(), any())
          }
        }

        "and uploading to object-store fails" in forAll(
          arbitraryEORINumber.arbitrary,
          arbitraryMovementType.arbitrary,
          arbitraryMovementId.arbitrary,
          arbitraryMessageId.arbitrary
        ) {
          (eoriNumber, movementType, movementId, messageId) =>
            beforeEach()
            when(
              mockObjectStoreService.addMessage(
                any[String].asInstanceOf[DownloadUrl],
                any[String].asInstanceOf[MovementId],
                any[String].asInstanceOf[MessageId]
              )(
                any(),
                any()
              )
            ).thenReturn(EitherT.leftT(ObjectStoreError.UnexpectedError(None)))

            val request = FakeRequest(
              POST,
              routes.V2MovementsController.attachLargeMessage(eoriNumber, movementType, movementId, messageId).url,
              headers = FakeHeaders(),
              jsonSuccessUpscanResponse
            )

            val result = sut.attachLargeMessage(eoriNumber, movementType, movementId, messageId)(request)

            status(result) mustBe OK //TODO: modify once PPNS implemented

            verify(mockObjectStoreService, times(1)).addMessage(
              DownloadUrl(any()),
              MovementId(eqTo(movementId.value)),
              MessageId(eqTo(messageId.value))
            )(any(), any())

            verify(mockObjectStoreService, times(0)).getMessage(ObjectStoreResourceLocation(any()))(any(), any())

            verify(mockXmlParsingService, times(0)).extractMessageType(any(), any())(any(), any())

            verify(mockValidationService, times(0)).validateXml(any(), any())(any(), any())

            verify(mockPersistenceService, times(0)).updateMessage(
              EORINumber(any()),
              any[MovementType],
              MovementId(eqTo(movementId.value)),
              MessageId(eqTo(messageId.value)),
              any()
            )(
              any(),
              any()
            )

            verify(mockRouterService, times(0))
              .sendLargeMessage(
                any[MessageType],
                EORINumber(eqTo(eoriNumber.value)),
                MovementId(eqTo(movementId.value)),
                MessageId(eqTo(messageId.value)),
                ObjectStoreURI(any())
              )(any(), any())
        }
      }
    }

    "should return Bad Request if it cannot parse the upscan response" in forAll(
      arbitraryEORINumber.arbitrary,
      arbitraryMovementType.arbitrary,
      arbitraryMovementId.arbitrary,
      arbitraryMessageId.arbitrary
    ) {
      (eoriNumber, movementType, movementId, messageId) =>
        val request = FakeRequest(
          POST,
          routes.V2MovementsController.attachLargeMessage(eoriNumber, movementType, movementId, messageId).url,
          headers = FakeHeaders(),
          jsonInvalidUpscanResponse
        )

        val result = sut.attachLargeMessage(eoriNumber, movementType, movementId, messageId)(request)

        status(result) mustBe BAD_REQUEST
    }

  }

  "should return Ok when failure response from upscan" in forAll(
    arbitraryEORINumber.arbitrary,
    arbitraryMovementType.arbitrary,
    arbitraryMovementId.arbitrary,
    arbitraryMessageId.arbitrary
  ) {
    (eoriNumber, movementType, movementId, messageId) =>
      beforeEach()

      val request = FakeRequest(
        POST,
        v2.controllers.routes.V2MovementsController.attachLargeMessage(eoriNumber, movementType, movementId, messageId).url,
        headers = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)),
        jsonFailedUpscanResponse
      )

      val result = sut.attachLargeMessage(eoriNumber, movementType, movementId, messageId)(request)

      status(result) mustBe OK

      verify(mockAuditService, times(1)).audit(eqTo(AuditType.TraderFailedUploadEvent), any(), eqTo(MimeTypes.JSON))(any(), any())
  }

}
