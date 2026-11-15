package gameconsole.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ObservableGameSessionTest {

    private static final List<Participant> TWO_PLAYERS = List.of(
            new Participant("player-ana", "Ana", Participant.HUMAN),
            new Participant("player-luis", "Luis", Participant.HUMAN));

    private static final class ProbeSession extends ObservableGameSession {

        ProbeSession() {
            super("probe", "1.0", "probe-001");
        }

        @Override
        protected void onStart() {
            beginMatch(Map.of("mode", "TEST"), TWO_PLAYERS);
        }

        @Override
        protected GameView buildView() {
            return new GameView(matchId(), gameId(), "Probe", 0, 0, List.of(), List.of(), "", "", "", finished(), null, List.of());
        }

        @Override
        protected ActionResult handle(String playerId, Map<String, Object> action) {
            announceAction(playerId, "TOUCH", Map.of("count", 1));
            if (Boolean.TRUE.equals(action.get("end"))) {
                finishMatch(Map.of("result", "WIN"));
            }
            return ActionResult.ok();
        }
    }

    private static List<GameOccurrence> record(ProbeSession session) {
        List<GameOccurrence> seen = new ArrayList<>();
        session.addObserver(seen::add);
        return seen;
    }

    @Test
    void startingAMatchAnnouncesTheMatchAndEveryPlayerInOrder() {
        ProbeSession session = new ProbeSession();
        List<GameOccurrence> seen = record(session);

        session.start();

        assertEquals(List.of(OccurrenceKind.MATCH_STARTED, OccurrenceKind.PLAYER_JOINED, OccurrenceKind.PLAYER_JOINED),
                seen.stream().map(GameOccurrence::kind).toList());
        assertEquals("player-ana", seen.get(1).playerId());
        assertEquals("probe-001", seen.get(0).matchId());
    }

    @Test
    void startingTwiceAnnouncesTheMatchOnlyOnce() {
        ProbeSession session = new ProbeSession();
        List<GameOccurrence> seen = record(session);

        session.start();
        session.start();

        assertEquals(3, seen.size());
    }

    @Test
    void timestampsAreStrictlyIncreasingEvenWhenEventsAreInstant() {
        ProbeSession session = new ProbeSession();
        List<GameOccurrence> seen = record(session);
        session.start();
        for (int index = 0; index < 200; index++) {
            session.perform("player-ana", Map.of());
        }

        Instant previous = Instant.EPOCH;
        for (GameOccurrence occurrence : seen) {
            assertTrue(occurrence.occurredAt().isAfter(previous));
            previous = occurrence.occurredAt();
        }
    }

    @Test
    void finishingAnnouncesEveryPlayerLeavingAndThenTheEndOfTheMatch() {
        ProbeSession session = new ProbeSession();
        List<GameOccurrence> seen = record(session);
        session.start();
        seen.clear();

        session.perform("player-ana", Map.of("end", true));

        List<OccurrenceKind> kinds = seen.stream().map(GameOccurrence::kind).toList();
        assertEquals(List.of(OccurrenceKind.PLAYER_ACTION, OccurrenceKind.PLAYER_LEFT, OccurrenceKind.PLAYER_LEFT, OccurrenceKind.MATCH_FINISHED), kinds);
        assertTrue(session.finished());
    }

    @Test
    void actionsAfterTheEndAreRejectedAndProduceNoEvents() {
        ProbeSession session = new ProbeSession();
        List<GameOccurrence> seen = record(session);
        session.start();
        session.perform("player-ana", Map.of("end", true));
        int before = seen.size();

        ActionResult result = session.perform("player-ana", Map.of());

        assertFalse(result.accepted());
        assertEquals(before, seen.size());
    }

    @Test
    void abandoningAnOpenMatchFinishesItWithAnAbandonedResult() {
        ProbeSession session = new ProbeSession();
        List<GameOccurrence> seen = record(session);
        session.start();

        session.abandon();

        GameOccurrence last = seen.get(seen.size() - 1);
        assertEquals(OccurrenceKind.MATCH_FINISHED, last.kind());
        assertEquals("ABANDONED", last.data().get("result"));
        assertTrue(session.finished());
    }

    @Test
    void abandoningTwiceDoesNotRepeatTheEndOfTheMatch() {
        ProbeSession session = new ProbeSession();
        List<GameOccurrence> seen = record(session);
        session.start();
        session.abandon();
        int afterFirst = seen.size();

        session.abandon();

        assertEquals(afterFirst, seen.size());
    }

    @Test
    void aFailingObserverDoesNotStopTheOthersNorTheGame() {
        ProbeSession session = new ProbeSession();
        session.addObserver(occurrence -> {
            throw new IllegalStateException("broken observer");
        });
        List<GameOccurrence> seen = record(session);

        session.start();

        assertEquals(3, seen.size());
    }
}
