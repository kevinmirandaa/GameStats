package gamestats.analytics.analysis.games

import gamestats.analytics.analysis.GameAnalyzer
import gamestats.analytics.domain.*

object MemoryAnalyzer extends GameAnalyzer:
  val gameId: String = "memory"

  private val percentFactor = 100.0

  def analyze(batch: MatchBatch): GameInsights =
    val playerIds = batch.events.flatMap(_.playerId).distinct
    GameInsights(
      matchMetrics = forMatch(batch.events),
      playerMetrics = playerIds.map(playerId => playerId -> forPlayer(playerId, batch.events)).toMap,
      patterns = Nil
    )

  private def accuracy(found: Int, missed: Int): Option[Double] =
    Option.when(found + missed > 0)(found * percentFactor / (found + missed))

  private def forMatch(events: List[Event]): Map[String, Double] =
    val found = events.count(_.isAction("PAIR_FOUND"))
    val missed = events.count(_.isAction("PAIR_MISSED"))
    Statistics.definedOnly(
      "totalFlips" -> Some(events.count(_.isAction("FLIP_CARD")).toDouble),
      "pairsFound" -> Some(found.toDouble),
      "attempts" -> Some((found + missed).toDouble),
      "accuracyPercent" -> accuracy(found, missed),
      "drawn" -> Some(GameOutcomes.drawn(events)),
      "abandoned" -> Some(GameOutcomes.abandoned(events))
    )

  private def forPlayer(playerId: String, events: List[Event]): Map[String, Double] =
    val own = events.filter(_.playerId.contains(playerId))
    val found = own.count(_.isAction("PAIR_FOUND"))
    val missed = own.count(_.isAction("PAIR_MISSED"))
    Statistics.definedOnly(
      "flips" -> Some(own.count(_.isAction("FLIP_CARD")).toDouble),
      "pairsFound" -> Some(found.toDouble),
      "pairsMissed" -> Some(missed.toDouble),
      "accuracyPercent" -> accuracy(found, missed),
      "won" -> Some(GameOutcomes.playerWon(events, playerId)),
      "lost" -> Some(GameOutcomes.playerLost(events, playerId)),
      "drawn" -> Some(GameOutcomes.playerDrew(events, playerId))
    )
