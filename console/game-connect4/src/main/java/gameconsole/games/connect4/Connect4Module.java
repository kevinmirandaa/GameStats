package gameconsole.games.connect4;

import gameconsole.api.GameMode;
import gameconsole.api.GameModule;
import gameconsole.api.GameOption;
import gameconsole.api.GameSession;
import gameconsole.api.PlayerSeat;
import gameconsole.api.SessionRequest;
import gameconsole.api.SessionServices;
import java.util.List;
import java.util.Map;

public final class Connect4Module implements GameModule {

    public static final String GAME_ID = "connect4";
    public static final String GAME_VERSION = "1.0";
    public static final String VERSUS_COMPUTER = "VS_COMPUTER";
    public static final String TWO_PLAYERS = "TWO_PLAYERS";

    private static final Map<String, Integer> DEPTH_BY_DIFFICULTY = Map.of("FACIL", 3, "MEDIO", 5, "DIFICIL", 7);

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
        return "Conecta 4";
    }

    @Override
    public String description() {
        return "Alinea cuatro fichas antes que tu rival. Contra la computadora, cada jugada la calcula un programa en Prolog.";
    }

    @Override
    public List<GameMode> modes() {
        return List.of(
                new GameMode(VERSUS_COMPUTER, "Contra la computadora (Prolog)", 1, true),
                new GameMode(TWO_PLAYERS, "Dos jugadores", 2, true));
    }

    @Override
    public List<GameOption> options() {
        return List.of(
                new GameOption("difficulty", "Dificultad", List.of("FACIL", "MEDIO", "DIFICIL"), "MEDIO"),
                new GameOption("first", "Empieza", List.of("HUMANO", "COMPUTADORA"), "HUMANO"));
    }

    @Override
    public GameSession createSession(SessionRequest request, SessionServices services) {
        String difficulty = request.option("difficulty", "MEDIO");
        int depth = DEPTH_BY_DIFFICULTY.getOrDefault(difficulty, DEPTH_BY_DIFFICULTY.get("MEDIO"));
        List<PlayerSeat> seats = request.players();
        boolean versusComputer = !TWO_PLAYERS.equals(request.mode());

        ConnectFourPlayer first = new HumanPlayer(seats.get(0).playerId(), seats.get(0).displayName(), Disc.RED);
        ConnectFourPlayer second = versusComputer
                ? new ComputerPlayer(Disc.YELLOW, new PrologColumnAdvisor(services.logicEngine()), depth)
                : new HumanPlayer(seats.get(1).playerId(), seats.get(1).displayName(), Disc.YELLOW);

        boolean computerStarts = versusComputer && "COMPUTADORA".equals(request.option("first", "HUMANO"));
        ConnectFourMatch match = new ConnectFourMatch(List.of(first, second), computerStarts ? 1 : 0);
        Connect4Adapter adapter = new Connect4Adapter(request.matchId(), versusComputer ? VERSUS_COMPUTER : TWO_PLAYERS, match, difficulty);
        return adapter;
    }
}
