package gamestats.analytics.storage

import java.util.concurrent.atomic.AtomicReference

import gamestats.analytics.domain.MatchAnalysis

trait AnalysisStore:
  def save(analysis: MatchAnalysis): Unit

  def find(matchId: String): Option[MatchAnalysis]

  def all: List[MatchAnalysis]

final class InMemoryAnalysisStore extends AnalysisStore:
  private val analysesByMatchId = AtomicReference(Map.empty[String, MatchAnalysis])

  def save(analysis: MatchAnalysis): Unit =
    analysesByMatchId.updateAndGet(_.updated(analysis.matchId, analysis))
    ()

  def find(matchId: String): Option[MatchAnalysis] = analysesByMatchId.get().get(matchId)

  def all: List[MatchAnalysis] = analysesByMatchId.get().values.toList.sortBy(_.matchId)
