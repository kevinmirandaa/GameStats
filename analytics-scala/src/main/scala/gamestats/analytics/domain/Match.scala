package gamestats.analytics.domain

enum MatchStatus(val wireName: String):
  case Completed extends MatchStatus("COMPLETED")
  case Incomplete extends MatchStatus("INCOMPLETE")

object MatchStatus:
  def fromWireName(wireName: String): Option[MatchStatus] =
    MatchStatus.values.find(_.wireName == wireName)

final case class MatchBatch(
    gameId: String,
    gameVersion: String,
    matchId: String,
    status: MatchStatus,
    events: List[Event]
)

final case class DetectedPattern(
    name: String,
    playerId: String,
    startEventId: String,
    endEventId: String,
    details: Map[String, Double]
)

final case class PlayerAnalysis(
    playerId: String,
    eventCount: Int,
    metrics: Map[String, Double]
)

final case class MatchAnalysis(
    matchId: String,
    gameId: String,
    status: MatchStatus,
    winnerPlayerId: Option[String],
    durationSeconds: Double,
    totalEvents: Int,
    eventCountsByType: Map[String, Int],
    eventCountsByAction: Map[String, Int],
    matchMetrics: Map[String, Double],
    players: List[PlayerAnalysis],
    patterns: List[DetectedPattern]
)

final case class GameInsights(
    matchMetrics: Map[String, Double],
    playerMetrics: Map[String, Map[String, Double]],
    patterns: List[DetectedPattern]
)

object GameInsights:
  val empty: GameInsights = GameInsights(Map.empty, Map.empty, Nil)

final case class PatternOccurrence(matchId: String, gameId: String, pattern: DetectedPattern)

final case class MatchSummary(
    matchId: String,
    gameId: String,
    status: MatchStatus,
    totalEvents: Int,
    durationSeconds: Double,
    patternCount: Int
)

final case class PlayerAggregate(playerId: String, matchIds: List[String], aggregates: Map[String, Double])

final case class GameAggregate(gameId: String, aggregates: Map[String, Double])
