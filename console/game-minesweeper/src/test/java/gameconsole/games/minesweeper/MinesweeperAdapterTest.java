package gameconsole.games.minesweeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gameconsole.api.ActionResult;
import gameconsole.api.GameOccurrence;
import gameconsole.api.GameSession;
import gameconsole.api.GameView;
import gameconsole.api.OccurrenceKind;
import gameconsole.api.PlayerSeat;
import gameconsole.api.SessionRequest;
import gameconsole.api.SessionServices;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MinesweeperAdapterTest {

    private static final long SEED = 21L;

    private final MinesweeperModule module = new MinesweeperModule(new Random(SEED), Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC));

    private GameSession open(List<GameOccurrence> sink) {
        GameSession session = module.createSession(
                new SessionRequest("minesweeper-test-1", MinesweeperModule.SOLO, List.of(new PlayerSeat("player-ana", "Ana")), Map.of()),
                new SessionServices(null));
        session.addObserver(sink::add);
        session.start();
        return session;
    }

    private static List<String> actionsOf(List<GameOccurrence> seen) {
        return seen.stream().filter(occurrence -> occurrence.action() != null).map(GameOccurrence::action).toList();
    }

    private static MineField replicaAfterFirstReveal(int firstCell) {
        MineField replica = new MineField(9, 9, 10, new Random(SEED));
        replica.reveal(firstCell / 9, firstCell % 9);
        return replica;
    }

    @Test
    void startingAnnouncesTheMatchAndThePlayer() {
        List<GameOccurrence> seen = new ArrayList<>();

        open(seen);

        assertEquals(List.of(OccurrenceKind.MATCH_STARTED, OccurrenceKind.PLAYER_JOINED), seen.stream().map(GameOccurrence::kind).toList());
        assertEquals(10, seen.get(0).data().get("mines"));
        assertEquals("minesweeper", seen.get(0).gameId());
    }

    @Test
    void revealingACellEmitsADetailedEvent() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(seen);

        ActionResult result = session.perform("player-ana", Map.of("cell", 40));

        assertTrue(result.accepted());
        GameOccurrence reveal = seen.get(seen.size() - 1);
        assertEquals("REVEAL_CELL", reveal.action());
        assertEquals(4, reveal.data().get("row"));
        assertEquals(4, reveal.data().get("column"));
        assertEquals(false, reveal.data().get("hitMine"));
        assertEquals(true, reveal.data().get("cascade"));
    }

    @Test
    void flaggingEmitsAFlagEventAndTheViewShowsIt() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(seen);

        session.perform("player-ana", Map.of("cell", 0, "flag", true));

        assertEquals("FLAG_CELL", seen.get(seen.size() - 1).action());
        assertEquals("flag", session.view().cells().get(0).style());
        assertTrue(session.view().status().contains("9"));
    }

    @Test
    void revealingAMineEndsTheMatchWithALossAndShowsTheMines() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(seen);
        session.perform("player-ana", Map.of("cell", 40));
        MineField replica = replicaAfterFirstReveal(40);
        int mineCell = -1;
        for (int cell = 0; cell < 81 && mineCell < 0; cell++) {
            if (replica.isMine(cell / 9, cell % 9)) {
                mineCell = cell;
            }
        }

        session.perform("player-ana", Map.of("cell", mineCell));

        assertTrue(session.finished());
        assertTrue(actionsOf(seen).contains("PLAYER_LOST"));
        GameOccurrence finish = seen.get(seen.size() - 1);
        assertEquals("LOSS", finish.data().get("result"));
        assertFalse(finish.data().containsKey("winnerPlayerId"));
        assertNull(session.view().winnerPlayerId());
        assertTrue(session.view().cells().get(mineCell).style().contains("exploded"));
    }

    @Test
    void revealingEverySafeCellWinsTheMatch() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(seen);
        session.perform("player-ana", Map.of("cell", 40));
        MineField replica = replicaAfterFirstReveal(40);

        for (int cell = 0; cell < 81; cell++) {
            if (!replica.isMine(cell / 9, cell % 9) && session.view().cells().get(cell).enabled()) {
                session.perform("player-ana", Map.of("cell", cell));
            }
        }

        assertTrue(session.finished());
        assertTrue(actionsOf(seen).contains("PLAYER_WON"));
        assertEquals("WIN", seen.get(seen.size() - 1).data().get("result"));
        assertEquals("player-ana", session.view().winnerPlayerId());
    }

    @Test
    void invalidActionsAreRejectedWithoutEvents() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(seen);
        int before = seen.size();

        assertFalse(session.perform("player-zoe", Map.of("cell", 1)).accepted());
        assertFalse(session.perform("player-ana", Map.of()).accepted());
        assertFalse(session.perform("player-ana", Map.of("cell", 500)).accepted());
        assertFalse(session.perform("player-ana", Map.of("cell", -3)).accepted());
        session.perform("player-ana", Map.of("cell", 0, "flag", true));
        assertFalse(session.perform("player-ana", Map.of("cell", 0)).accepted());
        assertEquals(before + 1, seen.size());
    }

    @Test
    void revealingAnAlreadyRevealedCellIsRejected() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(seen);
        session.perform("player-ana", Map.of("cell", 40));

        assertFalse(session.perform("player-ana", Map.of("cell", 40)).accepted());
        assertFalse(session.perform("player-ana", Map.of("cell", 40, "flag", true)).accepted());
    }

    @Test
    void theViewIsAGridOfTheDifficultySize() {
        List<GameOccurrence> seen = new ArrayList<>();

        GameView view = open(seen).view();

        assertEquals(9, view.rows());
        assertEquals(9, view.columns());
        assertEquals(81, view.cells().size());
        assertEquals(List.of("flag"), view.extraActions());
    }

    @Test
    void abandoningFinishesTheMatchAsAbandoned() {
        List<GameOccurrence> seen = new ArrayList<>();
        GameSession session = open(seen);

        session.abandon();

        assertEquals("ABANDONED", seen.get(seen.size() - 1).data().get("result"));
    }
}
