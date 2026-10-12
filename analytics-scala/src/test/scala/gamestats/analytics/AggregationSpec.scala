package gamestats.analytics

import gamestats.analytics.aggregation.{GameAggregation, PlayerAggregation}
import gamestats.analytics.domain.*
import gamestats.analytics.TestSupport.analysisOf

class AggregationSpec extends munit.FunSuite:

  private def player(id: String, metrics: (String, Double)*): PlayerAnalysis = PlayerAnalysis(id, 3, metrics.toMap)

  private val firstMatch = analysisOf(
    "m1", "toy", Some("p1"),
    List(player("p1", "score" -> 10.0, "bestLap" -> 90.0), player("p2", "score" -> 4.0)),
    matchMetrics = Map("totalScore" -> 14.0),
    patterns = List(DetectedPattern("STREAK", "p1", "a", "b", Map.empty))
  )
  private val secondMatch = analysisOf(
    "m2", "toy", Some("p2"),
    List(player("p1", "score" -> 20.0), player("p2", "score" -> 6.0)),
    matchMetrics = Map("totalScore" -> 26.0),
    patterns = List(DetectedPattern("STREAK", "p1", "c", "d", Map.empty), DetectedPattern("COMEBACK", "p2", "e", "f", Map.empty)),
    status = MatchStatus.Incomplete
  )
  private val otherGame = analysisOf("m3", "other", Some("p1"), List(player("p1", "score" -> 99.0)))

  test("player aggregate computes matches, wins, mean, min and max") {
    val aggregate = PlayerAggregation.aggregate("p1", List(firstMatch, secondMatch)).get
    assertEquals(aggregate.matchIds, List("m1", "m2"))
    assertEquals(aggregate.aggregates("matchesPlayed"), 2.0)
    assertEquals(aggregate.aggregates("wins"), 1.0)
    assertEquals(aggregate.aggregates("mean_score"), 15.0)
    assertEquals(aggregate.aggregates("min_score"), 10.0)
    assertEquals(aggregate.aggregates("max_score"), 20.0)
  }

  test("a metric is aggregated only over the matches where it is defined") {
    val aggregate = PlayerAggregation.aggregate("p1", List(firstMatch, secondMatch)).get
    assertEquals(aggregate.aggregates("mean_bestLap"), 90.0)
    assertEquals(aggregate.aggregates("max_bestLap"), 90.0)
  }

  test("pattern counts include only the patterns of that player") {
    val aggregate = PlayerAggregation.aggregate("p1", List(firstMatch, secondMatch)).get
    assertEquals(aggregate.aggregates("patternCount_STREAK"), 2.0)
    assert(!aggregate.aggregates.contains("patternCount_COMEBACK"))
  }

  test("an unknown player has no aggregate") {
    assertEquals(PlayerAggregation.aggregate("ghost", List(firstMatch, secondMatch)), None)
  }

  test("game aggregate counts matches and summarizes match metrics and patterns") {
    val aggregate = GameAggregation.aggregate("toy", List(firstMatch, secondMatch, otherGame)).get
    assertEquals(aggregate.aggregates("matchesAnalyzed"), 2.0)
    assertEquals(aggregate.aggregates("matchesCompleted"), 1.0)
    assertEquals(aggregate.aggregates("mean_totalScore"), 20.0)
    assertEquals(aggregate.aggregates("min_totalScore"), 14.0)
    assertEquals(aggregate.aggregates("max_totalScore"), 26.0)
    assertEquals(aggregate.aggregates("patternCount_STREAK"), 2.0)
    assertEquals(aggregate.aggregates("patternCount_COMEBACK"), 1.0)
  }

  test("a game without matches has no aggregate") {
    assertEquals(GameAggregation.aggregate("missing", List(firstMatch)), None)
  }
