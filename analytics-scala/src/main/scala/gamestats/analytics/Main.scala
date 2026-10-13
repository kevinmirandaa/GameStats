package gamestats.analytics

import gamestats.analytics.analysis.GameAnalyzerRegistry
import gamestats.analytics.analysis.games.{CombatAnalyzer, RacingAnalyzer}
import gamestats.analytics.http.AnalyticsRoutes
import gamestats.analytics.service.AnalyticsService
import java.nio.file.Path

import gamestats.analytics.storage.{AnalysisStore, FileAnalysisStore, InMemoryAnalysisStore}

object Main extends cask.Main:
  private val defaultPort = 8081

  override def port: Int = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(defaultPort)

  override def host: String = "0.0.0.0"

  private def reportProblem(message: String): Unit = System.err.println(s"analytics: $message")

  private val store: AnalysisStore =
    sys.env.get("RESULTS_DIRECTORY").filter(_.nonEmpty) match
      case Some(directory) => FileAnalysisStore.open(Path.of(directory), reportProblem)
      case None            => InMemoryAnalysisStore()

  private val service = AnalyticsService(GameAnalyzerRegistry(List(RacingAnalyzer, CombatAnalyzer)), store)

  val allRoutes: Seq[cask.Routes] = Seq(AnalyticsRoutes(service))
