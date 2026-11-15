package gameconsole.games.connect4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gameconsole.api.ActionResult;
import gameconsole.api.GameOccurrence;
import gameconsole.api.GameSession;
import gameconsole.api.GameView;
import gameconsole.api.OccurrenceKind;
import gameconsole.api.PlayerSeat;
import gameconsole.api.SessionRequest;
import gameconsole.api.SessionServices;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Connect4AdapterTest {

    private final Connect4Module module = new Connect4Module();
    private final FakeLogicEngine engine = new FakeLogicEngine();

    private GameSession open(String mode, Map<String, String> options, List<GameOccurrence> sink) {
        List<PlayerSeat> seats = TWO_PLAYERS_MODE.equals(mode)
                ? List.of(new PlayerSeat("player-ana", "Ana"), new PlayerSeat("player-luis", "Luis"))
                : List.of(new PlayerSeat("player-ana", "Ana"));
        GameSession session = module.createSession(new SessionRequest("connect4-test-1", mode, seats, options), new SessionServices(engine));
        session.addObserver(sink::add);
        session.start();
        return session;
    }

    private static final String TWO_PLAYERS_MODE = Connect4Module.TWO_PLAYERS;

    private static List<String> actionsOf(List<GameOccurrence> occurrences) {
        List<String> actions = new ArrayList<>();
        for (GameOccurrence occurrence : occurrences) {
            if (occurrence.kind() == OccurrenceKind.PLAYER_ACTION) {
                actions.add(occurrence.action());
            }
        }
        return actions;
    }

    @Test
    void startingAGameAgainstTheComputerAnnouncesTheMatchAndBothPlayers() {
        List<GameOccurrence> seen = new ArrayList<>();

        open(Connect4Module.VERSUS_COMPUTER, Map.of(), seen);

        assertEquals(List.of(OccurrenceKind.MATCH_STARTED, OccurrenceKind.PLAYER_JOINED, OccurrenceKind.PLAYER_JOINED),
                seen.stream().map(GameOccurrence::kind).toList());
        assertEquals("player-ana", seen.get(1).playerId());
        assertEquals("ai-prolog-connect4", seen.get(2).playerId());
        assertEquals("COMPUTER", seen.get(2).data().get("kind"));
    }

    @Test
    void aHumanMoveIsFollowedByAPrologDecisionAndTheComputerMove() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(Connect4Module.VERSUS_COMPUTER, Map.of(), seen);

        ActionResult result = session.perform("player-ana", Map.of("cell", 3));

        assertTrue(result.accepted());
        assertEquals(List.of("MOVE", "AI_DECISION", "MOVE"), actionsOf(seen));
        assertEquals(1, engine.requests().size());
        assertEquals(Connect4Module.GAME_ID, seen.get(0).gameId());
    }

    @Test
    void theDecisionEventCarriesTheBoardTheChoiceAndTheSearchStatistics() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(Connect4Module.VERSUS_COMPUTER, Map.of(), seen);

        session.perform("player-ana", Map.of("column", 2));

        GameOccurrence decision = seen.stream().filter(occurrence -> "AI_DECISION".equals(occurrence.action())).findFirst().orElseThrow();
        assertEquals("ai-prolog-connect4", decision.playerId());
        assertNotNull(decision.data().get("boardState"));
        assertEquals(0, decision.data().get("chosenColumn"));
        assertEquals(345L, decision.data().get("nodesSearched"));
        assertEquals(5, decision.data().get("searchDepth"));
        assertEquals("swi-prolog", decision.data().get("engine"));
    }

    @Test
    void theDifficultyChoosesTheSearchDepthSentToProlog() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(Connect4Module.VERSUS_COMPUTER, Map.of("difficulty", "DIFICIL"), seen);

        session.perform("player-ana", Map.of("column", 3));

        assertEquals(7, engine.requests().get(0).get("depth"));
    }

    @Test
    void theComputerMovesFirstWhenRequested() {
        List<GameOccurrence> seen = new ArrayList<>();

        GameSession session = open(Connect4Module.VERSUS_COMPUTER, Map.of("first", "COMPUTADORA"), seen);

        assertEquals(List.of("AI_DECISION", "MOVE"), actionsOf(seen));
        assertEquals("player-ana", session.view().currentPlayerId());
    }

    @Test
    void movingOutOfTurnOrIntoAFullColumnIsRejected() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(TWO_PLAYERS_MODE, Map.of(), seen);

        assertFalse(session.perform("player-luis", Map.of("column", 1)).accepted());
        assertFalse(session.perform("player-ana", Map.of("column", 99)).accepted());
        assertFalse(session.perform("player-ana", Map.of()).accepted());
        assertFalse(session.perform("player-zoe", Map.of("column", 1)).accepted());
        assertEquals(List.of(), actionsOf(seen));
    }

    @Test
    void nonIntegerOrOutOfRangeColumnsAreRejected() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(TWO_PLAYERS_MODE, Map.of(), seen);

        assertFalse(session.perform("player-ana", Map.of("column", 2.9)).accepted());
        assertFalse(session.perform("player-ana", Map.of("column", 1e30)).accepted());
        assertFalse(session.perform("player-ana", Map.of("cell", -5)).accepted());
        assertFalse(session.perform("player-ana", Map.of("column", "3")).accepted());
        assertEquals(List.of(), actionsOf(seen));
        assertTrue(session.perform("player-ana", Map.of("column", 2.0)).accepted());
    }

    @Test
    void aVerticalWinProducesWonLostAndFinishEventsInOrder() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(TWO_PLAYERS_MODE, Map.of(), seen);
        for (int round = 0; round < 3; round++) {
            session.perform("player-ana", Map.of("column", 0));
            session.perform("player-luis", Map.of("column", 1));
        }

        session.perform("player-ana", Map.of("column", 0));

        List<String> kinds = seen.stream().skip(3 + 6).map(occurrence -> occurrence.kind() == OccurrenceKind.PLAYER_ACTION ? occurrence.action() : occurrence.kind().name()).toList();
        assertEquals(List.of("MOVE", "PLAYER_WON", "PLAYER_LOST", "PLAYER_LEFT", "PLAYER_LEFT", "MATCH_FINISHED"), kinds);
        GameOccurrence finish = seen.get(seen.size() - 1);
        assertEquals("WIN", finish.data().get("result"));
        assertEquals("player-ana", finish.data().get("winnerPlayerId"));
        assertTrue(session.finished());
        assertEquals("player-ana", session.view().winnerPlayerId());
    }

    @Test
    void whenTheComputerWinsItsWinIsReportedWithTheComputerKind() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(Connect4Module.VERSUS_COMPUTER, Map.of(), seen);
        int[] humanColumns = {6, 5, 6, 5};
        for (int column : humanColumns) {
            session.perform("player-ana", Map.of("column", column));
        }

        assertTrue(session.finished());
        GameOccurrence won = seen.stream().filter(occurrence -> "PLAYER_WON".equals(occurrence.action())).findFirst().orElseThrow();
        assertEquals("ai-prolog-connect4", won.playerId());
        GameOccurrence finish = seen.get(seen.size() - 1);
        assertEquals("COMPUTER", finish.data().get("winnerKind"));
        assertEquals("ai-prolog-connect4", session.view().winnerPlayerId());
    }

    @Test
    void anUnavailablePrologEngineKeepsTheHumanMoveAndAllowsARetry() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(Connect4Module.VERSUS_COMPUTER, Map.of(), seen);
        engine.setUnavailable(true);

        ActionResult first = session.perform("player-ana", Map.of("column", 3));

        assertTrue(first.accepted());
        assertEquals(List.of("MOVE"), actionsOf(seen));
        assertFalse(session.view().message().isEmpty());

        engine.setUnavailable(false);
        session.perform("player-ana", Map.of("column", 3));

        assertEquals(List.of("MOVE", "AI_DECISION", "MOVE"), actionsOf(seen));
        assertEquals("", session.view().message());
    }

    @Test
    void abandoningAnOpenGameFinishesItAsAbandoned() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(Connect4Module.VERSUS_COMPUTER, Map.of(), seen);

        session.abandon();

        GameOccurrence finish = seen.get(seen.size() - 1);
        assertEquals(OccurrenceKind.MATCH_FINISHED, finish.kind());
        assertEquals("ABANDONED", finish.data().get("result"));
    }

    @Test
    void theViewShowsAGridWithPlayableColumnsAndTheCurrentTurn() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(TWO_PLAYERS_MODE, Map.of(), seen);
        session.perform("player-ana", Map.of("column", 3));

        GameView view = session.view();

        assertEquals(6, view.rows());
        assertEquals(7, view.columns());
        assertEquals(42, view.cells().size());
        assertEquals("player-luis", view.currentPlayerId());
        assertEquals("red", view.cells().get(5 * 7 + 3).style());
        assertTrue(view.cells().get(0).enabled());
        assertTrue(view.status().contains("Luis"));
        assertEquals("fichas rojas", view.players().get(0).score());
    }

    @Test
    void theModuleDescribesItsModesAndOptions() {
        assertEquals("connect4", module.gameId());
        assertEquals(2, module.modes().size());
        assertEquals(2, module.options().size());
    }
}
