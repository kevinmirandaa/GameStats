package gamestats.analytics

import java.nio.file.{Files, Paths}
import java.time.Instant

import gamestats.analytics.analysis.GameAnalyzer
import gamestats.analytics.domain.*

object TestSupport:

  val exampleMatches: List[(String, String)] = List(
    "racing" -> "patterns-triggered",
    "racing" -> "patterns-not-triggered",
    "combat" -> "patterns-triggered",
    "combat" -> "patterns-not-triggered"
  )

  def readBatchJson(game: String, name: String): String =
    Files.readString(Paths.get(s"../contracts/examples/games/$game/$name.batch.json"))

  def readExpected(game: String, name: String): ujson.Value =
    ujson.read(Files.readString(Paths.get(s"../contracts/examples/games/$game/$name.expected.json")))

  def eventAt(secondOffset: Int, eventId: String, playerId: Option[String], action: Option[String] = None, data: ujson.Obj = ujson.Obj()): Event =
    Event(
      eventId = eventId,
      timestamp = Instant.parse("2026-10-12T18:00:00Z").plusSeconds(secondOffset),
      gameId = "toy",
      gameVersion = "1.0",
      matchId = "toy-match",
      playerId = playerId,
      eventType = if action.isDefined then EventType.GameAction else EventType.PlayerJoined,
      action = action,
      data = EventData(data)
    )

  def toyBatch(events: List[Event], status: MatchStatus = MatchStatus.Completed): MatchBatch =
    MatchBatch("toy", "1.0", "toy-match", status, events)

  object ToyAnalyzer extends GameAnalyzer:
    val gameId = "toy"

    def analyze(batch: MatchBatch): GameInsights =
      val pointsByPlayer = batch.events
        .filter(_.isAction("SCORE"))
        .flatMap(event => event.playerId.map(_ -> event.data.number("points").getOrElse(0.0)))
        .groupMapReduce(_._1)(_._2)(_ + _)
      val patterns = batch.events.filter(_.isAction("SCORE")).headOption.toList.flatMap { first =>
        first.playerId.map(player => DetectedPattern("FIRST_SCORE", player, first.eventId, first.eventId, Map("points" -> 1.0)))
      }
      GameInsights(
        matchMetrics = Map("totalPoints" -> pointsByPlayer.values.sum),
        playerMetrics = pointsByPlayer.map((player, points) => player -> Map("points" -> points)),
        patterns = patterns
      )

    override def additionalPlayerIds(events: List[Event]): Set[String] =
      events.flatMap(_.data.text("opponentId")).toSet

  def analysisOf(
      matchId: String,
      gameId: String,
      winner: Option[String],
      players: List[PlayerAnalysis],
      matchMetrics: Map[String, Double] = Map.empty,
      patterns: List[DetectedPattern] = Nil,
      status: MatchStatus = MatchStatus.Completed
  ): MatchAnalysis =
    MatchAnalysis(matchId, gameId, status, winner, 10.0, 5, Map.empty, Map.empty, matchMetrics, players, patterns)
