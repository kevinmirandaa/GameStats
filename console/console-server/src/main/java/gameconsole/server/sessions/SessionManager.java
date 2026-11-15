package gameconsole.server.sessions;

import gameconsole.api.GameMode;
import gameconsole.api.GameModule;
import gameconsole.api.GameObserver;
import gameconsole.api.GameSession;
import gameconsole.api.PlayerSeat;
import gameconsole.api.SessionRequest;
import gameconsole.api.SessionServices;
import gameconsole.server.plugins.GameCatalog;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    public static final class InvalidSessionRequestException extends RuntimeException {
        public InvalidSessionRequestException(String message) {
            super(message);
        }
    }

    public static final class TooManySessionsException extends RuntimeException {
        public TooManySessionsException(String message) {
            super(message);
        }
    }

    private record ManagedSession(GameSession session, Instant createdAt) {
    }

    private final GameCatalog catalog;
    private final SessionServices services;
    private final List<GameObserver> observers;
    private final int maximumOpenSessions;
    private final Map<String, ManagedSession> sessionsByMatchId = new ConcurrentHashMap<>();
    private final Object creationLock = new Object();

    public SessionManager(GameCatalog catalog, SessionServices services, List<GameObserver> observers, int maximumOpenSessions) {
        this.catalog = catalog;
        this.services = services;
        this.observers = List.copyOf(observers);
        this.maximumOpenSessions = maximumOpenSessions;
    }

    public GameSession create(String gameId, String modeId, List<String> rawNames, Map<String, String> options) {
        GameModule module = catalog.find(gameId).orElseThrow(() -> new InvalidSessionRequestException("Juego desconocido: " + gameId));
        GameMode mode = module.modes().stream()
                .filter(candidate -> candidate.id().equals(modeId))
                .findFirst()
                .orElseThrow(() -> new InvalidSessionRequestException("Modo no disponible para " + gameId + ": " + modeId));
        List<String> names = new ArrayList<>();
        for (int position = 1; position <= mode.playerCount(); position++) {
            String raw = rawNames != null && rawNames.size() >= position ? rawNames.get(position - 1) : "";
            names.add(PlayerIdentity.cleanName(raw, position));
        }
        List<String> ids = PlayerIdentity.distinctIds(names);
        List<PlayerSeat> seats = new ArrayList<>();
        for (int index = 0; index < names.size(); index++) {
            seats.add(new PlayerSeat(ids.get(index), names.get(index)));
        }
        String matchId = gameId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Map<String, String> safeOptions = new HashMap<>();
        if (options != null) {
            options.forEach((name, value) -> {
                if (name != null && value != null) {
                    safeOptions.put(name, value);
                }
            });
        }
        GameSession session = module.createSession(new SessionRequest(matchId, modeId, seats, safeOptions), services);
        for (GameObserver observer : observers) {
            session.addObserver(observer);
        }
        synchronized (creationLock) {
            if (openCount() >= maximumOpenSessions) {
                throw new TooManySessionsException("Se alcanzo el maximo de partidas abiertas");
            }
            sessionsByMatchId.put(matchId, new ManagedSession(session, Instant.now()));
        }
        session.start();
        return session;
    }

    public Optional<GameSession> find(String matchId) {
        return Optional.ofNullable(sessionsByMatchId.get(matchId)).map(ManagedSession::session);
    }

    public List<GameSession> all() {
        return sessionsByMatchId.values().stream()
                .sorted(Comparator.comparing(ManagedSession::createdAt))
                .map(ManagedSession::session)
                .toList();
    }

    public int openCount() {
        return (int) sessionsByMatchId.values().stream().filter(managed -> !managed.session().finished()).count();
    }

    public boolean abandon(String matchId) {
        ManagedSession managed = sessionsByMatchId.get(matchId);
        if (managed == null) {
            return false;
        }
        managed.session().abandon();
        return true;
    }

    public void abandonAll() {
        for (ManagedSession managed : sessionsByMatchId.values()) {
            managed.session().abandon();
        }
    }

    public int sweep(Instant now, Duration idleTimeout, Duration finishedRetention) {
        int changes = 0;
        for (Map.Entry<String, ManagedSession> entry : sessionsByMatchId.entrySet()) {
            GameSession session = entry.getValue().session();
            Duration quiet = Duration.between(session.lastActivity(), now);
            if (!session.finished() && quiet.compareTo(idleTimeout) > 0) {
                session.abandon();
                changes++;
            } else if (session.finished() && quiet.compareTo(finishedRetention) > 0) {
                sessionsByMatchId.remove(entry.getKey());
                changes++;
            }
        }
        return changes;
    }
}
