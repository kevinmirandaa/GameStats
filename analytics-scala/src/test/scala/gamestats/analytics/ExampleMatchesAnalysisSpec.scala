package gamestats.analytics

import gamestats.analytics.analysis.games.{CombatAnalyzer, RacingAnalyzer}
import gamestats.analytics.analysis.{GameAnalyzerRegistry, MatchAnalysisPipeline}
import gamestats.analytics.decoding.BatchDecoder
import gamestats.analytics.TestSupport.*

class ExampleMatchesAnalysisSpec extends munit.FunSuite:

  private val tolerance = 0.001
  private val registry = GameAnalyzerRegistry(List(RacingAnalyzer, CombatAnalyzer))

  private def numbersOf(value: ujson.Value): Map[String, Double] =
    value.obj.map((name, number) => name -> number.num).toMap

  private def assertSameNumbers(actual: Map[String, Double], expected: Map[String, Double], description: String): Unit =
    assertEquals(actual.keySet, expected.keySet, s"$description: metric names")
    expected.foreach { (name, expectedValue) =>
      assertEqualsDouble(actual(name), expectedValue, tolerance, s"$description: $name")
    }

  exampleMatches.foreach { (game, name) =>
    test(s"the full analysis of $game $name matches the expected result") {
      val batch = BatchDecoder.decode(readBatchJson(game, name)).toOption.get
      val expected = readExpected(game, name)
      val analysis = MatchAnalysisPipeline.analyze(batch, registry)

      assertSameNumbers(analysis.matchMetrics, numbersOf(expected("matchMetrics")), "match metrics")

      val expectedPlayers = expected("players").arr.toList
      assertEquals(analysis.players.map(_.playerId), expectedPlayers.map(_("playerId").str))
      analysis.players.zip(expectedPlayers).foreach { (actual, expectedPlayer) =>
        assertEquals(actual.eventCount, expectedPlayer("eventCount").num.toInt, actual.playerId)
        assertSameNumbers(actual.metrics, numbersOf(expectedPlayer("metrics")), actual.playerId)
      }

      val expectedPatterns = expected("patterns").arr.toList
      assertEquals(analysis.patterns.size, expectedPatterns.size, "pattern count")
      analysis.patterns.zip(expectedPatterns).foreach { (actual, expectedPattern) =>
        assertEquals(actual.name, expectedPattern("name").str)
        assertEquals(actual.playerId, expectedPattern("playerId").str)
        assertEquals(actual.startEventId, expectedPattern("startEventId").str)
        assertEquals(actual.endEventId, expectedPattern("endEventId").str)
        assertSameNumbers(actual.details, numbersOf(expectedPattern("details")), s"${actual.name} details")
      }
    }
  }
