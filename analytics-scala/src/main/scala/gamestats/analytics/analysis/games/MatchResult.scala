package gamestats.analytics.analysis.games

import gamestats.analytics.domain.*

final case class MatchResult(finishEventId: String, winnerPlayerId: Option[String], ranking: List[String])

object MatchResult:

  def of(batch: MatchBatch): Option[MatchResult] =
    Option
      .when(batch.status == MatchStatus.Completed)(batch.events.findLast(_.eventType == EventType.MatchFinished))
      .flatten
      .map { finished =>
        MatchResult(
          finishEventId = finished.eventId,
          winnerPlayerId = finished.data.text("winnerPlayerId"),
          ranking = finished.data.texts("ranking")
        )
      }
