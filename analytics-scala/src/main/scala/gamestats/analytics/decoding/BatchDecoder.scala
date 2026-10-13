package gamestats.analytics.decoding

import java.time.{Instant, OffsetDateTime}

import scala.util.Try

import gamestats.analytics.decoding.JsonReading.*
import gamestats.analytics.domain.*

object BatchDecoder:

  def decode(rawJson: String): Either[String, MatchBatch] =
    for
      root <- parseObject(rawJson, "batch")
      gameId <- requiredText(root, "gameId")
      gameVersion <- requiredText(root, "gameVersion")
      matchId <- requiredText(root, "matchId")
      status <- decodeStatus(root)
      eventObjects <- requiredArray(root, "events")
      events <- decodeEvents(eventObjects)
      _ <- ensureEventsBelongToMatch(events, matchId)
    yield MatchBatch(gameId, gameVersion, matchId, status, events)

  private def decodeStatus(root: ujson.Obj): Either[String, MatchStatus] =
    requiredText(root, "status").flatMap { statusText =>
      MatchStatus.fromWireName(statusText).toRight(s"status '$statusText' is not COMPLETED or INCOMPLETE")
    }

  private def decodeEvents(eventValues: List[ujson.Value]): Either[String, List[Event]] =
    val decodedEvents = eventValues.zipWithIndex.map { (value, position) =>
      decodeEvent(value).left.map(reason => s"events($position): $reason")
    }
    sequence(decodedEvents)

  private def decodeEvent(value: ujson.Value): Either[String, Event] =
    for
      fields <- asObject(value, "event")
      eventId <- requiredText(fields, "eventId")
      timestamp <- decodeTimestamp(fields)
      gameId <- requiredText(fields, "gameId")
      gameVersion <- requiredText(fields, "gameVersion")
      matchId <- requiredText(fields, "matchId")
      eventType <- decodeEventType(fields)
      data <- decodeData(fields)
    yield Event(
      eventId = eventId,
      timestamp = timestamp,
      gameId = gameId,
      gameVersion = gameVersion,
      matchId = matchId,
      playerId = optionalText(fields, "playerId"),
      eventType = eventType,
      action = optionalText(fields, "action"),
      data = data
    )

  private def decodeTimestamp(fields: ujson.Obj): Either[String, Instant] =
    requiredText(fields, "timestamp").flatMap { timestampText =>
      Try(OffsetDateTime.parse(timestampText).toInstant).toEither.left
        .map(_ => s"timestamp '$timestampText' is not an RFC 3339 date and time")
    }

  private def decodeEventType(fields: ujson.Obj): Either[String, EventType] =
    requiredText(fields, "type").flatMap { typeText =>
      EventType.fromWireName(typeText).toRight(s"type '$typeText' is not supported")
    }

  private def decodeData(fields: ujson.Obj): Either[String, EventData] =
    fields.obj.get("data") match
      case None                   => Right(EventData.empty)
      case Some(dataObject: ujson.Obj) => Right(EventData(dataObject))
      case Some(_)                => Left("data must be a JSON object")

  private def ensureEventsBelongToMatch(events: List[Event], matchId: String): Either[String, Unit] =
    events.find(_.matchId != matchId) match
      case Some(foreignEvent) => Left(s"event ${foreignEvent.eventId} belongs to match ${foreignEvent.matchId}, not $matchId")
      case None               => Right(())
