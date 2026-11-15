package gameconsole.games.connect4;

import gameconsole.api.LogicEngine;
import gameconsole.api.LogicUnavailableException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PrologColumnAdvisor implements ColumnAdvisor {

    private static final String QUERY_NAME = "decide";

    private final LogicEngine engine;

    public PrologColumnAdvisor(LogicEngine engine) {
        this.engine = engine;
    }

    @Override
    public ColumnAdvice advise(Board board, Disc disc, int searchDepth) throws LogicUnavailableException {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("board", board.rowsAsCodes());
        arguments.put("player", disc.prologCode());
        arguments.put("depth", searchDepth);

        long startedAt = System.nanoTime();
        Map<String, Object> answer = engine.ask(QUERY_NAME, arguments);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        return new ColumnAdvice(
                number(answer, "column").intValue(),
                number(answer, "score"),
                number(answer, "nodes"),
                number(answer, "depth").intValue(),
                candidatesOf(answer),
                elapsedMillis);
    }

    private static Long number(Map<String, Object> answer, String field) throws LogicUnavailableException {
        if (answer.get(field) instanceof Number value) {
            return value.longValue();
        }
        throw new LogicUnavailableException("La respuesta de Prolog no incluye el campo " + field);
    }

    private static List<ColumnAdvice.Candidate> candidatesOf(Map<String, Object> answer) {
        List<ColumnAdvice.Candidate> candidates = new ArrayList<>();
        if (answer.get("candidates") instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> candidate
                        && candidate.get("column") instanceof Number column
                        && candidate.get("score") instanceof Number score) {
                    candidates.add(new ColumnAdvice.Candidate(column.intValue(), score.longValue()));
                }
            }
        }
        return candidates;
    }
}
