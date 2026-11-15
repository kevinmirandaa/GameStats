package gamestats.analytics

import gamestats.analytics.aggregation.OverviewAggregation
import gamestats.analytics.domain.*
import gamestats.analytics.TestSupport.*

class OverviewSpec extends munit.FunSuite:

  private def player(id: String): PlayerAnalysis = PlayerAnalysis(id, 3, Map.empty)

  private val analyses = List(
    analysisOf("c4-1", "connect4", Some("ana"), List(player("ana"), player("ai"))),
    analysisOf("c4-2", "connect4", None, List(player("ana"), player("ai")), status = MatchStatus.Incomplete),
    analysisOf("mem-1", "memory", Some("luis"), List(player("ana"), player("luis")))
  )

  test("the overview counts every analyzed match by status") {
    val overview = OverviewAggregation.overview(analyses)

    assertEquals(overview.totalMatches, 3)
    assertEquals(overview.completedMatches, 2)
    assertEquals(overview.incompleteMatches, 1)
    assertEquals(overview.totalEvents, 15)
  }

  test("the overview counts distinct players across games") {
    assertEquals(OverviewAggregation.overview(analyses).playerCount, 3)
  }

  test("the overview separates the instances of each game") {
    val games = OverviewAggregation.overview(analyses).games.map(game => game.gameId -> (game.matches, game.completed, game.incomplete, game.playerCount))

    assertEquals(games, List("connect4" -> (2, 1, 1, 2), "memory" -> (1, 1, 0, 2)))
  }

  test("the overview reports the average duration of each game") {
    assertEquals(OverviewAggregation.overview(analyses).games.map(_.averageDurationSeconds), List(10.0, 10.0))
  }

  test("an empty store produces an empty overview") {
    val overview = OverviewAggregation.overview(Nil)

    assertEquals((overview.totalMatches, overview.playerCount, overview.games), (0, 0, Nil))
  }
