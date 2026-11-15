package gameconsole.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gameconsole.server.config.ConsoleConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsoleApplicationTest {

    @TempDir
    Path plugins;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();
    private FakeHttpService ingest;
    private FakeHttpService analytics;
    private FakeHttpService prolog;
    private ConsoleApplication application;
    private String base;

    @BeforeEach
    void startEverything() throws IOException {
        DynamicPluginJar.create(plugins, "echo.jar");
        ingest = new FakeHttpService();
        ingest.respondWith(request -> request.path().equals("/metrics")
                ? new FakeHttpService.Reply(200, "{\"activeMatches\":2,\"activeMatchesByGame\":{\"echo\":2},\"eventsAccepted\":9}")
                : new FakeHttpService.Reply(202, "{}"));
        analytics = new FakeHttpService();
        analytics.respondWith(request -> request.path().equals("/stats/overview")
                ? new FakeHttpService.Reply(200, "{\"totalMatches\":4,\"games\":[]}")
                : request.path().startsWith("/stats/games/missing") ? new FakeHttpService.Reply(404, "{\"error\":\"sin partidas\"}")
                : new FakeHttpService.Reply(200, "[]"));
        prolog = new FakeHttpService();
        prolog.respondWith(request -> new FakeHttpService.Reply(200, "{\"status\":\"UP\"}"));
        ConsoleConfiguration configuration = new ConsoleConfiguration(
                0, plugins, ingest.url(), analytics.url(), prolog.url(),
                Duration.ofMinutes(20), Duration.ofMinutes(30), 200, 3, Duration.ofMillis(10), Duration.ofMillis(40), 4);
        application = new ConsoleApplication(configuration);
        base = "http://127.0.0.1:" + application.start();
    }

    @AfterEach
    void stopEverything() {
        application.stop();
        ingest.close();
        analytics.close();
        prolog.close();
    }

    private record Reply(int status, JsonNode body) {
    }

    private Reply call(String method, String path, String json) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path));
        HttpRequest.BodyPublisher body = json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json);
        request.header("Content-Type", "application/json").method(method, body);
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        JsonNode parsed;
        try {
            parsed = response.body().isBlank() ? null : mapper.readTree(response.body());
        } catch (IOException notJson) {
            parsed = mapper.getNodeFactory().textNode(response.body());
        }
        return new Reply(response.statusCode(), parsed);
    }

    private JsonNode createEchoSession(String player) throws Exception {
        Reply created = call("POST", "/api/sessions", "{\"gameId\":\"echo\",\"mode\":\"SOLO\",\"players\":[\"" + player + "\"]}");
        assertEquals(201, created.status());
        return created.body();
    }

    private List<JsonNode> deliveredEvents() throws IOException {
        List<JsonNode> events = new ArrayList<>();
        for (FakeHttpService.Received request : ingest.received()) {
            if (request.path().equals("/events")) {
                events.add(mapper.readTree(request.body()));
            }
        }
        return events;
    }

    @Test
    void healthReportsTheLoadedGamesAndPrologAvailability() throws Exception {
        Reply health = call("GET", "/api/health", null);

        assertEquals(200, health.status());
        assertEquals("UP", health.body().get("status").asText());
        assertEquals(1, health.body().get("games").asInt());
        assertTrue(health.body().get("prologAvailable").asBoolean());
    }

    @Test
    void healthReportsPrologAsUnavailableWhenItDoesNotAnswer() throws Exception {
        prolog.respondWith(request -> new FakeHttpService.Reply(500, "{}"));

        assertFalse(call("GET", "/api/health", null).body().get("prologAvailable").asBoolean());
    }

    @Test
    void theCatalogListsTheDynamicallyLoadedGameWithItsModes() throws Exception {
        Reply games = call("GET", "/api/games", null);

        assertEquals(1, games.body().size());
        assertEquals("echo", games.body().get(0).get("gameId").asText());
        assertEquals("SOLO", games.body().get(0).get("modes").get(0).get("id").asText());
        Reply plugins = call("GET", "/api/plugins", null);
        assertEquals("echo.jar", plugins.body().get("loaded").get(0).get("jar").asText());
    }

    @Test
    void playingASessionDeliversItsEventsToGameStatsInOrder() throws Exception {
        JsonNode view = createEchoSession("Ana");
        String matchId = view.get("matchId").asText();

        Reply action = call("POST", "/api/sessions/" + matchId + "/actions", "{\"playerId\":\"player-ana\",\"action\":{\"finish\":true}}");

        assertEquals(200, action.status());
        assertTrue(action.body().get("accepted").asBoolean());
        assertTrue(action.body().get("view").get("finished").asBoolean());
        awaitDelivered(7);
        List<String> kinds = new ArrayList<>();
        for (JsonNode event : deliveredEvents()) {
            assertEquals(matchId, event.get("matchId").asText());
            assertEquals("1.0", event.get("schemaVersion").asText());
            kinds.add(event.get("type").asText() + (event.has("action") ? ":" + event.get("action").asText() : ""));
        }
        assertEquals(List.of("MATCH_STARTED", "PLAYER_JOINED", "GAME_ACTION:TOUCH", "GAME_ACTION:PLAYER_WON", "PLAYER_LEFT", "MATCH_FINISHED"), kinds);
    }

    private void awaitDelivered(int atLeast) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (application.publisher().statistics().delivered() < atLeast - 1 && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        application.publisher().awaitDelivery(Duration.ofSeconds(5));
    }

    @Test
    void rejectedActionsAnswer422WithTheReasonAndTheCurrentView() throws Exception {
        String matchId = createEchoSession("Ana").get("matchId").asText();

        Reply action = call("POST", "/api/sessions/" + matchId + "/actions", "{\"playerId\":\"player-zoe\",\"action\":{}}");

        assertEquals(422, action.status());
        assertFalse(action.body().get("accepted").asBoolean());
        assertFalse(action.body().get("message").asText().isEmpty());
        assertEquals(matchId, action.body().get("view").get("matchId").asText());
    }

    @Test
    void unknownSessionsAndGamesAreReportedCleanly() throws Exception {
        assertEquals(404, call("GET", "/api/sessions/echo-nope", null).status());
        assertEquals(404, call("POST", "/api/sessions/echo-nope/actions", "{\"playerId\":\"x\",\"action\":{}}").status());
        assertEquals(404, call("DELETE", "/api/sessions/echo-nope", null).status());
        Reply invalid = call("POST", "/api/sessions", "{\"gameId\":\"nope\",\"mode\":\"SOLO\",\"players\":[\"Ana\"]}");
        assertEquals(400, invalid.status());
        assertTrue(invalid.body().get("error").asText().contains("nope"));
    }

    @Test
    void abandoningASessionFinishesItAndAnnouncesTheEndToGameStats() throws Exception {
        String matchId = createEchoSession("Ana").get("matchId").asText();

        assertEquals(204, call("DELETE", "/api/sessions/" + matchId, null).status());

        assertTrue(call("GET", "/api/sessions/" + matchId, null).body().get("finished").asBoolean());
        application.publisher().awaitDelivery(Duration.ofSeconds(5));
        JsonNode last = deliveredEvents().get(deliveredEvents().size() - 1);
        assertEquals("MATCH_FINISHED", last.get("type").asText());
        assertEquals("ABANDONED", last.get("data").get("result").asText());
    }

    @Test
    void theSessionListShowsEveryOpenMatch() throws Exception {
        createEchoSession("Ana");
        createEchoSession("Luis");

        Reply list = call("GET", "/api/sessions", null);

        assertEquals(2, list.body().size());
        assertFalse(list.body().get(0).get("finished").asBoolean());
    }

    @Test
    void manyConcurrentSessionsKeepTheirEventsSeparatedInGameStats() throws Exception {
        int sessions = 20;
        Map<String, Integer> touchesByMatch = new ConcurrentHashMap<>();
        List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < sessions; index++) {
            int number = index;
            threads.add(Thread.ofVirtual().start(() -> {
                try {
                    String matchId = createEchoSession("Jugador" + number).get("matchId").asText();
                    String playerId = "player-jugador" + number;
                    int touches = 2 + number % 3;
                    for (int touch = 0; touch < touches; touch++) {
                        call("POST", "/api/sessions/" + matchId + "/actions", "{\"playerId\":\"" + playerId + "\",\"action\":{}}");
                    }
                    call("POST", "/api/sessions/" + matchId + "/actions", "{\"playerId\":\"" + playerId + "\",\"action\":{\"finish\":true}}");
                    touchesByMatch.put(matchId, touches + 1);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }));
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(application.publisher().awaitDelivery(Duration.ofSeconds(20)));
        Map<String, Map<String, Integer>> countsByMatch = new HashMap<>();
        for (JsonNode event : deliveredEvents()) {
            String matchId = event.get("matchId").asText();
            String label = event.get("type").asText() + (event.has("action") ? ":" + event.get("action").asText() : "");
            countsByMatch.computeIfAbsent(matchId, key -> new HashMap<>()).merge(label, 1, Integer::sum);
            assertTrue(event.get("playerId") == null || event.get("playerId").asText().startsWith("player-jugador"));
        }
        assertEquals(sessions, countsByMatch.size());
        for (Map.Entry<String, Integer> expected : touchesByMatch.entrySet()) {
            Map<String, Integer> counts = countsByMatch.get(expected.getKey());
            assertEquals(1, counts.get("MATCH_STARTED"));
            assertEquals(1, counts.get("MATCH_FINISHED"));
            assertEquals(expected.getValue(), counts.get("GAME_ACTION:TOUCH"));
        }
        assertEquals(0, application.publisher().statistics().failed());
    }

    @Test
    void theStatisticsEndpointsRelayGameStatsAnswersWithoutChangingThem() throws Exception {
        Reply overview = call("GET", "/api/stats/overview", null);
        Reply live = call("GET", "/api/stats/live", null);
        Reply missing = call("GET", "/api/stats/games/missing", null);

        assertEquals(200, overview.status());
        assertEquals(4, overview.body().get("totalMatches").asInt());
        assertEquals(2, live.body().get("activeMatchesByGame").get("echo").asInt());
        assertEquals(404, missing.status());
        assertEquals("sin partidas", missing.body().get("error").asText());
    }

    @Test
    void theMatchListRelaysTheGameFilterToGameStats() throws Exception {
        call("GET", "/api/stats/matches?gameId=echo", null);

        boolean relayed = analytics.received().stream().anyMatch(request -> request.path().equals("/stats/matches") && "gameId=echo".equals(request.query()));
        assertTrue(relayed);
    }

    @Test
    void whenGameStatsIsDownTheStatisticsAnswer502ButGamesKeepWorking() throws Exception {
        analytics.close();
        ingest.respondWith(request -> new FakeHttpService.Reply(503, "{}"));

        Reply overview = call("GET", "/api/stats/overview", null);
        JsonNode view = createEchoSession("Ana");
        Reply action = call("POST", "/api/sessions/" + view.get("matchId").asText() + "/actions", "{\"playerId\":\"player-ana\",\"action\":{}}");

        assertEquals(502, overview.status());
        assertEquals(200, action.status());
    }

    @Test
    void theDeliveryEndpointShowsTheEventCounters() throws Exception {
        createEchoSession("Ana");
        application.publisher().awaitDelivery(Duration.ofSeconds(5));

        Reply delivery = call("GET", "/api/delivery", null);

        assertTrue(delivery.body().get("statistics").get("delivered").asInt() >= 2);
        assertEquals(0, delivery.body().get("statistics").get("failed").asInt());
    }

    @Test
    void theRecentActivityFeedListsTheLatestEventsFirst() throws Exception {
        String matchId = createEchoSession("Ana").get("matchId").asText();
        call("POST", "/api/sessions/" + matchId + "/actions", "{\"playerId\":\"player-ana\",\"action\":{}}");

        Reply activity = call("GET", "/api/activity?limit=2", null);

        assertEquals(2, activity.body().size());
        assertEquals("TOUCH", activity.body().get(0).get("action").asText());
    }

    @Test
    void malformedOrIncompleteBodiesAnswer400InsteadOfAServerError() throws Exception {
        String[] bodies = {"{bad", "", "null", "[1,2]", "{\"gameId\":\"echo\",\"mode\":\"SOLO\",\"players\":\"x\"}", "{\"gameId\":\"echo\",\"mode\":\"SOLO\",\"players\":[\"A\"],\"options\":{\"difficulty\":null}}"};
        int created = 0;
        for (String body : bodies) {
            int status = call("POST", "/api/sessions", body).status();
            if (status == 201) {
                created++;
            } else {
                assertEquals(400, status, "cuerpo: " + body + " respuesta: " + call("POST", "/api/sessions", body).body());
            }
        }
        assertEquals(1, created);
        String matchId = createEchoSession("Ana").get("matchId").asText();
        assertEquals(400, call("POST", "/api/sessions/" + matchId + "/actions", "{bad").status());
        assertEquals(400, call("POST", "/api/sessions/" + matchId + "/actions", "{\"playerId\":\"player-ana\",\"action\":[1]}").status());
    }

    @Test
    void identifiersWithSpacesInStatisticsPathsDoNotCauseAServerError() throws Exception {
        assertTrue(call("GET", "/api/stats/games/mi%20juego", null).status() < 500);
        assertTrue(call("GET", "/api/stats/players/ana%20maria", null).status() < 500);
        assertTrue(analytics.received().stream().anyMatch(request -> request.path().equals("/stats/games/mi%20juego") || request.path().equals("/stats/games/mi juego")));
    }

    @Test
    void theWebConsoleIsServedAtTheRootPath() throws Exception {
        HttpResponse<String> page = client.send(HttpRequest.newBuilder(URI.create(base + "/")).GET().build(), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> script = client.send(HttpRequest.newBuilder(URI.create(base + "/app.js")).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("GameConsole"));
        assertEquals(200, script.statusCode());
        assertTrue(script.body().contains("/api/stats/overview"));
    }
}
