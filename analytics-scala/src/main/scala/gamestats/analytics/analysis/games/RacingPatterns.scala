package gamestats.analytics.analysis.games

import gamestats.analytics.domain.*

object RacingPatterns:

  private val minimumImprovingLaps = 3
  private val comebackWorstPosition = 4.0

  private final case class TimedLap(playerId: String, event: Event, lapTimeMs: Double)

  def detect(events: List[Event], result: Option[MatchResult]): List[DetectedPattern] =
    improvingLapStreaks(events) ++ raceComeback(events, result)

  private def improvingLapStreaks(events: List[Event]): List[DetectedPattern] =
    val lapsByPlayer = events.flatMap(timedLapOf).groupBy(_.playerId)
    lapsByPlayer.toList.flatMap { (playerId, laps) =>
      strictlyImprovingRuns(laps).filter(_.size >= minimumImprovingLaps).map(run => streakPattern(playerId, run))
    }

  private def timedLapOf(event: Event): Option[TimedLap] =
    Option.when(event.isAction("LAP_COMPLETED"))(event).flatMap { lap =>
      lap.playerId.zip(lap.data.number("lapTimeMs")).map((playerId, lapTimeMs) => TimedLap(playerId, lap, lapTimeMs))
    }

  private def strictlyImprovingRuns(laps: List[TimedLap]): List[List[TimedLap]] =
    laps
      .foldLeft(List.empty[List[TimedLap]]) {
        case ((currentRun @ (latestLap :: _)) :: finishedRuns, lap) if lap.lapTimeMs < latestLap.lapTimeMs =>
          (lap :: currentRun) :: finishedRuns
        case (runs, lap) => List(lap) :: runs
      }
      .map(_.reverse)
      .reverse

  private def streakPattern(playerId: String, run: List[TimedLap]): DetectedPattern =
    DetectedPattern(
      name = "IMPROVING_LAP_STREAK",
      playerId = playerId,
      startEventId = run.head.event.eventId,
      endEventId = run.last.event.eventId,
      details = Map(
        "streakLength" -> run.size.toDouble,
        "firstLapMs" -> run.head.lapTimeMs,
        "lastLapMs" -> run.last.lapTimeMs
      )
    )

  private def raceComeback(events: List[Event], result: Option[MatchResult]): List[DetectedPattern] =
    for
      finished <- result.toList
      winner <- finished.winnerPlayerId.toList
      worstLap <- worstLapOf(winner, events).toList
      (lapEvent, worstPosition) = worstLap
      if worstPosition >= comebackWorstPosition
    yield DetectedPattern(
      name = "RACE_COMEBACK",
      playerId = winner,
      startEventId = lapEvent.eventId,
      endEventId = finished.finishEventId,
      details = Statistics.definedOnly(
        "worstPosition" -> Some(worstPosition),
        "worstLap" -> lapEvent.data.number("lap"),
        "finalPosition" -> Some(1.0)
      )
    )

  private def worstLapOf(playerId: String, events: List[Event]): Option[(Event, Double)] =
    events
      .filter(event => event.isAction("LAP_COMPLETED") && event.playerId.contains(playerId))
      .flatMap(lap => lap.data.number("position").map(position => lap -> position))
      .maxByOption(_._2)
