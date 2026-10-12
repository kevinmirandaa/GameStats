package gamestats.analytics.aggregation

import gamestats.analytics.domain.*

object MetricStatistics:

  def summarize(seriesByMetricName: Map[String, List[Double]]): Map[String, Double] =
    seriesByMetricName.flatMap { (metricName, values) =>
      Map(
        s"mean_$metricName" -> values.sum / values.size,
        s"min_$metricName" -> values.min,
        s"max_$metricName" -> values.max
      )
    }

  def patternCounts(patterns: List[DetectedPattern]): Map[String, Double] =
    patterns.groupMapReduce(pattern => s"patternCount_${pattern.name}")(_ => 1.0)(_ + _)

object PlayerAggregation:

  def aggregate(playerId: String, analyses: List[MatchAnalysis]): Option[PlayerAggregate] =
    val participations = analyses.filter(_.players.exists(_.playerId == playerId))
    Option.when(participations.nonEmpty)(buildAggregate(playerId, participations))

  private def buildAggregate(playerId: String, participations: List[MatchAnalysis]): PlayerAggregate =
    val series = participations
      .flatMap(_.players.filter(_.playerId == playerId))
      .flatMap(_.metrics.toList)
      .groupMap(_._1)(_._2)
    val playerPatterns = participations.flatMap(_.patterns.filter(_.playerId == playerId))
    val totals = Map(
      "matchesPlayed" -> participations.size.toDouble,
      "wins" -> participations.count(_.winnerPlayerId.contains(playerId)).toDouble
    )
    PlayerAggregate(
      playerId = playerId,
      matchIds = participations.map(_.matchId).sorted,
      aggregates = totals ++ MetricStatistics.summarize(series) ++ MetricStatistics.patternCounts(playerPatterns)
    )

object GameAggregation:

  def aggregate(gameId: String, analyses: List[MatchAnalysis]): Option[GameAggregate] =
    val gameAnalyses = analyses.filter(_.gameId == gameId)
    Option.when(gameAnalyses.nonEmpty)(buildAggregate(gameId, gameAnalyses))

  private def buildAggregate(gameId: String, gameAnalyses: List[MatchAnalysis]): GameAggregate =
    val series = gameAnalyses.flatMap(_.matchMetrics.toList).groupMap(_._1)(_._2)
    val totals = Map(
      "matchesAnalyzed" -> gameAnalyses.size.toDouble,
      "matchesCompleted" -> gameAnalyses.count(_.status == MatchStatus.Completed).toDouble
    )
    GameAggregate(
      gameId = gameId,
      aggregates = totals ++ MetricStatistics.summarize(series) ++ MetricStatistics.patternCounts(gameAnalyses.flatMap(_.patterns))
    )
