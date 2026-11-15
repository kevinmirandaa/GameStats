package gameconsole.server.events;

import gameconsole.api.GameObserver;
import gameconsole.api.GameOccurrence;
import gameconsole.api.OccurrenceKind;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class EventTranslator implements GameObserver {

    private static final String SCHEMA_VERSION = "1.0";

    private final EventPublisher publisher;

    public EventTranslator(EventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void onOccurrence(GameOccurrence occurrence) {
        publisher.publish(toEvent(occurrence));
    }

    public static Map<String, Object> toEvent(GameOccurrence occurrence) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("schemaVersion", SCHEMA_VERSION);
        event.put("eventId", UUID.randomUUID().toString());
        event.put("timestamp", occurrence.occurredAt().toString());
        event.put("gameId", occurrence.gameId());
        event.put("gameVersion", occurrence.gameVersion());
        event.put("matchId", occurrence.matchId());
        if (occurrence.playerId() != null) {
            event.put("playerId", occurrence.playerId());
        }
        event.put("type", typeOf(occurrence.kind()));
        if (occurrence.action() != null) {
            event.put("action", occurrence.action());
        }
        event.put("data", occurrence.data());
        return event;
    }

    private static String typeOf(OccurrenceKind kind) {
        return switch (kind) {
            case MATCH_STARTED -> "MATCH_STARTED";
            case MATCH_FINISHED -> "MATCH_FINISHED";
            case PLAYER_JOINED -> "PLAYER_JOINED";
            case PLAYER_LEFT -> "PLAYER_LEFT";
            case PLAYER_ACTION -> "GAME_ACTION";
        };
    }
}
