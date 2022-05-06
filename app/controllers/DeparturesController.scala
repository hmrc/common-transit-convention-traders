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

package controllers

import akka.stream.Materializer
import akka.stream.SourceShape
import akka.stream.UniformFanInShape
import akka.stream.scaladsl.Concat
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.ByteString
import audit.AuditService
import audit.AuditType
import com.fasterxml.jackson.core.io.JsonStringEncoder
import com.kenshoo.play.metrics.Metrics
import connectors.DeparturesConnector
import controllers.actions._
import controllers.stream.StreamingParsers
import controllers.stream.VersionedRouting
import metrics.HasActionMetrics
import metrics.MetricsKeys
import models.MessageType
import models.domain.DepartureId
import models.formats.HttpFormats
import models.response.HateoasDeparturePostResponseMessage
import models.response.HateoasResponseDeparture
import models.response.HateoasResponseDepartures
import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.ControllerComponents
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import utils.CallOps._
import utils.ResponseHelper
import utils.Utils

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.xml.NodeSeq

class DeparturesController @Inject() (
  cc: ControllerComponents,
  authAction: AuthAction,
  authActionNewEnrolmentOnly: AuthNewEnrolmentOnlyAction,
  departuresConnector: DeparturesConnector,
  validateAcceptJsonHeaderAction: ValidateAcceptJsonHeaderAction,
  validateDepartureDeclarationAction: ValidateDepartureDeclarationAction,
  ensureGuaranteeAction: EnsureGuaranteeAction,
  auditService: AuditService,
  messageAnalyser: AnalyseMessageActionProvider,
  messageSizeAction: MessageSizeAction,
  val metrics: Metrics
)(implicit ec: ExecutionContext, val materializer: Materializer)
    extends BackendController(cc)
    with HasActionMetrics
    with HttpErrorFunctions
    with ResponseHelper
    with StreamingParsers
    with VersionedRouting
    with HttpFormats {

  import MetricsKeys.Endpoints._

  lazy val departuresCount = histo(GetDeparturesForEoriCount)

  def submitDeclaration(): Action[Source[ByteString, _]] = route {
    case Some("application/vnd.hmrc.2.0+json") => submitDeclarationVersionTwo()
    case _                                     => submitDeclarationVersionOne()
  }

  private def submitDeclarationVersionOne(): Action[NodeSeq] =
    withMetricsTimerAction(SubmitDepartureDeclaration) {
      (authAction andThen validateDepartureDeclarationAction andThen messageAnalyser() andThen ensureGuaranteeAction).async(parse.xml) {
        implicit request =>
          departuresConnector.post(request.newXml.toString).map {
            case Right(response) =>
              response.header(LOCATION) match {
                case Some(locationValue) =>
                  if (request.guaranteeAdded) {
                    auditService.auditEvent(AuditType.TenThousandEuroGuaranteeAdded, request.newXml)
                  }
                  MessageType.getMessageType(request.body) match {
                    case Some(messageType: MessageType) =>
                      val departureId = DepartureId(Utils.lastFragment(locationValue).toInt)
                      Accepted(
                        Json.toJson(
                          HateoasDeparturePostResponseMessage(
                            departureId,
                            messageType.code,
                            request.body,
                            response.responseData
                          )
                        )
                      ).withHeaders(LOCATION -> routes.DeparturesController.getDeparture(departureId).urlWithContext)
                    case None =>
                      InternalServerError
                  }
                case _ =>
                  InternalServerError
              }
            case Left(response) => handleNon2xx(response)
          }
      }
    }

  def getDeparture(departureId: DepartureId): Action[AnyContent] =
    withMetricsTimerAction(GetDeparture) {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          departuresConnector.get(departureId).map {
            case Right(departure) =>
              Ok(Json.toJson(HateoasResponseDeparture(departure)))
            case Left(invalidResponse) =>
              handleNon2xx(invalidResponse)
          }
      }
    }

  def getDeparturesForEori(updatedSince: Option[OffsetDateTime]): Action[AnyContent] =
    withMetricsTimerAction(GetDeparturesForEori) {
      (authAction andThen validateAcceptJsonHeaderAction).async {
        implicit request =>
          departuresConnector.getForEori(updatedSince).map {
            case Right(departures) =>
              departuresCount.update(departures.departures.length)
              Ok(Json.toJson(HateoasResponseDepartures(departures)))
            case Left(invalidResponse) =>
              handleNon2xx(invalidResponse)
          }
      }
    }

  private def UTF8ByteString(string: String) = ByteString(string, StandardCharsets.UTF_8)

  private lazy val embedXmlInJson = {
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // We want to fan in 3 items, and return one item
      val concat = builder.add(Concat[ByteString](5))

      val messageTypeFlow   = builder.add(Flow.fromFunction((in: ByteString) => UTF8ByteString("{ \"messageType\": \"") ++ in ++ UTF8ByteString("\",")))
      val eoriFlow          = builder.add(Flow.fromFunction((in: ByteString) => UTF8ByteString(" \"eoriNumber\": \"") ++ in ++ UTF8ByteString("\",")))
      val messageFlowStart  = builder.add(Source.single(UTF8ByteString(" \"message\": \"")))
      val messageEscapeFlow = builder.add(Flow.fromFunction((in: ByteString) => ByteString.fromArray(JsonStringEncoder.getInstance().quoteAsUTF8(in.decodeString(StandardCharsets.UTF_8)))))
      val endJson           = builder.add(Source.single(UTF8ByteString("\" }")))

      messageTypeFlow   ~> concat.in(0)
      eoriFlow          ~> concat.in(1)
      messageFlowStart  ~> concat.in(2)
      messageEscapeFlow ~> concat.in(3)
      endJson           ~> concat.in(4)

      UniformFanInShape(concat.out, messageTypeFlow.in, eoriFlow.in, messageEscapeFlow.in)
    }
  }

  private def createSource(eori: String, messageType: String, source: Source[ByteString, _]): Source[ByteString, _] =
    Source.fromGraph(
      GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val eoriSource        = builder.add(Source.single(UTF8ByteString(eori)))
        val messageTypeSource = builder.add(Source.single(UTF8ByteString(messageType)))
        val messageSource     = builder.add(source)
        val jsonBuilder       = builder.add(embedXmlInJson)

        eoriSource        ~> jsonBuilder.in(0)
        messageTypeSource ~> jsonBuilder.in(1)
        messageSource     ~> jsonBuilder.in(2)

        SourceShape(jsonBuilder.out)
      }
    )

  private def submitDeclarationVersionTwo(): Action[Source[ByteString, _]] =
    (authActionNewEnrolmentOnly andThen messageSizeAction).async(streamFromMemory) {
      request =>
        // TODO: When streaming to the validation service, we will want to keep a copy of the
        //  stream so we can replay it. We will need to do one of two things:
        //  * Stream to a temporary file, then stream off it twice
        //  * Stream from memory to the validation service AND a file, then stream FROM the file if we get the OK
        //  The first option will likely be more stable but slower than the second option.

        val eori        = "abcde"
        val messageType = "cc015c"

        val s: Source[ByteString, _] = createSource(eori, messageType, request.body)
        s.to(Sink.foreach(x => print(x.decodeString(StandardCharsets.UTF_8)))).run()

        // Because we have an open stream, we **must** do something with it. For now, we send it to the ignore sink.
//        request.body.to(Sink.ignore).run()
        logger.info("Version 2 of endpoint has been called")
        Future.successful(Accepted)
    }

}
