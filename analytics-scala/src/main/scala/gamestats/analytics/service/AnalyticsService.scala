package gamestats.analytics.service

import gamestats.analytics.aggregation.{GameAggregation, PlayerAggregation}
import gamestats.analytics.analysis.{GameAnalyzerRegistry, MatchAnalysisPipeline}
import gamestats.analytics.decoding.BatchDecoder
import gamestats.analytics.domain.*
import gamestats.analytics.storage.AnalysisStore

final class AnalyticsService(registry: GameAnalyzerRegistry, store: AnalysisStore):

  def analyzeBatch(rawJson: String): Either[String, MatchAnalysis] =
    BatchDecoder.decode(rawJson).map { batch =>
      val analysis = MatchAnalysisPipeline.analyze(batch, registry)
      store.save(analysis)
      analysis
    }

  def matchSummaries(gameId: Option[String]): List[MatchSummary] =
    store.all
      .filter(analysis => gameId.forall(_ == analysis.gameId))
      .map(analysis =>
        MatchSummary(
          matchId = analysis.matchId,
          gameId = analysis.gameId,
          status = analysis.status,
          totalEvents = analysis.totalEvents,
          durationSeconds = analysis.durationSeconds,
          patternCount = analysis.patterns.size
        )
      )

  def matchAnalysis(matchId: String): Option[MatchAnalysis] = store.find(matchId)

  def playerAggregate(playerId: String): Option[PlayerAggregate] =
    PlayerAggregation.aggregate(playerId, store.all)

  def gameAggregate(gameId: String): Option[GameAggregate] =
    GameAggregation.aggregate(gameId, store.all)

  def patterns(gameId: Option[String], patternName: Option[String]): List[PatternOccurrence] =
    store.all
      .filter(analysis => gameId.forall(_ == analysis.gameId))
      .flatMap(analysis => analysis.patterns.map(pattern => PatternOccurrence(analysis.matchId, analysis.gameId, pattern)))
      .filter(occurrence => patternName.forall(_ == occurrence.pattern.name))
