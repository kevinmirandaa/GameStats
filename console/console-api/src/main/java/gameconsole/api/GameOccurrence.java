package gameconsole.api;

import java.time.Instant;
import java.util.Map;

public record GameOccurrence(
        String gameId,
        String gameVersion,
        String matchId,
        OccurrenceKind kind,
        String playerId,
        String action,
        Map<String, Object> data,
        Instant occurredAt) {

    public GameOccurrence {
        data = Map.copyOf(data);
    }
}
