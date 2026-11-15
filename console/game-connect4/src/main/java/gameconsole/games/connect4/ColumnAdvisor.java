package gameconsole.games.connect4;

import gameconsole.api.LogicUnavailableException;

public interface ColumnAdvisor {

    ColumnAdvice advise(Board board, Disc disc, int searchDepth) throws LogicUnavailableException;
}
