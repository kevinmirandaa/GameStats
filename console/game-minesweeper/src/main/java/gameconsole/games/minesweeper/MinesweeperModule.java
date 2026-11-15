package gameconsole.games.minesweeper;

import gameconsole.api.GameMode;
import gameconsole.api.GameModule;
import gameconsole.api.GameOption;
import gameconsole.api.GameSession;
import gameconsole.api.SessionRequest;
import gameconsole.api.SessionServices;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class MinesweeperModule implements GameModule {

    public static final String GAME_ID = "minesweeper";
    public static final String GAME_VERSION = "1.0";
    public static final String SOLO = "SOLO";

    private record Layout(int rows, int columns, int mines) {
    }

    private static final Map<String, Layout> LAYOUT_BY_DIFFICULTY = Map.of(
            "PRINCIPIANTE", new Layout(9, 9, 10),
            "INTERMEDIO", new Layout(12, 12, 25),
            "EXPERTO", new Layout(16, 16, 40));

    private final Random random;
    private final Clock clock;

    public MinesweeperModule() {
        this(new Random(), Clock.systemUTC());
    }

    MinesweeperModule(Random random, Clock clock) {
        this.random = random;
        this.clock = clock;
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
        return "Buscaminas";
    }

    @Override
    public String description() {
        return "Descubre todas las casillas sin mina. Los numeros indican cuantas minas hay alrededor. Usa banderas para marcarlas.";
    }

    @Override
    public List<GameMode> modes() {
        return List.of(new GameMode(SOLO, "Un jugador", 1, true));
    }

    @Override
    public List<GameOption> options() {
        return List.of(new GameOption("difficulty", "Dificultad", List.of("PRINCIPIANTE", "INTERMEDIO", "EXPERTO"), "PRINCIPIANTE"));
    }

    @Override
    public GameSession createSession(SessionRequest request, SessionServices services) {
        String difficulty = LAYOUT_BY_DIFFICULTY.containsKey(request.option("difficulty", "PRINCIPIANTE")) ? request.option("difficulty", "PRINCIPIANTE") : "PRINCIPIANTE";
        Layout layout = LAYOUT_BY_DIFFICULTY.get(difficulty);
        MineField field = new MineField(layout.rows(), layout.columns(), layout.mines(), random);
        return new MinesweeperAdapter(request.matchId(), request.players().get(0).playerId(), request.players().get(0).displayName(), difficulty, field, clock);
    }
}
