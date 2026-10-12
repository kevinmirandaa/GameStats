package gamestats.analytics

import gamestats.analytics.analysis.GameAnalyzerRegistry
import gamestats.analytics.http.AnalyticsRoutes
import gamestats.analytics.service.AnalyticsService
import gamestats.analytics.storage.InMemoryAnalysisStore

object Main extends cask.Main:
  private val defaultPort = 8081

  override def port: Int = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(defaultPort)

  override def host: String = "0.0.0.0"

  private val service = AnalyticsService(GameAnalyzerRegistry.withoutGameAnalyzers, InMemoryAnalysisStore())

  val allRoutes: Seq[cask.Routes] = Seq(AnalyticsRoutes(service))
