package gameconsole.api;

import java.util.List;

public interface GameModule {

    String gameId();

    String gameVersion();

    String title();

    String description();

    List<GameMode> modes();

    List<GameOption> options();

    GameSession createSession(SessionRequest request, SessionServices services);
}
