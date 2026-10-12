package gamestats.analytics.analysis.games

import gamestats.analytics.analysis.{GameAnalyzer, MatchDuration}
import gamestats.analytics.domain.*

object CombatAnalyzer extends GameAnalyzer:
  val gameId: String = "combat"

  def analyze(batch: MatchBatch): GameInsights =
    GameInsights(
      matchMetrics = CombatMetrics.forMatch(batch.events, MatchDuration.secondsOf(batch.events)),
      playerMetrics = CombatMetrics.forPlayers(batch.events),
      patterns = CombatPatterns.detect(batch.events, MatchResult.of(batch))
    )

  override def additionalPlayerIds(events: List[Event]): Set[String] =
    Elimination.allIn(events).map(_.victimId).toSet
