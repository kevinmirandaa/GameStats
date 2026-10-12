package gamestats.analytics.domain

import java.time.Instant

enum EventType(val wireName: String):
  case MatchStarted extends EventType("MATCH_STARTED")
  case MatchFinished extends EventType("MATCH_FINISHED")
  case PlayerJoined extends EventType("PLAYER_JOINED")
  case PlayerLeft extends EventType("PLAYER_LEFT")
  case GameAction extends EventType("GAME_ACTION")

object EventType:
  def fromWireName(wireName: String): Option[EventType] =
    EventType.values.find(_.wireName == wireName)

final case class EventData(json: ujson.Obj):

  def number(fieldName: String): Option[Double] =
    json.obj.get(fieldName).collect { case ujson.Num(value) => value }

  def text(fieldName: String): Option[String] =
    json.obj.get(fieldName).collect { case ujson.Str(value) => value }

  def boolean(fieldName: String): Option[Boolean] =
    json.obj.get(fieldName).collect {
      case ujson.True  => true
      case ujson.False => false
    }

  def texts(fieldName: String): List[String] =
    json.obj.get(fieldName) match
      case Some(ujson.Arr(items)) => items.toList.collect { case ujson.Str(value) => value }
      case _                      => Nil

object EventData:
  val empty: EventData = EventData(ujson.Obj())

final case class Event(
    eventId: String,
    timestamp: Instant,
    gameId: String,
    gameVersion: String,
    matchId: String,
    playerId: Option[String],
    eventType: EventType,
    action: Option[String],
    data: EventData
):

  def isAction(actionName: String): Boolean =
    eventType == EventType.GameAction && action.contains(actionName)
