package gameconsole.api;

import java.time.Instant;
import java.util.Map;

public interface GameSession {

    String matchId();

    String gameId();

    void addObserver(GameObserver observer);

    void start();

    GameView view();

    ActionResult perform(String playerId, Map<String, Object> action);

    void abandon();

    boolean finished();

    Instant lastActivity();
}
