package gamestats.analytics.analysis.games

import gamestats.analytics.domain.*

object RacingMetrics:

  def forPlayers(events: List[Event], result: Option[MatchResult]): Map[String, Map[String, Double]] =
    val startPositions = startPositionsOf(events)
    events.flatMap(_.playerId).distinct.map { playerId =>
      playerId -> forPlayer(playerId, events.filter(_.playerId.contains(playerId)), startPositions.get(playerId), result)
    }.toMap

  def forMatch(events: List[Event], result: Option[MatchResult]): Map[String, Double] =
    val lapTimes = events.filter(_.isAction("LAP_COMPLETED")).flatMap(_.data.number("lapTimeMs"))
    val positionChanges = events.filter(_.isAction("POSITION_CHANGED"))
    Statistics.definedOnly(
      "totalLapsCompleted" -> Some(lapTimes.size.toDouble),
      "fastestLapMs" -> lapTimes.minOption,
      "averageLapMs" -> Statistics.mean(lapTimes),
      "penaltyCount" -> Some(events.count(_.isAction("PENALTY_APPLIED")).toDouble),
      "pitStopCount" -> Some(events.count(_.isAction("PIT_STOP")).toDouble),
      "positionChangesTotal" -> Some(positionChanges.size.toDouble),
      "leadChanges" -> Some(positionChanges.count(_.data.number("toPosition").contains(1.0)).toDouble),
      "finishedPlayers" -> Some(result.map(_.ranking.size).getOrElse(0).toDouble)
    )

  private def forPlayer(
      playerId: String,
      playerEvents: List[Event],
      startPosition: Option[Double],
      result: Option[MatchResult]
  ): Map[String, Double] =
    val lapTimes = playerEvents.filter(_.isAction("LAP_COMPLETED")).flatMap(_.data.number("lapTimeMs"))
    val topSpeeds = playerEvents.filter(_.isAction("LAP_COMPLETED")).flatMap(_.data.number("topSpeedKmh"))
    val penaltySeconds = playerEvents.filter(_.isAction("PENALTY_APPLIED")).flatMap(_.data.number("seconds")).sum
    val pitStopDurations = playerEvents.filter(_.isAction("PIT_STOP")).flatMap(_.data.number("durationMs"))
    val overtakes = playerEvents.count(isOvertake)
    val finalPosition = finalPositionOf(playerId, result)

    Statistics.definedOnly(
      "lapsCompleted" -> Some(lapTimes.size.toDouble),
      "bestLapMs" -> lapTimes.minOption,
      "averageLapMs" -> Statistics.mean(lapTimes),
      "lapTimeStdDevMs" -> Statistics.populationStandardDeviation(lapTimes),
      "topSpeedKmh" -> topSpeeds.maxOption,
      "penaltySecondsTotal" -> Some(penaltySeconds),
      "pitStopCount" -> Some(pitStopDurations.size.toDouble),
      "pitStopTotalMs" -> Some(pitStopDurations.sum),
      "overtakesMade" -> Some(overtakes.toDouble),
      "effectiveRaceTimeMs" -> Option.when(lapTimes.nonEmpty)(lapTimes.sum + penaltySeconds * 1000.0),
      "finalPosition" -> finalPosition,
      "positionsGained" -> startPosition.zip(finalPosition).map((start, finish) => start - finish)
    )

  private def isOvertake(event: Event): Boolean =
    event.isAction("POSITION_CHANGED") &&
      event.data.number("toPosition").zip(event.data.number("fromPosition")).exists((to, from) => to < from)

  private def finalPositionOf(playerId: String, result: Option[MatchResult]): Option[Double] =
    result.map(_.ranking.indexOf(playerId)).filter(_ >= 0).map(index => index + 1.0)

  private def startPositionsOf(events: List[Event]): Map[String, Double] =
    events
      .filter(_.eventType == EventType.PlayerJoined)
      .flatMap(joined => joined.playerId.zip(joined.data.number("startPosition")))
      .toMap
