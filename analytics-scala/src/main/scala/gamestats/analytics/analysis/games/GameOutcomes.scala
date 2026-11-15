package gamestats.analytics.analysis.games

import gamestats.analytics.domain.*

object GameOutcomes:

  private val noneYet = 0.0
  private val happened = 1.0

  def flag(condition: Boolean): Double = if condition then happened else noneYet

  def finishResult(events: List[Event]): Option[String] =
    events.findLast(_.eventType == EventType.MatchFinished).flatMap(_.data.text("result"))

  def abandoned(events: List[Event]): Double = flag(finishResult(events).contains("ABANDONED"))

  def drawn(events: List[Event]): Double = flag(finishResult(events).contains("DRAW"))

  def playerWon(events: List[Event], playerId: String): Double =
    flag(events.exists(event => event.playerId.contains(playerId) && event.isAction("PLAYER_WON")))

  def playerLost(events: List[Event], playerId: String): Double =
    flag(events.exists(event => event.playerId.contains(playerId) && event.isAction("PLAYER_LOST")))

  def playerDrew(events: List[Event], playerId: String): Double =
    flag(events.exists(event => event.playerId.contains(playerId) && event.isAction("PLAYER_DRAW")))
