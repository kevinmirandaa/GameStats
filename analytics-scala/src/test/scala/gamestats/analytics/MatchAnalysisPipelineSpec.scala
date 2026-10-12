package gamestats.analytics

import gamestats.analytics.analysis.{GameAnalyzerRegistry, MatchAnalysisPipeline}
import gamestats.analytics.decoding.BatchDecoder
import gamestats.analytics.domain.*
import gamestats.analytics.TestSupport.*

class MatchAnalysisPipelineSpec extends munit.FunSuite:

  private val noAnalyzers = GameAnalyzerRegistry.withoutGameAnalyzers
  private val toyRegistry = GameAnalyzerRegistry(List(ToyAnalyzer))

  private def countsOf(value: ujson.Value): Map[String, Int] =
    value.obj.map((name, count) => name -> count.num.toInt).toMap

  exampleMatches.foreach { (game, name) =>
    test(s"generic analysis of $game $name matches the expected result") {
      val batch = BatchDecoder.decode(readBatchJson(game, name)).toOption.get
      val expected = readExpected(game, name)
      val analysis = MatchAnalysisPipeline.analyze(batch, noAnalyzers)

      assertEquals(analysis.matchId, expected("matchId").str)
      assertEquals(analysis.status.wireName, expected("status").str)
      assertEquals(analysis.totalEvents, expected("totalEvents").num.toInt)
      assertEqualsDouble(analysis.durationSeconds, expected("durationSeconds").num, 0.001)
      assertEquals(analysis.eventCountsByType, countsOf(expected("eventCountsByType")))
      assertEquals(analysis.eventCountsByAction, countsOf(expected("eventCountsByAction")))
      assertEquals(
        analysis.players.map(player => player.playerId -> player.eventCount),
        expected("players").arr.map(player => player("playerId").str -> player("eventCount").num.toInt).toList
      )
      assertEquals(analysis.matchMetrics, Map.empty[String, Double])
      assertEquals(analysis.patterns, Nil)
    }
  }

  test("the winner is read from the finished event") {
    val batch = BatchDecoder.decode(readBatchJson("racing", "patterns-triggered")).toOption.get
    assertEquals(MatchAnalysisPipeline.analyze(batch, noAnalyzers).winnerPlayerId, Some("player-04"))
  }

  test("events are analyzed in timestamp order even when delivered shuffled") {
    val batch = BatchDecoder.decode(readBatchJson("combat", "patterns-triggered")).toOption.get
    val shuffled = batch.copy(events = batch.events.reverse)
    assertEquals(MatchAnalysisPipeline.analyze(shuffled, noAnalyzers), MatchAnalysisPipeline.analyze(batch, noAnalyzers))
  }

  test("an unknown game produces a generic analysis without failing") {
    val batch = toyBatch(List(eventAt(0, "e1", Some("p1"), Some("SCORE"))))
    val analysis = MatchAnalysisPipeline.analyze(batch, noAnalyzers)
    assertEquals(analysis.totalEvents, 1)
    assertEquals(analysis.players.map(_.metrics), List(Map.empty[String, Double]))
  }

  test("the analyzer of a game contributes metrics and patterns") {
    val batch = toyBatch(
      List(
        eventAt(0, "e1", Some("p1"), Some("SCORE"), ujson.Obj("points" -> 3)),
        eventAt(5, "e2", Some("p1"), Some("SCORE"), ujson.Obj("points" -> 2))
      )
    )
    val analysis = MatchAnalysisPipeline.analyze(batch, toyRegistry)
    assertEquals(analysis.matchMetrics, Map("totalPoints" -> 5.0))
    assertEquals(analysis.players.head.metrics, Map("points" -> 5.0))
    assertEquals(analysis.patterns.map(_.name).distinct, List("FIRST_SCORE"))
  }

  test("players mentioned only in data are included when the analyzer declares them") {
    val batch = toyBatch(List(eventAt(0, "e1", Some("p1"), Some("SCORE"), ujson.Obj("opponentId" -> "p2"))))
    val analysis = MatchAnalysisPipeline.analyze(batch, toyRegistry)
    assertEquals(analysis.players.map(player => player.playerId -> player.eventCount), List("p1" -> 1, "p2" -> 0))
  }

  test("patterns are ordered by the position of their start event and then by name") {
    val events = List(eventAt(0, "e1", Some("p1")), eventAt(1, "e2", Some("p1")))
    val unordered = new gamestats.analytics.analysis.GameAnalyzer:
      val gameId = "toy"
      def analyze(batch: MatchBatch): GameInsights =
        GameInsights(
          Map.empty,
          Map.empty,
          List(
            DetectedPattern("B", "p1", "e2", "e2", Map.empty),
            DetectedPattern("B", "p1", "e1", "e1", Map.empty),
            DetectedPattern("A", "p1", "e1", "e1", Map.empty)
          )
        )
    val analysis = MatchAnalysisPipeline.analyze(toyBatch(events), GameAnalyzerRegistry(List(unordered)))
    assertEquals(analysis.patterns.map(pattern => pattern.name -> pattern.startEventId), List("A" -> "e1", "B" -> "e1", "B" -> "e2"))
  }

  test("an incomplete match keeps its status and a match without events lasts zero seconds") {
    val analysis = MatchAnalysisPipeline.analyze(toyBatch(Nil, MatchStatus.Incomplete), noAnalyzers)
    assertEquals(analysis.status, MatchStatus.Incomplete)
    assertEquals(analysis.durationSeconds, 0.0)
    assertEquals(analysis.winnerPlayerId, None)
  }
