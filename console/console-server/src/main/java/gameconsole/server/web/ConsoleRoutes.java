package gameconsole.server.web;

import gameconsole.api.ActionResult;
import gameconsole.api.GameMode;
import gameconsole.api.GameModule;
import gameconsole.api.GameSession;
import gameconsole.server.activity.RecentActivityObserver;
import gameconsole.server.events.GameStatsPublisher;
import gameconsole.server.logic.PrologHttpEngine;
import gameconsole.server.plugins.GameCatalog;
import gameconsole.server.sessions.SessionManager;
import gameconsole.server.stats.GameStatsGateway;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConsoleRoutes {

    public record CreateSessionRequest(String gameId, String mode, List<String> players, Map<String, String> options) {
    }

    public record ActionRequest(String playerId, Map<String, Object> action) {
    }

    private final GameCatalog catalog;
    private final SessionManager sessions;
    private final GameStatsPublisher publisher;
    private final GameStatsGateway gateway;
    private final PrologHttpEngine prolog;
    private final RecentActivityObserver activity;

    public ConsoleRoutes(
            GameCatalog catalog,
            SessionManager sessions,
            GameStatsPublisher publisher,
            GameStatsGateway gateway,
            PrologHttpEngine prolog,
            RecentActivityObserver activity) {
        this.catalog = catalog;
        this.sessions = sessions;
        this.publisher = publisher;
        this.gateway = gateway;
        this.prolog = prolog;
        this.activity = activity;
    }

    public void register(Javalin app) {
        app.get("/api/health", this::health);
        app.get("/api/games", this::games);
        app.get("/api/plugins", this::plugins);
        app.post("/api/sessions", this::createSession);
        app.get("/api/sessions", this::listSessions);
        app.get("/api/sessions/{matchId}", this::showSession);
        app.post("/api/sessions/{matchId}/actions", this::perform);
        app.delete("/api/sessions/{matchId}", this::abandon);
        app.get("/api/activity", this::recentActivity);
        app.get("/api/delivery", this::delivery);
        app.get("/api/stats/overview", context -> relay(context, gateway.analytics("/stats/overview")));
        app.get("/api/stats/live", context -> relay(context, gateway.ingest("/metrics")));
        app.get("/api/stats/matches", context -> relay(context, gateway.analytics("/stats/matches" + query(context))));
        app.get("/api/stats/matches/{matchId}", context -> relay(context, gateway.analytics("/stats/matches/" + encoded(context, "matchId"))));
        app.get("/api/stats/games/{gameId}", context -> relay(context, gateway.analytics("/stats/games/" + encoded(context, "gameId"))));
        app.get("/api/stats/players/{playerId}", context -> relay(context, gateway.analytics("/stats/players/" + encoded(context, "playerId"))));
        app.get("/api/stats/patterns", context -> relay(context, gateway.analytics("/patterns" + query(context))));
        app.exception(SessionManager.InvalidSessionRequestException.class, (failure, context) -> error(context, HttpStatus.BAD_REQUEST, failure.getMessage()));
        app.exception(SessionManager.TooManySessionsException.class, (failure, context) -> error(context, HttpStatus.TOO_MANY_REQUESTS, failure.getMessage()));
    }

    private void health(Context context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("games", catalog.modules().size());
        body.put("openSessions", sessions.openCount());
        body.put("prologAvailable", prolog.isHealthy());
        context.json(body);
    }

    private void games(Context context) {
        List<Map<String, Object>> games = catalog.modules().stream().map(ConsoleRoutes::describe).toList();
        context.json(games);
    }

    private static Map<String, Object> describe(GameModule module) {
        Map<String, Object> game = new LinkedHashMap<>();
        game.put("gameId", module.gameId());
        game.put("gameVersion", module.gameVersion());
        game.put("title", module.title());
        game.put("description", module.description());
        game.put("modes", module.modes().stream().map(ConsoleRoutes::describeMode).toList());
        game.put("options", module.options());
        return game;
    }

    private static Map<String, Object> describeMode(GameMode mode) {
        Map<String, Object> described = new LinkedHashMap<>();
        described.put("id", mode.id());
        described.put("label", mode.label());
        described.put("playerCount", mode.playerCount());
        return described;
    }

    private void plugins(Context context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loaded", catalog.plugins().stream().map(plugin -> Map.of("jar", plugin.jarName(), "gameId", plugin.module().gameId())).toList());
        body.put("failures", catalog.failures());
        context.json(body);
    }

    private void createSession(Context context) {
        CreateSessionRequest request = parseBody(context, CreateSessionRequest.class);
        GameSession session = sessions.create(request.gameId(), request.mode(), request.players(), request.options());
        context.status(HttpStatus.CREATED).json(session.view());
    }

    private void listSessions(Context context) {
        List<Map<String, Object>> summaries = sessions.all().stream().map(session -> {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("matchId", session.matchId());
            summary.put("gameId", session.gameId());
            summary.put("finished", session.finished());
            summary.put("status", session.view().status());
            return summary;
        }).toList();
        context.json(summaries);
    }

    private void showSession(Context context) {
        sessions.find(context.pathParam("matchId")).ifPresentOrElse(
                session -> context.json(session.view()),
                () -> error(context, HttpStatus.NOT_FOUND, "Partida no encontrada"));
    }

    private void perform(Context context) {
        GameSession session = sessions.find(context.pathParam("matchId")).orElse(null);
        if (session == null) {
            error(context, HttpStatus.NOT_FOUND, "Partida no encontrada");
            return;
        }
        ActionRequest request = parseBody(context, ActionRequest.class);
        ActionResult result = session.perform(request.playerId(), request.action() == null ? Map.of() : request.action());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accepted", result.accepted());
        body.put("message", result.message());
        body.put("view", session.view());
        context.status(result.accepted() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_CONTENT).json(body);
    }

    private void abandon(Context context) {
        if (sessions.abandon(context.pathParam("matchId"))) {
            context.status(HttpStatus.NO_CONTENT);
        } else {
            error(context, HttpStatus.NOT_FOUND, "Partida no encontrada");
        }
    }

    private void recentActivity(Context context) {
        int limit = context.queryParamAsClass("limit", Integer.class).getOrDefault(50);
        context.json(activity.latest(Math.min(Math.max(limit, 1), 300)));
    }

    private void delivery(Context context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("statistics", publisher.statistics());
        body.put("recentProblems", publisher.recentProblems());
        context.json(body);
    }

    private static <T> T parseBody(Context context, Class<T> type) {
        try {
            T parsed = context.bodyAsClass(type);
            if (parsed == null) {
                throw new SessionManager.InvalidSessionRequestException("El cuerpo de la peticion esta vacio");
            }
            return parsed;
        } catch (SessionManager.InvalidSessionRequestException invalid) {
            throw invalid;
        } catch (Exception malformed) {
            throw new SessionManager.InvalidSessionRequestException("El cuerpo de la peticion no es un JSON valido");
        }
    }

    private static String encoded(Context context, String parameter) {
        return URLEncoder.encode(context.pathParam(parameter), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String query(Context context) {
        String query = context.queryString();
        return query == null || query.isEmpty() ? "" : "?" + query;
    }

    private static void relay(Context context, GameStatsGateway.Answer answer) {
        context.status(answer.status()).contentType("application/json").result(answer.body());
    }

    private static void error(Context context, HttpStatus status, String message) {
        context.status(status).json(Map.of("error", message));
    }
}
