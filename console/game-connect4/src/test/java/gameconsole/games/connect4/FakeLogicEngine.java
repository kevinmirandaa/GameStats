package gameconsole.games.connect4;

import gameconsole.api.LogicEngine;
import gameconsole.api.LogicUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class FakeLogicEngine implements LogicEngine {

    private final List<Map<String, Object>> requests = new ArrayList<>();
    private boolean unavailable;

    void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    List<Map<String, Object>> requests() {
        return requests;
    }

    @Override
    public Map<String, Object> ask(String queryName, Map<String, Object> arguments) throws LogicUnavailableException {
        if (unavailable) {
            throw new LogicUnavailableException("sin conexion");
        }
        requests.add(arguments);
        @SuppressWarnings("unchecked")
        List<List<Integer>> rows = (List<List<Integer>>) arguments.get("board");
        int column = 0;
        for (int candidate = 0; candidate < Board.COLUMNS; candidate++) {
            if (rows.get(0).get(candidate) == 0) {
                column = candidate;
                break;
            }
        }
        return Map.of(
                "column", column,
                "score", 12,
                "nodes", 345,
                "depth", arguments.get("depth"),
                "candidates", List.of(Map.of("column", column, "score", 12), Map.of("column", 6, "score", -4)));
    }
}
