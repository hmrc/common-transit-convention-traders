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
import play.api.http.Status.UNSUPPORTED_MEDIA_TYPE
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
import v2.base.TestActorSystem
import v2.base.TestSourceProvider
import v2.fakes.controllers.actions.FakeAuthNewEnrolmentOnlyAction
import v2.fakes.controllers.actions.FakeMessageSizeActionProvider
import v2.models.AuditType
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.errors.FailedToValidateError
import v2.models.errors.JsonValidationError
import v2.models.errors.PersistenceError
import v2.models.errors.RouterError
import v2.models.errors.XmlValidationError
import v2.models.errors.ConversionError
import v2.models.request.MessageType
import v2.models.responses.DeclarationResponse
import v2.services.AuditingService
import v2.services.ConversionService
import v2.services.DeparturesService
import v2.services.RouterService
import v2.services.ValidationService

import java.nio.charset.StandardCharsets
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

  // TODO: Make this a cc015c
  def CC015C: NodeSeq =
    <CC015C>
      <SynIdeMES1>UNOC</SynIdeMES1>
      <SynVerNumMES2>3</SynVerNumMES2>
      <MesRecMES6>NCTS</MesRecMES6>
      <DatOfPreMES9>20201217</DatOfPreMES9>
      <TimOfPreMES10>1340</TimOfPreMES10>
      <IntConRefMES11>17712576475433</IntConRefMES11>
      <AppRefMES14>NCTS</AppRefMES14>
      <MesIdeMES19>1</MesIdeMES19>
      <MesTypMES20>GB015B</MesTypMES20>
      <HEAHEA>
        <RefNumHEA4>GUATEST1201217134032</RefNumHEA4>
        <TypOfDecHEA24>T1</TypOfDecHEA24>
        <CouOfDesCodHEA30>IT</CouOfDesCodHEA30>
        <AutLocOfGooCodHEA41>954131533-GB60DEP</AutLocOfGooCodHEA41>
        <CouOfDisCodHEA55>GB</CouOfDisCodHEA55>
        <IdeOfMeaOfTraAtDHEA78>NC15 REG</IdeOfMeaOfTraAtDHEA78>
        <NatOfMeaOfTraAtDHEA80>GB</NatOfMeaOfTraAtDHEA80>
        <ConIndHEA96>0</ConIndHEA96>
        <NCTSAccDocHEA601LNG>EN</NCTSAccDocHEA601LNG>
        <TotNumOfIteHEA305>1</TotNumOfIteHEA305>
        <TotNumOfPacHEA306>10</TotNumOfPacHEA306>
        <TotGroMasHEA307>1000</TotGroMasHEA307>
        <DecDatHEA383>20201217</DecDatHEA383>
        <DecPlaHEA394>Dover</DecPlaHEA394>
      </HEAHEA>
      <TRAPRIPC1>
        <NamPC17>NCTS UK TEST LAB HMCE</NamPC17>
        <StrAndNumPC122>11TH FLOOR, ALEX HOUSE, VICTORIA AV</StrAndNumPC122>
        <PosCodPC123>SS99 1AA</PosCodPC123>
        <CitPC124>SOUTHEND-ON-SEA, ESSEX</CitPC124>
        <CouPC125>GB</CouPC125>
        <TINPC159>GB954131533000</TINPC159>
      </TRAPRIPC1>
      <TRACONCO1>
        <NamCO17>NCTS UK TEST LAB HMCE</NamCO17>
        <StrAndNumCO122>11TH FLOOR, ALEX HOUSE, VICTORIA AV</StrAndNumCO122>
        <PosCodCO123>SS99 1AA</PosCodCO123>
        <CitCO124>SOUTHEND-ON-SEA, ESSEX</CitCO124>
        <CouCO125>GB</CouCO125>
        <TINCO159>GB954131533000</TINCO159>
      </TRACONCO1>
      <TRACONCE1>
        <NamCE17>NCTS UK TEST LAB HMCE</NamCE17>
        <StrAndNumCE122>ITALIAN OFFICE</StrAndNumCE122>
        <PosCodCE123>IT99 1IT</PosCodCE123>
        <CitCE124>MILAN</CitCE124>
        <CouCE125>IT</CouCE125>
        <TINCE159>IT11ITALIANC11</TINCE159>
      </TRACONCE1>
      <CUSOFFDEPEPT>
        <RefNumEPT1>GB000060</RefNumEPT1>
      </CUSOFFDEPEPT>
      <CUSOFFTRARNS>
        <RefNumRNS1>FR001260</RefNumRNS1>
        <ArrTimTRACUS085>202012191340</ArrTimTRACUS085>
      </CUSOFFTRARNS>
      <CUSOFFDESEST>
        <RefNumEST1>IT018100</RefNumEST1>
      </CUSOFFDESEST>
      <CONRESERS>
        <ConResCodERS16>A3</ConResCodERS16>
        <DatLimERS69>20201225</DatLimERS69>
      </CONRESERS>
      <SEAINFSLI>
        <SeaNumSLI2>1</SeaNumSLI2>
        <SEAIDSID>
          <SeaIdeSID1>NCTS001</SeaIdeSID1>
        </SEAIDSID>
      </SEAINFSLI>
      <GUAGUA>
        <GuaTypGUA1>0</GuaTypGUA1>
        <GUAREFREF>
          <GuaRefNumGRNREF1>20GB0000010000H72</GuaRefNumGRNREF1>
          <AccCodREF6>AC01</AccCodREF6>
        </GUAREFREF>
      </GUAGUA>
      <GOOITEGDS>
        <IteNumGDS7>1</IteNumGDS7>
        <GooDesGDS23>Wheat</GooDesGDS23>
        <GooDesGDS23LNG>EN</GooDesGDS23LNG>
        <GroMasGDS46>1000</GroMasGDS46>
        <NetMasGDS48>950</NetMasGDS48>
        <SPEMENMT2>
          <AddInfMT21>20GB0000010000H72</AddInfMT21>
          <AddInfCodMT23>CAL</AddInfCodMT23>
        </SPEMENMT2>
        <PACGS2>
          <MarNumOfPacGS21>AB234</MarNumOfPacGS21>
          <KinOfPacGS23>BX</KinOfPacGS23>
          <NumOfPacGS24>10</NumOfPacGS24>
        </PACGS2>
      </GOOITEGDS>
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
    FakeMessageSizeActionProvider
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
          if (invocation.getArgument(0, classOf[String]) == "id") EitherT.rightT(DeclarationResponse(MovementId("123"), MessageId("456")))
          else EitherT.leftT(PersistenceError.UnexpectedError(None))
      }

    reset(mockRouterService)
    when(
      mockRouterService.send(
        eqTo(MessageType.DepartureDeclaration),
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
  "with accept header set to application/vnd.hmrc.2.0+json (version two)" - {
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
        verify(mockRouterService, times(1)).send(eqTo(MessageType.DepartureDeclaration), EORINumber(any()), MovementId(any()), MessageId(any()), any())(
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
              .send(eqTo(MessageType.DepartureDeclaration), EORINumber(any()), MovementId(any()), MessageId(any()), any())(any(), any())
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
          FakeMessageSizeActionProvider
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
        ).thenReturn(EitherT.fromEither[Future](Right[PersistenceError, DeclarationResponse](DeclarationResponse(MovementId("123"), MessageId("456")))))

        val sut = new V2DeparturesControllerImpl(
          Helpers.stubControllerComponents(),
          SingletonTemporaryFileCreator,
          FakeAuthNewEnrolmentOnlyAction(EORINumber("nope")),
          mockValidationService,
          mockConversionService,
          mockDeparturesPersistenceService,
          mockRouterService,
          mockAuditService,
          FakeMessageSizeActionProvider
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
            .convertJsonToXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(
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

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DepartureDeclaration), any())(any(), any())
        verify(mockConversionService).convertJsonToXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any(), any())
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
            .convertJsonToXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(
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
        verify(mockConversionService).convertJsonToXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any(), any())
      }

      "must return Bad Request after JSON to XML conversion if the XML validation service reports an error" in {
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
            .convertJsonToXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(
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

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "code"    -> "SCHEMA_VALIDATION",
          "message" -> "Request failed schema validation",
          "validationErrors" -> Seq(
            Json.obj(
              "lineNumber"   -> 1,
              "columnNumber" -> 1,
              "message"      -> "invalid XML"
            )
          )
        )

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DepartureDeclaration), any())(any(), any())
        verify(mockConversionService).convertJsonToXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any(), any())
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
            .convertJsonToXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(
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
        verify(mockConversionService).convertJsonToXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any(), any())
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
            .convertJsonToXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(
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
            eqTo(MessageType.DepartureDeclaration),
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

        verify(mockValidationService, times(1)).validateJson(eqTo(MessageType.DepartureDeclaration), any())(any(), any())
        verify(mockConversionService).convertJsonToXml(eqTo(MessageType.DepartureDeclaration), any())(any(), any(), any())
        verify(mockDeparturesPersistenceService).saveDeclaration(any[String].asInstanceOf[EORINumber], any())(any(), any())
        verify(mockRouterService).send(
          eqTo(MessageType.DepartureDeclaration),
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

      when(mockValidationService.validateXml(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
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
        SingletonTemporaryFileCreator,
        FakeAuthNewEnrolmentOnlyAction(EORINumber("nope")),
        mockValidationService,
        mockConversionService,
        mockDeparturesPersistenceService,
        mockRouterService,
        mockAuditService,
        FakeMessageSizeActionProvider
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
