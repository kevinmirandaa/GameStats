package gameconsole.games.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MemoryAdapterTest {

    private static final long SEED = 11L;

    private final MemoryModule module = new MemoryModule(new Random(SEED));

    private GameSession open(String mode, String size, List<GameOccurrence> sink) {
        List<PlayerSeat> seats = List.of(new PlayerSeat("player-ana", "Ana"), new PlayerSeat("player-luis", "Luis"));
        GameSession session = module.createSession(new SessionRequest("memory-test-1", mode, seats, Map.of("size", size)), new SessionServices(null));
        session.addObserver(sink::add);
        session.start();
        return session;
    }

    private static List<String> layout(int pairs) {
        return new Deck(pairs, new Random(SEED)).cards().stream().map(Card::symbol).toList();
    }

    private static List<List<Integer>> pairsOf(List<String> symbols) {
        Map<String, List<Integer>> positions = new HashMap<>();
        for (int index = 0; index < symbols.size(); index++) {
            positions.computeIfAbsent(symbols.get(index), key -> new ArrayList<>()).add(index);
        }
        return new ArrayList<>(positions.values());
    }

    @Test
    void startingAnnouncesTheMatchAndEveryPlayer() {
        List<GameOccurrence> seen = new ArrayList<>();

        open(MemoryModule.TWO_PLAYERS, "PEQUENO", seen);

        assertEquals(List.of(OccurrenceKind.MATCH_STARTED, OccurrenceKind.PLAYER_JOINED, OccurrenceKind.PLAYER_JOINED),
                seen.stream().map(GameOccurrence::kind).toList());
        assertEquals(6, seen.get(0).data().get("pairs"));
        assertEquals("memory", seen.get(0).gameId());
    }

    @Test
    void flippingACardEmitsAFlipEventWithItsSymbolAndTurn() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(MemoryModule.TWO_PLAYERS, "PEQUENO", seen);

        ActionResult result = session.perform("player-ana", Map.of("cell", 4));

        assertTrue(result.accepted());
        GameOccurrence flip = seen.get(seen.size() - 1);
        assertEquals("FLIP_CARD", flip.action());
        assertEquals(4, flip.data().get("cardIndex"));
        assertEquals(1, flip.data().get("flipInTurn"));
        assertEquals(1, flip.data().get("turnNumber"));
        assertFalse(((String) flip.data().get("symbol")).isEmpty());
    }

    @Test
    void playingOutOfTurnOrWithUnknownPlayersIsRejected() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(MemoryModule.TWO_PLAYERS, "PEQUENO", seen);

        assertFalse(session.perform("player-luis", Map.of("cell", 0)).accepted());
        assertFalse(session.perform("player-zoe", Map.of("cell", 0)).accepted());
        assertFalse(session.perform("player-ana", Map.of()).accepted());
        assertFalse(session.perform("player-ana", Map.of("cell", 99)).accepted());
    }

    @Test
    void aSoloPlayerFindingEveryPairWinsAndTheMatchFinishes() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(MemoryModule.SOLO, "PEQUENO", seen);

        for (List<Integer> pair : pairsOf(layout(6))) {
            session.perform("player-ana", Map.of("cell", pair.get(0)));
            session.perform("player-ana", Map.of("cell", pair.get(1)));
        }

        assertTrue(session.finished());
        List<String> actions = seen.stream().filter(occurrence -> occurrence.action() != null).map(GameOccurrence::action).toList();
        assertEquals(6, actions.stream().filter("PAIR_FOUND"::equals).count());
        assertEquals(12, actions.stream().filter("FLIP_CARD"::equals).count());
        assertTrue(actions.contains("PLAYER_WON"));
        GameOccurrence finish = seen.get(seen.size() - 1);
        assertEquals(OccurrenceKind.MATCH_FINISHED, finish.kind());
        assertEquals("WIN", finish.data().get("result"));
        assertEquals("player-ana", finish.data().get("winnerPlayerId"));
    }

    @Test
    void twoPlayersEndWithAWinnerAndALoserWhenScoresDiffer() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(MemoryModule.TWO_PLAYERS, "PEQUENO", seen);

        for (List<Integer> pair : pairsOf(layout(6))) {
            session.perform("player-ana", Map.of("cell", pair.get(0)));
            session.perform("player-ana", Map.of("cell", pair.get(1)));
        }

        assertTrue(session.finished());
        List<String> actions = seen.stream().filter(occurrence -> occurrence.action() != null).map(GameOccurrence::action).toList();
        assertTrue(actions.contains("PLAYER_WON"));
        assertTrue(actions.contains("PLAYER_LOST"));
        assertEquals("player-ana", session.view().winnerPlayerId());
    }

    @Test
    void equalScoresEndInADrawForBothPlayers() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(MemoryModule.TWO_PLAYERS, "PEQUENO", seen);
        List<List<Integer>> pairs = pairsOf(layout(6));

        for (int index = 0; index < pairs.size(); index++) {
            String player = index < 3 ? "player-ana" : "player-luis";
            if (index == 3) {
                missOnce(session, layout(6));
            }
            session.perform(player, Map.of("cell", pairs.get(index).get(0)));
            session.perform(player, Map.of("cell", pairs.get(index).get(1)));
        }

        assertTrue(session.finished());
        assertEquals("DRAW", seen.get(seen.size() - 1).data().get("result"));
        assertEquals(2, seen.stream().filter(occurrence -> "PLAYER_DRAW".equals(occurrence.action())).count());
    }

    private static void missOnce(GameSession session, List<String> symbols) {
        int first = -1;
        int second = -1;
        for (int index = 0; index < symbols.size() && second < 0; index++) {
            if (!session.view().cells().get(index).enabled()) {
                continue;
            }
            if (first < 0) {
                first = index;
            } else if (!symbols.get(index).equals(symbols.get(first))) {
                second = index;
            }
        }
        session.perform(session.view().currentPlayerId(), Map.of("cell", first));
        session.perform(session.view().currentPlayerId(), Map.of("cell", second));
    }

    @Test
    void aMissedPairIsReportedTheViewAsksToContinueAndTheTurnPasses() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(MemoryModule.TWO_PLAYERS, "PEQUENO", seen);

        missOnce(session, layout(6));

        assertTrue(seen.stream().anyMatch(occurrence -> "PAIR_MISSED".equals(occurrence.action())));
        assertEquals(List.of("continue"), session.view().extraActions());
        session.perform("player-ana", Map.of("continue", true));
        assertEquals("player-luis", session.view().currentPlayerId());
    }

    @Test
    void theNextPlayerCanFlipDirectlyAfterAMiss() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(MemoryModule.TWO_PLAYERS, "PEQUENO", seen);
        missOnce(session, layout(6));

        ActionResult byNextPlayer = session.perform("player-luis", Map.of("cell", 0));

        assertTrue(byNextPlayer.accepted());
        assertEquals(2, seen.get(seen.size() - 1).data().get("turnNumber"));
    }

    @Test
    void thePlayerWhoMissedCannotFlipAnotherCardInTheSameTurn() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(MemoryModule.TWO_PLAYERS, "PEQUENO", seen);
        missOnce(session, layout(6));

        ActionResult byTheSamePlayer = session.perform("player-ana", Map.of("cell", 0));

        assertFalse(byTheSamePlayer.accepted());
    }

    @Test
    void theViewHasTheGridDimensionsOfTheChosenSize() {
        List<GameOccurrence> seen = new ArrayList<>();

        GameView view = open(MemoryModule.TWO_PLAYERS, "GRANDE", seen).view();

        assertEquals(20, view.cells().size());
        assertEquals(5, view.columns());
        assertEquals(4, view.rows());
        assertTrue(view.cells().stream().allMatch(cell -> cell.enabled() && cell.style().equals("hidden")));
    }

    @Test
    void abandoningFinishesTheMatchAsAbandoned() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(MemoryModule.TWO_PLAYERS, "PEQUENO", seen);

        session.abandon();

        assertEquals("ABANDONED", seen.get(seen.size() - 1).data().get("result"));
    }
}
