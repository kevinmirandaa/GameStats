package gamestats.analytics.analysis.games

import gamestats.analytics.analysis.GameAnalyzer
import gamestats.analytics.domain.*

object Connect4Analyzer extends GameAnalyzer:
  val gameId: String = "connect4"

  def analyze(batch: MatchBatch): GameInsights =
    val playerIds = batch.events.flatMap(_.playerId).distinct
    GameInsights(
      matchMetrics = forMatch(batch.events),
      playerMetrics = playerIds.map(playerId => playerId -> forPlayer(playerId, batch.events)).toMap,
      patterns = Nil
    )

  private def forMatch(events: List[Event]): Map[String, Double] =
    val decisions = events.filter(_.isAction("AI_DECISION"))
    val finish = events.findLast(_.eventType == EventType.MatchFinished)
    val result = GameOutcomes.finishResult(events)
    val winnerKind = finish.flatMap(_.data.text("winnerKind"))
    Statistics.definedOnly(
      "totalMoves" -> Some(events.count(_.isAction("MOVE")).toDouble),
      "aiDecisions" -> Option.when(decisions.nonEmpty)(decisions.size.toDouble),
      "averageAiNodes" -> Statistics.mean(decisions.flatMap(_.data.number("nodesSearched"))),
      "averageAiMillis" -> Statistics.mean(decisions.flatMap(_.data.number("decisionMillis"))),
      "humanWin" -> Some(GameOutcomes.flag(result.contains("WIN") && winnerKind.contains("HUMAN"))),
      "computerWin" -> Some(GameOutcomes.flag(result.contains("WIN") && winnerKind.contains("COMPUTER"))),
      "drawn" -> Some(GameOutcomes.drawn(events)),
      "abandoned" -> Some(GameOutcomes.abandoned(events))
    )

  private def forPlayer(playerId: String, events: List[Event]): Map[String, Double] =
    val own = events.filter(_.playerId.contains(playerId))
    val decisions = own.filter(_.isAction("AI_DECISION"))
    Statistics.definedOnly(
      "moves" -> Some(own.count(_.isAction("MOVE")).toDouble),
      "aiDecisions" -> Option.when(decisions.nonEmpty)(decisions.size.toDouble),
      "averageAiNodes" -> Statistics.mean(decisions.flatMap(_.data.number("nodesSearched"))),
      "averageAiMillis" -> Statistics.mean(decisions.flatMap(_.data.number("decisionMillis"))),
      "won" -> Some(GameOutcomes.playerWon(events, playerId)),
      "lost" -> Some(GameOutcomes.playerLost(events, playerId)),
      "drawn" -> Some(GameOutcomes.playerDrew(events, playerId))
    )
