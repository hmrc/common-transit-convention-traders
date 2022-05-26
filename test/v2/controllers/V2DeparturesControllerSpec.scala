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
import cats.syntax.all._
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.HeaderNames
import play.api.http.Status.ACCEPTED
import play.api.http.Status.BAD_REQUEST
import play.api.http.Status.REQUEST_ENTITY_TOO_LARGE
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Request
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers.contentAsJson
import play.api.test.Helpers.route
import play.api.test.Helpers.status
import uk.gov.hmrc.http.HeaderCarrier
import v2.controllers.actions.AuthNewEnrolmentOnlyAction
import v2.fakes.controllers.actions.FakeAuthNewEnrolmentOnlyAction
import v2.models.EORINumber
import v2.models.MessageId
import v2.models.MovementId
import v2.models.errors.FailedToValidateError
import v2.models.errors.PersistenceError
import v2.models.errors.ValidationError
import v2.models.request.MessageType
import v2.models.responses.DeclarationResponse
import v2.services.DeparturesPersistenceService
import v2.services.ValidationService

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Try
import scala.xml.NodeSeq
import scala.xml.XML

class V2DeparturesControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with ScalaFutures with MockitoSugar {

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

  val testSink: Sink[ByteString, Future[Either[FailedToValidateError, Unit]]] =
    Flow
      .fromFunction {
        input: ByteString =>
          Try(XML.loadString(input.decodeString(StandardCharsets.UTF_8))).toEither
            .leftMap(
              _ => FailedToValidateError.SchemaFailedToValidateError(NonEmptyList(ValidationError(42, 27, "invalid XML"), Nil))
            )
            .flatMap {
              element =>
                if (element.label.equalsIgnoreCase("CC015C")) Right(())
                else Left(FailedToValidateError.SchemaFailedToValidateError(validationErrors = NonEmptyList(ValidationError(1, 1, "an error"), Nil)))
            }
      }
      .toMat(Sink.last)(Keep.right)

  val mockValidationService: ValidationService = mock[ValidationService]
  when(mockValidationService.validateXML(eqTo(MessageType.DepartureDeclaration), any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext]))
    .thenAnswer {
      invocation =>
        EitherT(
          invocation
            .getArgument[Source[ByteString, _]](1)
            .fold(ByteString())(
              (current, next) => current ++ next
            )
            .runWith(testSink)(app.materializer)
        )
    }

  val mockDeparturesPersistenceService = mock[DeparturesPersistenceService]
  when(
    mockDeparturesPersistenceService
      .saveDeclaration(eqTo("id").asInstanceOf[EORINumber], any[Source[ByteString, _]]())(any[HeaderCarrier], any[ExecutionContext])
  )
    .thenReturn(EitherT.fromEither[Future](Right[PersistenceError, DeclarationResponse](DeclarationResponse(MovementId("123"), MessageId("456")))))

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(
      bind[AuthNewEnrolmentOnlyAction].to[FakeAuthNewEnrolmentOnlyAction],
      bind[ValidationService].toInstance(mockValidationService),
      bind[DeparturesPersistenceService].toInstance(mockDeparturesPersistenceService)
    )
    .build()
  lazy val sut: V2DeparturesController = app.injector.instanceOf[V2DeparturesController]

  implicit val timeout: Timeout = 5.seconds

  def fakeRequestDepartures[A](
    method: String,
    headers: FakeHeaders = FakeHeaders(Seq(HeaderNames.CONTENT_TYPE -> "application/xml")),
    uri: String = routing.routes.DeparturesRouter.submitDeclaration().url,
    body: A
  ): Request[A] =
    FakeRequest(method = method, uri = uri, headers = headers, body = body)

  // Version 2
  "with accept header set to application/vnd.hmrc.2.0+json (version two)" - {

    // For the content length headers, we have to ensure that we send something
    val standardHeaders = FakeHeaders(
      Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "application/xml", HeaderNames.CONTENT_LENGTH -> "1000")
    )

    "must return BadRequest when content length is not sent" in {
      val departureHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "application/xml")
      )
      // We emulate no ContentType by sending in a stream directly, without going through Play's request builder
      val request =
        fakeRequestDepartures(method = "POST", body = Source.single(ByteString(CC015C.mkString, StandardCharsets.UTF_8)), headers = departureHeaders)
      val result = app.injector.instanceOf[V2DeparturesController].submitDeclaration()(request)
      status(result) mustBe BAD_REQUEST
    }

    "must return RequestEntityTooLarge when body size exceeds limit" in {
      val departureHeaders = FakeHeaders(
        Seq(HeaderNames.ACCEPT -> "application/vnd.hmrc.2.0+json", HeaderNames.CONTENT_TYPE -> "application/xml", HeaderNames.CONTENT_LENGTH -> "500001")
      )
      val request = fakeRequestDepartures(method = "POST", body = CC015C, headers = departureHeaders)
      val result  = route(app, request).value
      status(result) mustBe REQUEST_ENTITY_TOO_LARGE
    }

    "must return Accepted when body length is within limits and is considered valid" in {
      val request = fakeRequestDepartures(method = "POST", body = CC015C, headers = standardHeaders)
      val result  = route(app, request).value
      status(result) mustBe ACCEPTED
    }

    "must return Bad Request when body is not an XML document" in {
      val request = fakeRequestDepartures(method = "POST", body = "notxml", headers = standardHeaders)
      val result  = route(app, request).value
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
      val request = fakeRequestDepartures(method = "POST", body = <test></test>, headers = standardHeaders)
      val result  = route(app, request).value
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

  }

}
