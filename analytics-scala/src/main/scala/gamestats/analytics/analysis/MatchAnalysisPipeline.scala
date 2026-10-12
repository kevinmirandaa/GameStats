package gamestats.analytics.analysis

import gamestats.analytics.domain.*

object MatchAnalysisPipeline:

  def analyze(batch: MatchBatch, registry: GameAnalyzerRegistry): MatchAnalysis =
    val orderedEvents = batch.events.sortBy(_.timestamp)
    val orderedBatch = batch.copy(events = orderedEvents)
    val gameAnalyzer = registry.find(batch.gameId)
    val insights = gameAnalyzer.map(_.analyze(orderedBatch)).getOrElse(GameInsights.empty)
    val additionalPlayers = gameAnalyzer.map(_.additionalPlayerIds(orderedEvents)).getOrElse(Set.empty)

    MatchAnalysis(
      matchId = batch.matchId,
      gameId = batch.gameId,
      status = batch.status,
      winnerPlayerId = winnerOf(orderedEvents),
      durationSeconds = MatchDuration.secondsOf(orderedEvents),
      totalEvents = orderedEvents.size,
      eventCountsByType = countBy(orderedEvents.map(_.eventType.wireName)),
      eventCountsByAction = countBy(orderedEvents.flatMap(_.action)),
      matchMetrics = insights.matchMetrics,
      players = playersOf(orderedEvents, additionalPlayers, insights),
      patterns = orderedPatterns(insights.patterns, orderedEvents)
    )

  private def winnerOf(events: List[Event]): Option[String] =
    events.findLast(_.eventType == EventType.MatchFinished).flatMap(_.data.text("winnerPlayerId"))

  private def countBy(names: List[String]): Map[String, Int] =
    names.groupMapReduce(identity)(_ => 1)(_ + _)

  private def playersOf(events: List[Event], additionalPlayers: Set[String], insights: GameInsights): List[PlayerAnalysis] =
    val playerIds = (events.flatMap(_.playerId).toSet ++ additionalPlayers).toList.sorted
    playerIds.map { playerId =>
      PlayerAnalysis(
        playerId = playerId,
        eventCount = events.count(_.playerId.contains(playerId)),
        metrics = insights.playerMetrics.getOrElse(playerId, Map.empty)
      )
    }

  private def orderedPatterns(patterns: List[DetectedPattern], events: List[Event]): List[DetectedPattern] =
    val positionByEventId = events.zipWithIndex.map((event, position) => event.eventId -> position).toMap
    patterns.sortBy(pattern => (positionByEventId.getOrElse(pattern.startEventId, Int.MaxValue), pattern.name))
