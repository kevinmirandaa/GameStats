package gamestats.analytics.encoding

import gamestats.analytics.domain.*

object AnalysisEncoder:

  private val decimalPlaces = 1000.0

  def matchAnalysis(analysis: MatchAnalysis): ujson.Value =
    ujson.Obj.from(
      List("matchId" -> ujson.Str(analysis.matchId), "gameId" -> ujson.Str(analysis.gameId), "status" -> ujson.Str(analysis.status.wireName)) ++
        analysis.winnerPlayerId.map(winner => "winnerPlayerId" -> ujson.Str(winner)).toList ++
        List(
          "durationSeconds" -> number(analysis.durationSeconds),
          "totalEvents" -> ujson.Num(analysis.totalEvents),
          "eventCountsByType" -> counts(analysis.eventCountsByType),
          "eventCountsByAction" -> counts(analysis.eventCountsByAction),
          "matchMetrics" -> metrics(analysis.matchMetrics),
          "players" -> ujson.Arr.from(analysis.players.map(player)),
          "patterns" -> ujson.Arr.from(analysis.patterns.map(pattern))
        )
    )

  def matchSummary(summary: MatchSummary): ujson.Value =
    ujson.Obj(
      "matchId" -> summary.matchId,
      "gameId" -> summary.gameId,
      "status" -> summary.status.wireName,
      "totalEvents" -> summary.totalEvents,
      "durationSeconds" -> number(summary.durationSeconds),
      "patternCount" -> summary.patternCount
    )

  def playerAggregate(aggregate: PlayerAggregate): ujson.Value =
    ujson.Obj(
      "playerId" -> aggregate.playerId,
      "matchIds" -> ujson.Arr.from(aggregate.matchIds.map(ujson.Str(_))),
      "aggregates" -> metrics(aggregate.aggregates)
    )

  def gameAggregate(aggregate: GameAggregate): ujson.Value =
    ujson.Obj("gameId" -> aggregate.gameId, "aggregates" -> metrics(aggregate.aggregates))

  def patternOccurrence(occurrence: PatternOccurrence): ujson.Value =
    ujson.Obj("matchId" -> occurrence.matchId, "gameId" -> occurrence.gameId, "pattern" -> pattern(occurrence.pattern))

  def error(message: String): ujson.Value = ujson.Obj("error" -> message)

  private def player(analysis: PlayerAnalysis): ujson.Value =
    ujson.Obj(
      "playerId" -> analysis.playerId,
      "eventCount" -> analysis.eventCount,
      "metrics" -> metrics(analysis.metrics)
    )

  private def pattern(detected: DetectedPattern): ujson.Value =
    ujson.Obj(
      "name" -> detected.name,
      "playerId" -> detected.playerId,
      "startEventId" -> detected.startEventId,
      "endEventId" -> detected.endEventId,
      "details" -> metrics(detected.details)
    )

  private def metrics(values: Map[String, Double]): ujson.Obj =
    ujson.Obj.from(values.toList.sortBy(_._1).map((name, value) => name -> number(value)))

  private def counts(values: Map[String, Int]): ujson.Obj =
    ujson.Obj.from(values.toList.sortBy(_._1).map((name, count) => name -> ujson.Num(count)))

  private def number(value: Double): ujson.Num = ujson.Num(Math.round(value * decimalPlaces) / decimalPlaces)
