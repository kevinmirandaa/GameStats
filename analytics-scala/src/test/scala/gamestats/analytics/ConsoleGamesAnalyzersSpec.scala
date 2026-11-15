package gamestats.analytics

import gamestats.analytics.analysis.GameAnalyzerRegistry
import gamestats.analytics.analysis.MatchAnalysisPipeline
import gamestats.analytics.analysis.games.{Connect4Analyzer, MemoryAnalyzer, MinesweeperAnalyzer}
import gamestats.analytics.domain.*
import gamestats.analytics.TestSupport.*

class ConsoleGamesAnalyzersSpec extends munit.FunSuite:

  private val human = "player-ana"
  private val computer = "ai-prolog-connect4"

  private def finish(second: Int, data: ujson.Obj): Event =
    eventAt(second, s"finish-$second", None).copy(eventType = EventType.MatchFinished, data = EventData(data))

  private def move(second: Int, playerId: String): Event =
    eventAt(second, s"move-$second", Some(playerId), Some("MOVE"), ujson.Obj("column" -> 3))

  private def decision(second: Int, nodes: Int, millis: Int): Event =
    eventAt(second, s"decision-$second", Some(computer), Some("AI_DECISION"), ujson.Obj("nodesSearched" -> nodes, "decisionMillis" -> millis))

  private def action(second: Int, playerId: String, name: String, data: ujson.Obj = ujson.Obj()): Event =
    eventAt(second, s"$name-$second", Some(playerId), Some(name), data)

  private def connect4Match(winnerId: String, winnerKind: String): List[Event] =
    List(
      move(1, human), decision(2, 100, 20), move(3, computer),
      move(4, human), decision(5, 300, 40), move(6, computer),
      action(7, winnerId, "PLAYER_WON"),
      action(8, if winnerId == human then computer else human, "PLAYER_LOST"),
      finish(9, ujson.Obj("result" -> "WIN", "winnerPlayerId" -> winnerId, "winnerKind" -> winnerKind))
    )

  test("connect4 counts moves and summarizes the Prolog decisions of the match") {
    val insights = Connect4Analyzer.analyze(toyBatch(connect4Match(human, "HUMAN")))

    assertEquals(insights.matchMetrics("totalMoves"), 4.0)
    assertEquals(insights.matchMetrics("aiDecisions"), 2.0)
    assertEquals(insights.matchMetrics("averageAiNodes"), 200.0)
    assertEquals(insights.matchMetrics("averageAiMillis"), 30.0)
  }

  test("connect4 tells apart a human win from a computer win") {
    val humanWin = Connect4Analyzer.analyze(toyBatch(connect4Match(human, "HUMAN"))).matchMetrics
    val computerWin = Connect4Analyzer.analyze(toyBatch(connect4Match(computer, "COMPUTER"))).matchMetrics

    assertEquals((humanWin("humanWin"), humanWin("computerWin")), (1.0, 0.0))
    assertEquals((computerWin("humanWin"), computerWin("computerWin")), (0.0, 1.0))
  }

  test("connect4 reports per player moves, outcome and only gives AI metrics to the computer") {
    val players = Connect4Analyzer.analyze(toyBatch(connect4Match(human, "HUMAN"))).playerMetrics

    assertEquals(players(human)("moves"), 2.0)
    assertEquals(players(human)("won"), 1.0)
    assertEquals(players(human)("lost"), 0.0)
    assert(!players(human).contains("aiDecisions"))
    assertEquals(players(computer)("aiDecisions"), 2.0)
    assertEquals(players(computer)("lost"), 1.0)
  }

  test("connect4 draws and abandoned matches are flagged") {
    val drawn = Connect4Analyzer.analyze(toyBatch(List(move(1, human), finish(2, ujson.Obj("result" -> "DRAW"))))).matchMetrics
    val abandoned = Connect4Analyzer.analyze(toyBatch(List(move(1, human), finish(2, ujson.Obj("result" -> "ABANDONED"))))).matchMetrics

    assertEquals((drawn("drawn"), drawn("abandoned")), (1.0, 0.0))
    assertEquals((abandoned("drawn"), abandoned("abandoned")), (0.0, 1.0))
    assertEquals((abandoned("humanWin"), abandoned("computerWin")), (0.0, 0.0))
  }

  test("connect4 handles a match without events or without Prolog decisions") {
    val empty = Connect4Analyzer.analyze(toyBatch(Nil))
    val noDecisions = Connect4Analyzer.analyze(toyBatch(List(move(1, human), move(2, "player-luis"))))

    assertEquals(empty.matchMetrics("totalMoves"), 0.0)
    assert(!noDecisions.matchMetrics.contains("aiDecisions"))
    assert(!noDecisions.matchMetrics.contains("averageAiNodes"))
  }

  private def flip(second: Int, playerId: String): Event = action(second, playerId, "FLIP_CARD")

  private def memoryMatch: List[Event] =
    List(
      flip(1, "player-ana"), flip(2, "player-ana"), action(3, "player-ana", "PAIR_FOUND"),
      flip(4, "player-ana"), flip(5, "player-ana"), action(6, "player-ana", "PAIR_MISSED"),
      flip(7, "player-luis"), flip(8, "player-luis"), action(9, "player-luis", "PAIR_MISSED"),
      action(10, "player-ana", "PLAYER_WON"), action(11, "player-luis", "PLAYER_LOST"),
      finish(12, ujson.Obj("result" -> "WIN", "winnerPlayerId" -> "player-ana"))
    )

  test("memory computes flips, pairs and accuracy for the match") {
    val metrics = MemoryAnalyzer.analyze(toyBatch(memoryMatch)).matchMetrics

    assertEquals(metrics("totalFlips"), 6.0)
    assertEquals(metrics("pairsFound"), 1.0)
    assertEquals(metrics("attempts"), 3.0)
    assertEqualsDouble(metrics("accuracyPercent"), 100.0 / 3, 0.0001)
  }

  test("memory computes the accuracy of each player and omits it when they never tried a pair") {
    val players = MemoryAnalyzer.analyze(toyBatch(memoryMatch)).playerMetrics

    assertEquals(players("player-ana")("pairsFound"), 1.0)
    assertEquals(players("player-ana")("pairsMissed"), 1.0)
    assertEquals(players("player-ana")("accuracyPercent"), 50.0)
    assertEquals(players("player-luis")("accuracyPercent"), 0.0)
    assertEquals(players("player-ana")("won"), 1.0)
    assertEquals(players("player-luis")("lost"), 1.0)

    val onlyFirstFlips = MemoryAnalyzer.analyze(toyBatch(List(flip(1, "player-ana")))).playerMetrics
    assert(!onlyFirstFlips("player-ana").contains("accuracyPercent"))
  }

  private def minesweeperMatch(lost: Boolean): List[Event] =
    val reveals = List(
      action(1, "player-ana", "REVEAL_CELL", ujson.Obj("cellsRevealed" -> 20, "cascade" -> true)),
      action(2, "player-ana", "FLAG_CELL", ujson.Obj("flagged" -> true)),
      action(3, "player-ana", "FLAG_CELL", ujson.Obj("flagged" -> false)),
      action(4, "player-ana", "REVEAL_CELL", ujson.Obj("cellsRevealed" -> 1, "cascade" -> false))
    )
    val ending =
      if lost then List(action(5, "player-ana", "PLAYER_LOST", ujson.Obj("secondsElapsed" -> 42)), finish(6, ujson.Obj("result" -> "LOSS", "secondsElapsed" -> 42)))
      else List(action(5, "player-ana", "PLAYER_WON", ujson.Obj("secondsElapsed" -> 70)), finish(6, ujson.Obj("result" -> "WIN", "secondsElapsed" -> 70, "winnerPlayerId" -> "player-ana")))
    reveals ++ ending

  test("minesweeper summarizes reveals, flags, cascades and time") {
    val metrics = MinesweeperAnalyzer.analyze(toyBatch(minesweeperMatch(lost = false))).matchMetrics

    assertEquals(metrics("reveals"), 2.0)
    assertEquals(metrics("flagsPlaced"), 1.0)
    assertEquals(metrics("cellsRevealed"), 21.0)
    assertEquals(metrics("cascades"), 1.0)
    assertEquals(metrics("secondsElapsed"), 70.0)
    assertEquals(metrics("abandoned"), 0.0)
  }

  test("minesweeper tells a win from a loss per player") {
    val won = MinesweeperAnalyzer.analyze(toyBatch(minesweeperMatch(lost = false))).playerMetrics("player-ana")
    val lost = MinesweeperAnalyzer.analyze(toyBatch(minesweeperMatch(lost = true))).playerMetrics("player-ana")

    assertEquals((won("won"), won("lost")), (1.0, 0.0))
    assertEquals((lost("won"), lost("lost")), (0.0, 1.0))
  }

  test("minesweeper omits the elapsed time of a match without a finish event") {
    val metrics = MinesweeperAnalyzer.analyze(toyBatch(List(action(1, "player-ana", "REVEAL_CELL", ujson.Obj("cellsRevealed" -> 3))))).matchMetrics

    assert(!metrics.contains("secondsElapsed"))
  }

  test("the pipeline analyzes a connect4 match with the registered analyzers and reports the winner") {
    val registry = GameAnalyzerRegistry(List(Connect4Analyzer, MemoryAnalyzer, MinesweeperAnalyzer))
    val batch = MatchBatch("connect4", "1.0", "connect4-abc", MatchStatus.Completed, connect4Match(human, "HUMAN"))

    val analysis = MatchAnalysisPipeline.analyze(batch, registry)

    assertEquals(analysis.winnerPlayerId, Some(human))
    assertEquals(analysis.matchMetrics("humanWin"), 1.0)
    assertEquals(analysis.players.map(_.playerId).sorted, List(computer, human).sorted)
  }

  test("every console game is registered under its game identifier") {
    assertEquals(List(Connect4Analyzer, MemoryAnalyzer, MinesweeperAnalyzer).map(_.gameId), List("connect4", "memory", "minesweeper"))
  }
