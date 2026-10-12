package gamestats.analytics.analysis.games

import gamestats.analytics.domain.*

final case class Elimination(killerId: String, victimId: String, event: Event)

object Elimination:
  def allIn(events: List[Event]): List[Elimination] =
    events.filter(_.isAction("ELIMINATION")).flatMap { event =>
      event.playerId.zip(event.data.text("victimPlayerId")).map((killerId, victimId) => Elimination(killerId, victimId, event))
    }

object CombatMetrics:

  private val percentFactor = 100.0
  private val secondsPerMinute = 60.0

  def forPlayers(events: List[Event]): Map[String, Map[String, Double]] =
    val eliminations = Elimination.allIn(events)
    val playerIds = (events.flatMap(_.playerId) ++ eliminations.map(_.victimId)).distinct
    playerIds.map(playerId => playerId -> forPlayer(playerId, events, eliminations)).toMap

  def forMatch(events: List[Event], durationSeconds: Double): Map[String, Double] =
    val eliminations = Elimination.allIn(events)
    val distances = eliminations.flatMap(_.event.data.number("distanceMeters"))
    val damageAmounts = events.filter(_.isAction("DAMAGE_DEALT")).flatMap(_.data.number("amount"))
    Statistics.definedOnly(
      "totalEliminations" -> Some(eliminations.size.toDouble),
      "totalDamageDealt" -> Some(damageAmounts.sum),
      "eliminationsPerMinute" -> Option.when(durationSeconds > 0)(eliminations.size / (durationSeconds / secondsPerMinute)),
      "mostEliminationsByOnePlayer" -> Some(eliminations.groupBy(_.killerId).values.map(_.size).maxOption.getOrElse(0).toDouble),
      "averageEliminationDistanceMeters" -> Statistics.mean(distances)
    )

  private def forPlayer(playerId: String, events: List[Event], eliminations: List[Elimination]): Map[String, Double] =
    val playerEvents = events.filter(_.playerId.contains(playerId))
    val playerEliminations = eliminations.filter(_.killerId == playerId)
    val deaths = eliminations.count(_.victimId == playerId)
    val damageAmounts = playerEvents.filter(_.isAction("DAMAGE_DEALT")).flatMap(_.data.number("amount"))
    val shots = playerEvents.filter(_.isAction("SHOT_FIRED"))
    val shotsHit = shots.count(_.data.boolean("hit").contains(true))
    val damageTotal = damageAmounts.sum

    Statistics.definedOnly(
      "eliminations" -> Some(playerEliminations.size.toDouble),
      "deaths" -> Some(deaths.toDouble),
      "killDeathRatio" -> Some(playerEliminations.size.toDouble / math.max(deaths, 1)),
      "damageDealtTotal" -> Some(damageTotal),
      "damageDealtAverage" -> Statistics.mean(damageAmounts),
      "shotsFired" -> Some(shots.size.toDouble),
      "shotsHit" -> Some(shotsHit.toDouble),
      "accuracyPercent" -> Option.when(shots.nonEmpty)(shotsHit * percentFactor / shots.size),
      "damagePerElimination" -> Option.when(playerEliminations.nonEmpty)(damageTotal / playerEliminations.size),
      "longestEliminationDistanceMeters" -> playerEliminations.flatMap(_.event.data.number("distanceMeters")).maxOption
    )
