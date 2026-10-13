package gamestats.analytics.http

import java.nio.charset.StandardCharsets

import scala.util.Using

import gamestats.analytics.encoding.AnalysisEncoder
import gamestats.analytics.service.AnalyticsService

private object DashboardPage:
  private val resourcePath = "/dashboard/index.html"

  lazy val html: String =
    Using.resource(getClass.getResourceAsStream(resourcePath))(stream => new String(stream.readAllBytes(), StandardCharsets.UTF_8))

class AnalyticsRoutes(service: AnalyticsService)(using castor.Context, cask.Logger) extends cask.Routes:

  @cask.get("/")
  def dashboard(): cask.Response[String] =
    cask.Response(DashboardPage.html, 200, Seq("Content-Type" -> "text/html; charset=utf-8"))

  @cask.post("/analyze")
  def analyze(request: cask.Request): cask.Response[String] =
    service.analyzeBatch(request.text()) match
      case Right(analysis) => json(200, AnalysisEncoder.matchAnalysis(analysis))
      case Left(reason)    => json(400, AnalysisEncoder.error(reason))

  @cask.get("/stats/matches")
  def matches(gameId: Option[String] = None): cask.Response[String] =
    json(200, ujson.Arr.from(service.matchSummaries(gameId).map(AnalysisEncoder.matchSummary)))

  @cask.get("/stats/matches/:matchId")
  def matchById(matchId: String): cask.Response[String] =
    found(service.matchAnalysis(matchId).map(AnalysisEncoder.matchAnalysis), s"match $matchId was not analyzed")

  @cask.get("/stats/players/:playerId")
  def player(playerId: String): cask.Response[String] =
    found(service.playerAggregate(playerId).map(AnalysisEncoder.playerAggregate), s"player $playerId has no analyzed matches")

  @cask.get("/stats/games/:gameId")
  def game(gameId: String): cask.Response[String] =
    found(service.gameAggregate(gameId).map(AnalysisEncoder.gameAggregate), s"game $gameId has no analyzed matches")

  @cask.get("/patterns")
  def patterns(gameId: Option[String] = None, `type`: Option[String] = None): cask.Response[String] =
    json(200, ujson.Arr.from(service.patterns(gameId, `type`).map(AnalysisEncoder.patternOccurrence)))

  @cask.get("/health")
  def health(): cask.Response[String] = json(200, ujson.Obj("status" -> "UP"))

  private def found(body: Option[ujson.Value], missingMessage: String): cask.Response[String] =
    body match
      case Some(value) => json(200, value)
      case None        => json(404, AnalysisEncoder.error(missingMessage))

  private def json(statusCode: Int, body: ujson.Value): cask.Response[String] =
    cask.Response(ujson.write(body), statusCode, Seq("Content-Type" -> "application/json"))

  initialize()
