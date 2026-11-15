package gameconsole.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gameconsole.api.GameOccurrence;
import gameconsole.api.OccurrenceKind;
import gameconsole.server.events.EventTranslator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventTranslatorTest {

    private static GameOccurrence occurrence(OccurrenceKind kind, String playerId, String action) {
        return new GameOccurrence("connect4", "1.0", "connect4-abc123", kind, playerId, action, Map.of("column", 3), Instant.parse("2026-11-14T10:15:30.123456Z"));
    }

    @Test
    void anActionBecomesAGameActionEventWithEveryEnvelopeField() {
        Map<String, Object> event = EventTranslator.toEvent(occurrence(OccurrenceKind.PLAYER_ACTION, "player-ana", "MOVE"));

        assertEquals("1.0", event.get("schemaVersion"));
        assertEquals("connect4", event.get("gameId"));
        assertEquals("1.0", event.get("gameVersion"));
        assertEquals("connect4-abc123", event.get("matchId"));
        assertEquals("player-ana", event.get("playerId"));
        assertEquals("GAME_ACTION", event.get("type"));
        assertEquals("MOVE", event.get("action"));
        assertEquals("2026-11-14T10:15:30.123456Z", event.get("timestamp"));
        assertEquals(Map.of("column", 3), event.get("data"));
        UUID.fromString((String) event.get("eventId"));
    }

    @Test
    void lifecycleKindsKeepTheirNames() {
        assertEquals("MATCH_STARTED", EventTranslator.toEvent(occurrence(OccurrenceKind.MATCH_STARTED, null, null)).get("type"));
        assertEquals("MATCH_FINISHED", EventTranslator.toEvent(occurrence(OccurrenceKind.MATCH_FINISHED, null, null)).get("type"));
        assertEquals("PLAYER_JOINED", EventTranslator.toEvent(occurrence(OccurrenceKind.PLAYER_JOINED, "player-ana", null)).get("type"));
        assertEquals("PLAYER_LEFT", EventTranslator.toEvent(occurrence(OccurrenceKind.PLAYER_LEFT, "player-ana", null)).get("type"));
    }

    @Test
    void matchLevelEventsOmitThePlayerAndTheAction() {
        Map<String, Object> event = EventTranslator.toEvent(occurrence(OccurrenceKind.MATCH_STARTED, null, null));

        assertFalse(event.containsKey("playerId"));
        assertFalse(event.containsKey("action"));
    }

    @Test
    void everyEventGetsItsOwnIdentifier() {
        GameOccurrence same = occurrence(OccurrenceKind.PLAYER_ACTION, "player-ana", "MOVE");

        assertNotEquals(EventTranslator.toEvent(same).get("eventId"), EventTranslator.toEvent(same).get("eventId"));
    }

    @Test
    void theObserverHandsTheTranslatedEventToThePublisher() {
        List<Map<String, Object>> published = new ArrayList<>();
        EventTranslator translator = new EventTranslator(published::add);

        translator.onOccurrence(occurrence(OccurrenceKind.PLAYER_ACTION, "player-ana", "MOVE"));

        assertEquals(1, published.size());
        assertTrue(published.get(0).containsKey("eventId"));
    }
}
