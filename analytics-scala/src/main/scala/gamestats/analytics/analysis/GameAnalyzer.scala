package gamestats.analytics.analysis

import gamestats.analytics.domain.*

trait GameAnalyzer:
  def gameId: String

  def analyze(batch: MatchBatch): GameInsights

  def additionalPlayerIds(events: List[Event]): Set[String] = Set.empty

final class GameAnalyzerRegistry(analyzers: List[GameAnalyzer]):
  private val analyzersByGameId: Map[String, GameAnalyzer] =
    analyzers.map(analyzer => analyzer.gameId -> analyzer).toMap

  def find(gameId: String): Option[GameAnalyzer] = analyzersByGameId.get(gameId)

  def registeredGameIds: List[String] = analyzersByGameId.keys.toList.sorted

object GameAnalyzerRegistry:
  val withoutGameAnalyzers: GameAnalyzerRegistry = GameAnalyzerRegistry(Nil)
