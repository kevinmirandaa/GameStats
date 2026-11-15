package gameconsole.api;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public abstract class ObservableGameSession implements GameSession {

    private final String gameId;
    private final String gameVersion;
    private final String matchId;
    private final List<GameObserver> observers = new CopyOnWriteArrayList<>();
    private final List<Participant> participants = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private Instant lastTimestamp = Instant.EPOCH;
    private Instant lastActivity = Instant.now();
    private boolean started;
    private boolean finished;

    protected ObservableGameSession(String gameId, String gameVersion, String matchId) {
        this.gameId = gameId;
        this.gameVersion = gameVersion;
        this.matchId = matchId;
    }

    @Override
    public final String matchId() {
        return matchId;
    }

    @Override
    public final String gameId() {
        return gameId;
    }

    @Override
    public final void addObserver(GameObserver observer) {
        observers.add(observer);
    }

    @Override
    public final void start() {
        locked(() -> {
            if (!started) {
                started = true;
                onStart();
            }
            return null;
        });
    }

    @Override
    public final boolean finished() {
        return locked(() -> finished);
    }

    @Override
    public final Instant lastActivity() {
        return locked(() -> lastActivity);
    }

    @Override
    public final GameView view() {
        return locked(this::buildView);
    }

    @Override
    public final ActionResult perform(String playerId, Map<String, Object> action) {
        return locked(() -> {
            lastActivity = Instant.now();
            if (finished) {
                return ActionResult.rejected("La partida ya termino");
            }
            return handle(playerId, action);
        });
    }

    @Override
    public final void abandon() {
        locked(() -> {
            if (!finished) {
                finishWith(Map.of("result", "ABANDONED"), "ABANDONED");
            }
            return null;
        });
    }

    private <T> T locked(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    protected abstract void onStart();

    protected abstract GameView buildView();

    protected abstract ActionResult handle(String playerId, Map<String, Object> action);

    protected final void beginMatch(Map<String, Object> matchData, List<Participant> seatedParticipants) {
        participants.addAll(seatedParticipants);
        announce(OccurrenceKind.MATCH_STARTED, null, null, matchData);
        for (Participant participant : seatedParticipants) {
            Map<String, Object> joinData = new LinkedHashMap<>();
            joinData.put("displayName", participant.displayName());
            joinData.put("kind", participant.kind());
            announce(OccurrenceKind.PLAYER_JOINED, participant.playerId(), null, joinData);
        }
    }

    protected final void announceAction(String playerId, String action, Map<String, Object> data) {
        announce(OccurrenceKind.PLAYER_ACTION, playerId, action, data);
    }

    protected final void finishMatch(Map<String, Object> finishData) {
        finishWith(finishData, "FINISHED");
    }

    private void finishWith(Map<String, Object> finishData, String leaveReason) {
        for (Participant participant : participants) {
            announce(OccurrenceKind.PLAYER_LEFT, participant.playerId(), null, Map.of("reason", leaveReason));
        }
        announce(OccurrenceKind.MATCH_FINISHED, null, null, finishData);
        finished = true;
    }

    private void announce(OccurrenceKind kind, String playerId, String action, Map<String, Object> data) {
        GameOccurrence occurrence = new GameOccurrence(gameId, gameVersion, matchId, kind, playerId, action, data, nextTimestamp());
        for (GameObserver observer : observers) {
            try {
                observer.onOccurrence(occurrence);
            } catch (RuntimeException failure) {
                continue;
            }
        }
    }

    private Instant nextTimestamp() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        lastTimestamp = now.isAfter(lastTimestamp) ? now : lastTimestamp.plus(1, ChronoUnit.MICROS);
        return lastTimestamp;
    }
}
