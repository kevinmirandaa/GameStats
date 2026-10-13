package gamestats.analytics

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.InetSocketAddress

import io.undertow.Undertow

import gamestats.analytics.analysis.GameAnalyzerRegistry
import gamestats.analytics.http.AnalyticsRoutes
import gamestats.analytics.service.AnalyticsService
import gamestats.analytics.storage.InMemoryAnalysisStore
import gamestats.analytics.TestSupport.*

class AnalyticsApiSpec extends munit.FunSuite:

  private case class Answer(status: Int, body: ujson.Value)

  private val server: Undertow =
    val service = AnalyticsService(GameAnalyzerRegistry(List(ToyAnalyzer)), InMemoryAnalysisStore())
    val application = new cask.Main:
      val allRoutes: Seq[cask.Routes] = Seq(AnalyticsRoutes(service))
    val undertow = Undertow.builder.addHttpListener(0, "localhost").setHandler(application.defaultHandler).build
    undertow.start()
    undertow

  private val baseUrl: String =
    val port = server.getListenerInfo.get(0).getAddress.asInstanceOf[InetSocketAddress].getPort
    s"http://localhost:$port"

  override def afterAll(): Unit = server.stop()

  private val client = HttpClient.newHttpClient()

  private def get(path: String): Answer =
    send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build())

  private def post(path: String, body: String): Answer =
    send(HttpRequest.newBuilder(URI.create(baseUrl + path)).POST(HttpRequest.BodyPublishers.ofString(body)).build())

  private def send(request: HttpRequest): Answer =
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    Answer(response.statusCode, ujson.read(response.body()))

  private def toyBatchJson(matchId: String, winner: String, points: Int, scorer: String = "p1"): String =
    s"""{"gameId":"toy","gameVersion":"1.0","matchId":"$matchId","status":"COMPLETED","events":[
      {"eventId":"$matchId-1","timestamp":"2026-10-12T18:00:00Z","gameId":"toy","gameVersion":"1.0","matchId":"$matchId","playerId":"$scorer","type":"GAME_ACTION","action":"SCORE","data":{"points":$points}},
      {"eventId":"$matchId-2","timestamp":"2026-10-12T18:00:30Z","gameId":"toy","gameVersion":"1.0","matchId":"$matchId","type":"MATCH_FINISHED","data":{"winnerPlayerId":"$winner"}}]}"""

  test("the root path serves the web dashboard") {
    val response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/")).GET().build(), HttpResponse.BodyHandlers.ofString())
    assertEquals(response.statusCode, 200)
    assert(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"))
    assert(response.body().contains("GameStats"))
    assert(response.body().contains("/stats/matches"))
  }

  test("health answers UP") {
    val answer = get("/health")
    assertEquals(answer.status, 200)
    assertEquals(answer.body("status").str, "UP")
  }

  test("a posted batch is analyzed, returned and stored") {
    val analyzed = post("/analyze", toyBatchJson("api-match-1", "p1", 7))
    assertEquals(analyzed.status, 200)
    assertEquals(analyzed.body("matchId").str, "api-match-1")
    assertEquals(analyzed.body("winnerPlayerId").str, "p1")
    assertEquals(analyzed.body("matchMetrics")("totalPoints").num, 7.0)

    val fetched = get("/stats/matches/api-match-1")
    assertEquals(fetched.status, 200)
    assertEquals(fetched.body, analyzed.body)
  }

  test("an invalid batch is answered with 400 and a reason") {
    val answer = post("/analyze", """{"matchId":"x"}""")
    assertEquals(answer.status, 400)
    assert(answer.body("error").str.nonEmpty)
  }

  test("an unknown match, player or game is answered with 404") {
    assertEquals(get("/stats/matches/none").status, 404)
    assertEquals(get("/stats/players/none").status, 404)
    assertEquals(get("/stats/games/none").status, 404)
  }

  test("the example racing match flows through the real api") {
    val answer = post("/analyze", readBatchJson("racing", "patterns-triggered"))
    assertEquals(answer.status, 200)
    assertEquals(answer.body("totalEvents").num.toInt, 30)
    assertEquals(answer.body("players").arr.size, 4)
  }

  test("player and game aggregates and match lists reflect every analyzed match") {
    val matchesBefore = get("/stats/matches?gameId=toy").body.arr.size
    post("/analyze", toyBatchJson("api-aggregate-1", "scorer-a", 5, "scorer-a"))
    post("/analyze", toyBatchJson("api-aggregate-2", "scorer-a", 9, "scorer-a"))
    post("/analyze", toyBatchJson("api-aggregate-3", "scorer-b", 2, "scorer-a"))

    val playerAnswer = get("/stats/players/scorer-a")
    assertEquals(playerAnswer.status, 200)
    assertEquals(playerAnswer.body("aggregates")("matchesPlayed").num, 3.0)
    assertEquals(playerAnswer.body("aggregates")("wins").num, 2.0)
    assertEquals(playerAnswer.body("aggregates")("min_points").num, 2.0)
    assertEquals(playerAnswer.body("aggregates")("max_points").num, 9.0)

    val matchesAfter = get("/stats/matches?gameId=toy").body.arr
    assertEquals(matchesAfter.size, matchesBefore + 3)
    assert(matchesAfter.forall(_("gameId").str == "toy"))

    val gameAnswer = get("/stats/games/toy")
    assertEquals(gameAnswer.body("aggregates")("matchesAnalyzed").num, (matchesBefore + 3).toDouble)
    assert(gameAnswer.body("aggregates")("patternCount_FIRST_SCORE").num >= 3.0)
  }

  test("patterns can be filtered by game and by type") {
    val allToyPatterns = get("/patterns?gameId=toy").body.arr
    assert(allToyPatterns.nonEmpty)
    assertEquals(get("/patterns?gameId=toy&type=FIRST_SCORE").body.arr.size, allToyPatterns.size)
    assertEquals(get("/patterns?type=NOT_A_PATTERN").body.arr.size, 0)
  }
