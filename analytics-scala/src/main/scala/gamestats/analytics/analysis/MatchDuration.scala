package gamestats.analytics.analysis

import java.time.Duration

import gamestats.analytics.domain.Event

object MatchDuration:

  private val millisecondsPerSecond = 1000.0

  def secondsOf(events: List[Event]): Double =
    (events.headOption, events.lastOption) match
      case (Some(first), Some(last)) => Duration.between(first.timestamp, last.timestamp).toMillis / millisecondsPerSecond
      case _                         => 0.0
