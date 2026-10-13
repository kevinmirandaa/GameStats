package gamestats.analytics

import gamestats.analytics.analysis.games.{CombatAnalyzer, RacingAnalyzer}
import gamestats.analytics.analysis.{GameAnalyzerRegistry, MatchAnalysisPipeline}
import gamestats.analytics.decoding.{AnalysisDecoder, BatchDecoder}
import gamestats.analytics.encoding.AnalysisEncoder
import gamestats.analytics.TestSupport.*

class AnalysisDecoderSpec extends munit.FunSuite:

  private val registry = GameAnalyzerRegistry(List(RacingAnalyzer, CombatAnalyzer))

  private def encoded(game: String, name: String): String =
    val batch = BatchDecoder.decode(readBatchJson(game, name)).toOption.get
    ujson.write(AnalysisEncoder.matchAnalysis(MatchAnalysisPipeline.analyze(batch, registry)))

  exampleMatches.foreach { (game, name) =>
    test(s"an encoded analysis of $game $name decodes and encodes back to the same document") {
      val original = encoded(game, name)
      val decoded = AnalysisDecoder.decode(original)
      assert(decoded.isRight, clue = decoded)
      assertEquals(ujson.read(ujson.write(AnalysisEncoder.matchAnalysis(decoded.toOption.get))), ujson.read(original))
    }
  }

  test("the expected result files of the examples can be decoded") {
    exampleMatches.foreach { (game, name) =>
      val expected = ujson.write(readExpected(game, name))
      assert(AnalysisDecoder.decode(expected).isRight, clue = s"$game $name")
    }
  }

  test("the winner and the patterns survive the round trip") {
    val decoded = AnalysisDecoder.decode(encoded("racing", "patterns-triggered")).toOption.get
    assertEquals(decoded.winnerPlayerId, Some("player-04"))
    assertEquals(decoded.patterns.map(_.name).toSet, Set("RACE_COMEBACK", "IMPROVING_LAP_STREAK"))
  }

  test("a document without winner decodes with no winner") {
    val withoutWinner = ujson.read(encoded("racing", "patterns-not-triggered"))
    withoutWinner.obj.remove("winnerPlayerId")
    assertEquals(AnalysisDecoder.decode(ujson.write(withoutWinner)).toOption.get.winnerPlayerId, None)
  }

  test("invalid documents are rejected with a reason") {
    val valid = ujson.read(encoded("combat", "patterns-triggered"))
    def without(field: String): String = { val copy = ujson.read(ujson.write(valid)); copy.obj.remove(field); ujson.write(copy) }
    def changed(field: String, value: ujson.Value): String = { val copy = ujson.read(ujson.write(valid)); copy.obj(field) = value; ujson.write(copy) }
    val invalidDocuments = Map(
      "not json" -> "nope",
      "not an object" -> "[]",
      "missing matchId" -> without("matchId"),
      "missing players" -> without("players"),
      "unknown status" -> changed("status", "DONE"),
      "metrics with text" -> changed("matchMetrics", ujson.Obj("fastest" -> "slow")),
      "players not an array" -> changed("players", ujson.Obj()),
      "duration as text" -> changed("durationSeconds", "long")
    )
    invalidDocuments.foreach { (description, document) =>
      assert(AnalysisDecoder.decode(document).isLeft, clue = description)
    }
  }
