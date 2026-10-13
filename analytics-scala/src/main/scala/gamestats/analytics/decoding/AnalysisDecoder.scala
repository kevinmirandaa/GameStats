package gamestats.analytics.decoding

import gamestats.analytics.decoding.JsonReading.*
import gamestats.analytics.domain.*

object AnalysisDecoder:

  def decode(rawJson: String): Either[String, MatchAnalysis] =
    for
      root <- parseObject(rawJson, "analysis")
      matchId <- requiredText(root, "matchId")
      gameId <- requiredText(root, "gameId")
      status <- decodeStatus(root)
      durationSeconds <- requiredNumber(root, "durationSeconds")
      totalEvents <- requiredNumber(root, "totalEvents")
      countsByType <- requiredNumberMap(root, "eventCountsByType")
      countsByAction <- requiredNumberMap(root, "eventCountsByAction")
      matchMetrics <- requiredNumberMap(root, "matchMetrics")
      playerValues <- requiredArray(root, "players")
      players <- sequence(playerValues.map(decodePlayer))
      patternValues <- requiredArray(root, "patterns")
      patterns <- sequence(patternValues.map(decodePattern))
    yield MatchAnalysis(
      matchId = matchId,
      gameId = gameId,
      status = status,
      winnerPlayerId = optionalText(root, "winnerPlayerId"),
      durationSeconds = durationSeconds,
      totalEvents = totalEvents.toInt,
      eventCountsByType = countsByType.view.mapValues(_.toInt).toMap,
      eventCountsByAction = countsByAction.view.mapValues(_.toInt).toMap,
      matchMetrics = matchMetrics,
      players = players,
      patterns = patterns
    )

  private def decodeStatus(root: ujson.Obj): Either[String, MatchStatus] =
    requiredText(root, "status").flatMap { statusText =>
      MatchStatus.fromWireName(statusText).toRight(s"status '$statusText' is not COMPLETED or INCOMPLETE")
    }

  private def decodePlayer(value: ujson.Value): Either[String, PlayerAnalysis] =
    for
      fields <- asObject(value, "player")
      playerId <- requiredText(fields, "playerId")
      eventCount <- requiredNumber(fields, "eventCount")
      metrics <- requiredNumberMap(fields, "metrics")
    yield PlayerAnalysis(playerId, eventCount.toInt, metrics)

  private def decodePattern(value: ujson.Value): Either[String, DetectedPattern] =
    for
      fields <- asObject(value, "pattern")
      name <- requiredText(fields, "name")
      playerId <- requiredText(fields, "playerId")
      startEventId <- requiredText(fields, "startEventId")
      endEventId <- requiredText(fields, "endEventId")
      details <- requiredNumberMap(fields, "details")
    yield DetectedPattern(name, playerId, startEventId, endEventId, details)
