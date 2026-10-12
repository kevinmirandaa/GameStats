package gamestats.analytics

import gamestats.analytics.analysis.games.RacingAnalyzer
import gamestats.analytics.domain.*
import gamestats.analytics.TestSupport.*

class RacingRulesSpec extends munit.FunSuite:

  private def lap(second: Int, id: String, player: String, lapTimeMs: Int, position: Int, lapNumber: Int = 1): Event =
    eventAt(
      second, id, Some(player), Some("LAP_COMPLETED"),
      ujson.Obj("lap" -> lapNumber, "lapTimeMs" -> lapTimeMs, "position" -> position, "topSpeedKmh" -> 250.0)
    )

  private def streaksIn(lapTimes: List[Int]): List[DetectedPattern] =
    val laps = lapTimes.zipWithIndex.map((time, index) => lap(index, s"lap-$index", "p1", time, 1, index + 1))
    RacingAnalyzer.analyze(toyBatch(laps)).patterns.filter(_.name == "IMPROVING_LAP_STREAK")

  test("three consecutive improving laps form a streak") {
    val streaks = streaksIn(List(95000, 94000, 93000))
    assertEquals(streaks.map(_.details("streakLength")), List(3.0))
    assertEquals(streaks.map(_.startEventId), List("lap-0"))
    assertEquals(streaks.map(_.endEventId), List("lap-2"))
  }

  test("two improving laps are not a streak") {
    assertEquals(streaksIn(List(95000, 94000, 96000)), Nil)
  }

  test("equal lap times do not continue a streak") {
    assertEquals(streaksIn(List(95000, 94000, 94000, 93000)), Nil)
  }

  test("a streak is reported once with its full length") {
    assertEquals(streaksIn(List(99000, 98000, 97000, 96000, 100000)).map(_.details("streakLength")), List(4.0))
  }

  test("each driver is evaluated separately") {
    val interleaved = List(
      lap(0, "a1", "p1", 95000, 1), lap(1, "b1", "p2", 90000, 2),
      lap(2, "a2", "p1", 94000, 1), lap(3, "b2", "p2", 91000, 2),
      lap(4, "a3", "p1", 93000, 1), lap(5, "b3", "p2", 92000, 2)
    )
    val streaks = RacingAnalyzer.analyze(toyBatch(interleaved)).patterns
    assertEquals(streaks.map(_.playerId), List("p1"))
  }

  private def comebackWith(worstPosition: Int, status: MatchStatus = MatchStatus.Completed, finishes: Boolean = true): List[DetectedPattern] =
    val laps = List(lap(0, "l1", "winner", 90000, worstPosition), lap(1, "l2", "winner", 91000, 1, 2))
    val ending = if finishes then List(finishEvent(2, "finish", Some("winner"), List("winner"))) else Nil
    RacingAnalyzer.analyze(toyBatch(laps ++ ending, status)).patterns.filter(_.name == "RACE_COMEBACK")

  test("a winner who was fourth at some lap made a comeback") {
    val comeback = comebackWith(4)
    assertEquals(comeback.map(_.playerId), List("winner"))
    assertEquals(comeback.head.details("worstPosition"), 4.0)
    assertEquals(comeback.head.endEventId, "finish")
  }

  test("a winner who was never worse than third made no comeback") {
    assertEquals(comebackWith(3), Nil)
  }

  test("an incomplete match has no comeback") {
    assertEquals(comebackWith(5, MatchStatus.Incomplete, finishes = false), Nil)
    assertEquals(comebackWith(5, MatchStatus.Incomplete), Nil)
  }

  test("the comeback starts at the first lap with the worst position") {
    val laps = List(lap(0, "l1", "winner", 90000, 5), lap(1, "l2", "winner", 91000, 5, 2), lap(2, "l3", "winner", 92000, 1, 3))
    val batch = toyBatch(laps :+ finishEvent(3, "finish", Some("winner"), List("winner")))
    assertEquals(RacingAnalyzer.analyze(batch).patterns.map(_.startEventId), List("l1"))
  }

  test("driver metrics omit values that cannot be computed") {
    val joined = eventAt(0, "joined", Some("quiet")).copy(data = EventData(ujson.Obj("startPosition" -> 2)))
    val metrics = RacingAnalyzer.analyze(toyBatch(List(joined))).playerMetrics("quiet")
    assertEquals(metrics("lapsCompleted"), 0.0)
    assertEquals(metrics("penaltySecondsTotal"), 0.0)
    assert(!metrics.contains("bestLapMs"))
    assert(!metrics.contains("effectiveRaceTimeMs"))
    assert(!metrics.contains("finalPosition"))
    assert(!metrics.contains("positionsGained"))
  }

  test("effective race time adds penalties to the lap times") {
    val events = List(
      lap(0, "l1", "p1", 90000, 1),
      lap(1, "l2", "p1", 91000, 1, 2),
      eventAt(2, "pen", Some("p1"), Some("PENALTY_APPLIED"), ujson.Obj("lap" -> 2, "seconds" -> 5, "reason" -> "cut"))
    )
    assertEquals(RacingAnalyzer.analyze(toyBatch(events)).playerMetrics("p1")("effectiveRaceTimeMs"), 186000.0)
  }

  test("an incomplete match reports no final positions and zero finished players") {
    val joined = eventAt(0, "joined", Some("p1")).copy(data = EventData(ujson.Obj("startPosition" -> 1)))
    val insights = RacingAnalyzer.analyze(toyBatch(List(joined, lap(1, "l1", "p1", 90000, 1)), MatchStatus.Incomplete))
    assertEquals(insights.matchMetrics("finishedPlayers"), 0.0)
    assert(!insights.playerMetrics("p1").contains("finalPosition"))
  }

  test("a match without laps has no fastest or average lap") {
    val insights = RacingAnalyzer.analyze(toyBatch(List(eventAt(0, "joined", Some("p1")))))
    assertEquals(insights.matchMetrics("totalLapsCompleted"), 0.0)
    assert(!insights.matchMetrics.contains("fastestLapMs"))
    assert(!insights.matchMetrics.contains("averageLapMs"))
  }
