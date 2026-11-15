package gameconsole.server.activity;

import gameconsole.api.GameObserver;
import gameconsole.api.GameOccurrence;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecentActivityObserver implements GameObserver {

    private final int capacity;
    private final Deque<Map<String, Object>> entries = new ArrayDeque<>();

    public RecentActivityObserver(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public synchronized void onOccurrence(GameOccurrence occurrence) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("at", occurrence.occurredAt().toString());
        entry.put("gameId", occurrence.gameId());
        entry.put("matchId", occurrence.matchId());
        entry.put("kind", occurrence.kind().name());
        entry.put("playerId", occurrence.playerId() == null ? "" : occurrence.playerId());
        entry.put("action", occurrence.action() == null ? "" : occurrence.action());
        entries.addFirst(entry);
        while (entries.size() > capacity) {
            entries.removeLast();
        }
    }

    public synchronized List<Map<String, Object>> latest(int limit) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            if (result.size() >= limit) {
                break;
            }
            result.add(entry);
        }
        return result;
    }
}
