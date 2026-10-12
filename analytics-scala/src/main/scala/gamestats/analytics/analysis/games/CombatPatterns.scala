package gamestats.analytics.analysis.games

import gamestats.analytics.domain.*

object CombatPatterns:

  private val minimumStreakLength = 3
  private val comebackMinimumDeficit = 3

  private final case class Deficit(size: Int, event: Event)

  def detect(events: List[Event], result: Option[MatchResult]): List[DetectedPattern] =
    val eliminations = Elimination.allIn(events)
    killStreaks(eliminations) ++ scoreComeback(eliminations, result)

  private def killStreaks(eliminations: List[Elimination]): List[DetectedPattern] =
    val (openStreaks, closedStreaks) =
      eliminations.foldLeft((Map.empty[String, List[Event]], List.empty[(String, List[Event])])) {
        case ((open, closed), elimination) =>
          val interruptedStreak = open.get(elimination.victimId).map(streak => elimination.victimId -> streak).toList
          val killerStreak = elimination.event :: open.getOrElse(elimination.killerId, Nil)
          val withoutVictim = open.removed(elimination.victimId)
          (withoutVictim.updated(elimination.killerId, killerStreak), closed ++ interruptedStreak)
      }
    (closedStreaks ++ openStreaks.toList)
      .filter((_, streak) => streak.size >= minimumStreakLength)
      .map((playerId, streak) => streakPattern(playerId, streak.reverse))

  private def streakPattern(playerId: String, streak: List[Event]): DetectedPattern =
    DetectedPattern(
      name = "KILL_STREAK",
      playerId = playerId,
      startEventId = streak.head.eventId,
      endEventId = streak.last.eventId,
      details = Map("streakLength" -> streak.size.toDouble)
    )

  private def scoreComeback(eliminations: List[Elimination], result: Option[MatchResult]): List[DetectedPattern] =
    for
      finished <- result.toList
      winner <- finished.winnerPlayerId.toList
      deepest <- deepestDeficitOf(winner, eliminations).toList
      if deepest.size >= comebackMinimumDeficit
    yield DetectedPattern(
      name = "SCORE_COMEBACK",
      playerId = winner,
      startEventId = deepest.event.eventId,
      endEventId = finished.finishEventId,
      details = Map(
        "maxDeficit" -> deepest.size.toDouble,
        "finalEliminations" -> eliminations.count(_.killerId == winner).toDouble
      )
    )

  private def deepestDeficitOf(winner: String, eliminations: List[Elimination]): Option[Deficit] =
    val (_, deepest) = eliminations.foldLeft((Map.empty[String, Int], Option.empty[Deficit])) {
      case ((scores, deepestSoFar), elimination) =>
        val updatedScores = scores.updated(elimination.killerId, scores.getOrElse(elimination.killerId, 0) + 1)
        val leaderScore = updatedScores.removed(winner).values.maxOption.getOrElse(0)
        val deficitSize = leaderScore - updatedScores.getOrElse(winner, 0)
        val isDeeper = deficitSize > deepestSoFar.map(_.size).getOrElse(0)
        (updatedScores, if isDeeper then Some(Deficit(deficitSize, elimination.event)) else deepestSoFar)
    }
    deepest
