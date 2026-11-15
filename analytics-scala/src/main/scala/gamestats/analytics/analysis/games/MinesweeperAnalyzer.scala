package gamestats.analytics.analysis.games

import gamestats.analytics.analysis.GameAnalyzer
import gamestats.analytics.domain.*

object MinesweeperAnalyzer extends GameAnalyzer:
  val gameId: String = "minesweeper"

  def analyze(batch: MatchBatch): GameInsights =
    val playerIds = batch.events.flatMap(_.playerId).distinct
    GameInsights(
      matchMetrics = forMatch(batch.events),
      playerMetrics = playerIds.map(playerId => playerId -> forPlayer(playerId, batch.events)).toMap,
      patterns = Nil
    )

  private def flagsPlaced(events: List[Event]): Int =
    events.count(event => event.isAction("FLAG_CELL") && event.data.boolean("flagged").contains(true))

  private def cellsRevealed(events: List[Event]): Double =
    events.filter(_.isAction("REVEAL_CELL")).flatMap(_.data.number("cellsRevealed")).sum

  private def forMatch(events: List[Event]): Map[String, Double] =
    val finish = events.findLast(_.eventType == EventType.MatchFinished)
    Statistics.definedOnly(
      "reveals" -> Some(events.count(_.isAction("REVEAL_CELL")).toDouble),
      "flagsPlaced" -> Some(flagsPlaced(events).toDouble),
      "cellsRevealed" -> Some(cellsRevealed(events)),
      "cascades" -> Some(events.count(event => event.isAction("REVEAL_CELL") && event.data.boolean("cascade").contains(true)).toDouble),
      "secondsElapsed" -> finish.flatMap(_.data.number("secondsElapsed")),
      "abandoned" -> Some(GameOutcomes.abandoned(events))
    )

  private def forPlayer(playerId: String, events: List[Event]): Map[String, Double] =
    val own = events.filter(_.playerId.contains(playerId))
    Statistics.definedOnly(
      "reveals" -> Some(own.count(_.isAction("REVEAL_CELL")).toDouble),
      "flagsPlaced" -> Some(flagsPlaced(own).toDouble),
      "cellsRevealed" -> Some(cellsRevealed(own)),
      "won" -> Some(GameOutcomes.playerWon(events, playerId)),
      "lost" -> Some(GameOutcomes.playerLost(events, playerId))
    )
