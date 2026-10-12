package gamestats.analytics.analysis.games

import gamestats.analytics.analysis.GameAnalyzer
import gamestats.analytics.domain.*

object RacingAnalyzer extends GameAnalyzer:
  val gameId: String = "racing"

  def analyze(batch: MatchBatch): GameInsights =
    val result = MatchResult.of(batch)
    GameInsights(
      matchMetrics = RacingMetrics.forMatch(batch.events, result),
      playerMetrics = RacingMetrics.forPlayers(batch.events, result),
      patterns = RacingPatterns.detect(batch.events, result)
    )
