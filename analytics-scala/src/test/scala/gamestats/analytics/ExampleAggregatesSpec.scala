package gamestats.analytics

import gamestats.analytics.analysis.games.{CombatAnalyzer, RacingAnalyzer}
import gamestats.analytics.analysis.GameAnalyzerRegistry
import gamestats.analytics.service.AnalyticsService
import gamestats.analytics.storage.InMemoryAnalysisStore
import gamestats.analytics.TestSupport.*

class ExampleAggregatesSpec extends munit.FunSuite:

  private val tolerance = 0.001

  private val service =
    val analyticsService = AnalyticsService(GameAnalyzerRegistry(List(RacingAnalyzer, CombatAnalyzer)), InMemoryAnalysisStore())
    exampleMatches.foreach((game, name) => assert(analyticsService.analyzeBatch(readBatchJson(game, name)).isRight))
    analyticsService

  private def gameAggregates(gameId: String): Map[String, Double] = service.gameAggregate(gameId).get.aggregates

  private def playerAggregates(playerId: String): Map[String, Double] = service.playerAggregate(playerId).get.aggregates

  private def assertValue(aggregates: Map[String, Double], name: String, expected: Double): Unit =
    assertEqualsDouble(aggregates(name), expected, tolerance, name)

  test("racing game aggregate over the two example races") {
    val aggregates = gameAggregates("racing")
    assertValue(aggregates, "matchesAnalyzed", 2)
    assertValue(aggregates, "matchesCompleted", 2)
    assertValue(aggregates, "min_fastestLapMs", 88000)
    assertValue(aggregates, "max_fastestLapMs", 89500)
    assertValue(aggregates, "mean_fastestLapMs", 88750)
    assertValue(aggregates, "mean_totalLapsCompleted", 14)
    assertValue(aggregates, "patternCount_IMPROVING_LAP_STREAK", 2)
    assertValue(aggregates, "patternCount_RACE_COMEBACK", 1)
  }

  test("combat game aggregate over the two example fights") {
    val aggregates = gameAggregates("combat")
    assertValue(aggregates, "matchesAnalyzed", 2)
    assertValue(aggregates, "min_totalEliminations", 7)
    assertValue(aggregates, "max_totalEliminations", 10)
    assertValue(aggregates, "mean_totalEliminations", 8.5)
    assertValue(aggregates, "mean_mostEliminationsByOnePlayer", 5)
    assertValue(aggregates, "patternCount_KILL_STREAK", 2)
    assertValue(aggregates, "patternCount_SCORE_COMEBACK", 1)
  }

  test("the racing winner aggregate includes win and patterns") {
    val aggregates = playerAggregates("player-04")
    assertValue(aggregates, "matchesPlayed", 1)
    assertValue(aggregates, "wins", 1)
    assertValue(aggregates, "mean_bestLapMs", 89500)
    assertValue(aggregates, "min_overtakesMade", 3)
    assertValue(aggregates, "max_overtakesMade", 3)
    assertValue(aggregates, "patternCount_IMPROVING_LAP_STREAK", 1)
    assertValue(aggregates, "patternCount_RACE_COMEBACK", 1)
  }

  test("the combat winner aggregate includes both of their patterns") {
    val aggregates = playerAggregates("player-21")
    assertValue(aggregates, "wins", 1)
    assertValue(aggregates, "max_eliminations", 6)
    assertValue(aggregates, "mean_killDeathRatio", 3)
    assertValue(aggregates, "patternCount_KILL_STREAK", 1)
    assertValue(aggregates, "patternCount_SCORE_COMEBACK", 1)
  }

  test("a player who only appeared as a victim in the first fight keeps their deaths") {
    val aggregates = playerAggregates("player-23")
    assertValue(aggregates, "wins", 0)
    assertValue(aggregates, "mean_deaths", 5)
    assert(!aggregates.keys.exists(_.startsWith("patternCount_")))
    assert(!aggregates.contains("mean_damagePerElimination"))
  }
