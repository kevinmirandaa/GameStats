package gamestats.analytics

import gamestats.analytics.analysis.games.CombatAnalyzer
import gamestats.analytics.analysis.MatchAnalysisPipeline
import gamestats.analytics.analysis.GameAnalyzerRegistry
import gamestats.analytics.domain.*
import gamestats.analytics.TestSupport.*

class CombatRulesSpec extends munit.FunSuite:

  private def elimination(second: Int, id: String, killer: String, victim: String, distance: Double = 10.0): Event =
    eventAt(second, id, Some(killer), Some("ELIMINATION"), ujson.Obj("victimPlayerId" -> victim, "weapon" -> "rifle", "distanceMeters" -> distance))

  private def eliminations(sequence: (String, String)*): List[Event] =
    sequence.zipWithIndex.map { case ((killer, victim), index) => elimination(index, s"e$index", killer, victim) }.toList

  private def patternsOf(events: List[Event], winner: Option[String] = None, status: MatchStatus = MatchStatus.Completed): List[DetectedPattern] =
    val finish = winner.map(player => finishEvent(events.size + 1, "finish", Some(player))).toList
    CombatAnalyzer.analyze(toyBatch(events ++ finish, status)).patterns

  test("three eliminations in a row are a kill streak") {
    val streaks = patternsOf(eliminations("a" -> "b", "a" -> "b", "a" -> "c")).filter(_.name == "KILL_STREAK")
    assertEquals(streaks.map(_.playerId), List("a"))
    assertEquals(streaks.head.details("streakLength"), 3.0)
    assertEquals(streaks.head.startEventId, "e0")
    assertEquals(streaks.head.endEventId, "e2")
  }

  test("two eliminations in a row are not a kill streak") {
    assertEquals(patternsOf(eliminations("a" -> "b", "a" -> "b")), Nil)
  }

  test("being eliminated interrupts a streak and the count starts again") {
    val events = eliminations("a" -> "b", "a" -> "b", "b" -> "a", "a" -> "b", "a" -> "b")
    assertEquals(patternsOf(events), Nil)
  }

  test("a streak interrupted after reaching three is still reported") {
    val events = eliminations("a" -> "b", "a" -> "b", "a" -> "c", "b" -> "a")
    val streaks = patternsOf(events)
    assertEquals(streaks.map(pattern => pattern.playerId -> pattern.details("streakLength")), List("a" -> 3.0))
    assertEquals(streaks.head.endEventId, "e2")
  }

  test("different players can have streaks in the same match") {
    val events = eliminations("a" -> "x", "a" -> "x", "b" -> "y", "a" -> "x", "b" -> "y", "b" -> "y")
    assertEquals(patternsOf(events).map(_.playerId).sorted, List("a", "b"))
  }

  private def scoreComebackEvents(deficit: Int): List[Event] =
    val loserKills = List.fill(deficit)("loser" -> "winner")
    val winnerKills = List.fill(deficit + 1)("winner" -> "loser")
    eliminations((loserKills ++ winnerKills)*)

  test("a winner who trailed by three eliminations made a comeback") {
    val comeback = patternsOf(scoreComebackEvents(3), Some("winner")).filter(_.name == "SCORE_COMEBACK")
    assertEquals(comeback.map(_.playerId), List("winner"))
    assertEquals(comeback.head.details("maxDeficit"), 3.0)
    assertEquals(comeback.head.details("finalEliminations"), 4.0)
    assertEquals(comeback.head.startEventId, "e2")
    assertEquals(comeback.head.endEventId, "finish")
  }

  test("a deficit of two is not a comeback") {
    assertEquals(patternsOf(scoreComebackEvents(2), Some("winner")).filter(_.name == "SCORE_COMEBACK"), Nil)
  }

  test("a match without a declared winner has no comeback") {
    assertEquals(patternsOf(scoreComebackEvents(4), None).filter(_.name == "SCORE_COMEBACK"), Nil)
  }

  test("an incomplete match has no comeback") {
    assertEquals(patternsOf(scoreComebackEvents(4), Some("winner"), MatchStatus.Incomplete).filter(_.name == "SCORE_COMEBACK"), Nil)
  }

  test("a player who only appears as a victim has metrics and is listed as a participant") {
    val events = List(elimination(0, "e0", "hunter", "prey", 25.0))
    val registry = GameAnalyzerRegistry(List(CombatAnalyzer))
    val analysis = MatchAnalysisPipeline.analyze(toyBatch(events).copy(gameId = "combat"), registry)

    assertEquals(analysis.players.map(_.playerId), List("hunter", "prey"))
    val prey = analysis.players.find(_.playerId == "prey").get
    assertEquals(prey.eventCount, 0)
    assertEquals(prey.metrics("deaths"), 1.0)
    assertEquals(prey.metrics("eliminations"), 0.0)
    assertEquals(prey.metrics("killDeathRatio"), 0.0)
    assert(!prey.metrics.contains("accuracyPercent"))
    assert(!prey.metrics.contains("damagePerElimination"))
  }

  test("the kill death ratio never divides by zero") {
    val metrics = CombatAnalyzer.analyze(toyBatch(eliminations("a" -> "b", "a" -> "b"))).playerMetrics("a")
    assertEquals(metrics("killDeathRatio"), 2.0)
  }

  test("accuracy is the percentage of shots that hit") {
    val shots = List(true, true, false, true).zipWithIndex.map { (hit, index) =>
      eventAt(index, s"s$index", Some("a"), Some("SHOT_FIRED"), ujson.Obj("weapon" -> "rifle", "hit" -> hit))
    }
    assertEquals(CombatAnalyzer.analyze(toyBatch(shots)).playerMetrics("a")("accuracyPercent"), 75.0)
  }

  test("a match without eliminations has zero totals and no averages") {
    val metrics = CombatAnalyzer.analyze(toyBatch(List(eventAt(0, "joined", Some("a"))))).matchMetrics
    assertEquals(metrics("totalEliminations"), 0.0)
    assertEquals(metrics("mostEliminationsByOnePlayer"), 0.0)
    assert(!metrics.contains("averageEliminationDistanceMeters"))
    assert(!metrics.contains("eliminationsPerMinute"))
  }
