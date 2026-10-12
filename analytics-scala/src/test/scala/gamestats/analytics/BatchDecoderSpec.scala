package gamestats.analytics

import gamestats.analytics.decoding.BatchDecoder
import gamestats.analytics.domain.*

class BatchDecoderSpec extends munit.FunSuite:

  private val minimalEvent =
    """{"eventId":"e1","timestamp":"2026-10-12T18:00:00Z","gameId":"g","gameVersion":"1","matchId":"m1","type":"MATCH_STARTED"}"""

  private def batchWith(eventsJson: String, status: String = "COMPLETED", matchId: String = "m1"): String =
    s"""{"gameId":"g","gameVersion":"1","matchId":"$matchId","status":"$status","events":[$eventsJson]}"""

  TestSupport.exampleMatches.foreach { (game, name) =>
    test(s"decodes the example match $game $name") {
      val decoded = BatchDecoder.decode(TestSupport.readBatchJson(game, name))
      assert(decoded.isRight, clue = decoded)
      assertEquals(decoded.toOption.get.gameId, game)
      assert(decoded.toOption.get.events.nonEmpty)
    }
  }

  test("a missing data field becomes an empty object") {
    val decoded = BatchDecoder.decode(batchWith(minimalEvent)).toOption.get
    assertEquals(decoded.events.head.data, EventData.empty)
  }

  test("timestamps with an offset are converted to the same instant") {
    val offsetEvent = minimalEvent.replace("2026-10-12T18:00:00Z", "2026-10-12T12:00:00-06:00")
    val decoded = BatchDecoder.decode(batchWith(offsetEvent)).toOption.get
    assertEquals(decoded.events.head.timestamp.toString, "2026-10-12T18:00:00Z")
  }

  test("incomplete status is decoded") {
    assertEquals(BatchDecoder.decode(batchWith(minimalEvent, "INCOMPLETE")).toOption.get.status, MatchStatus.Incomplete)
  }

  test("an unknown game action is accepted") {
    val unknownAction = minimalEvent.replace("MATCH_STARTED", "GAME_ACTION").replace("}", ""","playerId":"p1","action":"NEVER_SEEN_BEFORE"}""")
    assert(BatchDecoder.decode(batchWith(unknownAction)).isRight)
  }

  test("invalid inputs are rejected with a reason") {
    val invalidInputs = Map(
      "not json" -> "this is not json",
      "not an object" -> "[]",
      "missing matchId" -> """{"gameId":"g","gameVersion":"1","status":"COMPLETED","events":[]}""",
      "unknown status" -> batchWith(minimalEvent, "FINISHED"),
      "events not an array" -> """{"gameId":"g","gameVersion":"1","matchId":"m1","status":"COMPLETED","events":{}}""",
      "bad timestamp" -> batchWith(minimalEvent.replace("2026-10-12T18:00:00Z", "yesterday")),
      "unknown type" -> batchWith(minimalEvent.replace("MATCH_STARTED", "SOMETHING")),
      "missing eventId" -> batchWith(minimalEvent.replace(""""eventId":"e1",""", "")),
      "data not an object" -> batchWith(minimalEvent.replace("}", ""","data":5}""")),
      "event of another match" -> batchWith(minimalEvent, matchId = "m2")
    )
    invalidInputs.foreach { (description, input) =>
      assert(BatchDecoder.decode(input).isLeft, clue = description)
    }
  }

  test("the reason names the position of the failing event") {
    val reason = BatchDecoder.decode(batchWith(minimalEvent + "," + minimalEvent.replace("e1", ""))).left.toOption.get
    assert(reason.startsWith("events(1)"), clue = reason)
  }
