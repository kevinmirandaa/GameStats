package gameconsole.games.connect4;

import java.util.List;

public record ColumnAdvice(int column, long score, long nodesSearched, int searchDepth, List<Candidate> candidates, long elapsedMillis) {

    public record Candidate(int column, long score) {
    }
}
