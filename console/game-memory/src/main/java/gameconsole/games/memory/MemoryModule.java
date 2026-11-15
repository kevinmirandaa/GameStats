package gameconsole.games.memory;

import gameconsole.api.GameMode;
import gameconsole.api.GameModule;
import gameconsole.api.GameOption;
import gameconsole.api.GameSession;
import gameconsole.api.PlayerSeat;
import gameconsole.api.SessionRequest;
import gameconsole.api.SessionServices;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class MemoryModule implements GameModule {

    public static final String GAME_ID = "memory";
    public static final String GAME_VERSION = "1.0";
    public static final String SOLO = "SOLO";
    public static final String TWO_PLAYERS = "TWO_PLAYERS";

    private static final Map<String, Integer> PAIRS_BY_SIZE = Map.of("PEQUENO", 6, "MEDIANO", 8, "GRANDE", 10);
    private static final Map<String, Integer> COLUMNS_BY_SIZE = Map.of("PEQUENO", 4, "MEDIANO", 4, "GRANDE", 5);

    private final Random random;

    public MemoryModule() {
        this(new Random());
    }

    MemoryModule(Random random) {
        this.random = random;
    }

    @Override
    public String gameId() {
        return GAME_ID;
    }

    @Override
    public String gameVersion() {
        return GAME_VERSION;
    }

    @Override
    public String title() {
        return "Memoria";
    }

    @Override
    public String description() {
        return "Encuentra todas las parejas de cartas. Solo o contra otro jugador, gana quien reuna mas parejas.";
    }

    @Override
    public List<GameMode> modes() {
        return List.of(
                new GameMode(TWO_PLAYERS, "Dos jugadores", 2, true),
                new GameMode(SOLO, "Un jugador", 1, true));
    }

    @Override
    public List<GameOption> options() {
        return List.of(new GameOption("size", "Tamano", List.of("PEQUENO", "MEDIANO", "GRANDE"), "MEDIANO"));
    }

    @Override
    public GameSession createSession(SessionRequest request, SessionServices services) {
        String size = PAIRS_BY_SIZE.containsKey(request.option("size", "MEDIANO")) ? request.option("size", "MEDIANO") : "MEDIANO";
        boolean solo = SOLO.equals(request.mode());
        List<MemoryPlayer> players = new ArrayList<>();
        for (PlayerSeat seat : request.players().subList(0, solo ? 1 : 2)) {
            players.add(new MemoryPlayer(seat.playerId(), seat.displayName()));
        }
        Deck deck = new Deck(PAIRS_BY_SIZE.get(size), random);
        MemoryMatch match = new MemoryMatch(deck, players);
        return new MemoryAdapter(request.matchId(), solo ? SOLO : TWO_PLAYERS, size, COLUMNS_BY_SIZE.get(size), match);
    }
}
