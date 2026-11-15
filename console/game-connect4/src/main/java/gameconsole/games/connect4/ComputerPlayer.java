package gameconsole.games.connect4;

import gameconsole.api.LogicUnavailableException;

public final class ComputerPlayer extends ConnectFourPlayer {

    public static final String COMPUTER_PLAYER_ID = "ai-prolog-connect4";

    private final ColumnAdvisor advisor;
    private final int searchDepth;

    public ComputerPlayer(Disc disc, ColumnAdvisor advisor, int searchDepth) {
        super(COMPUTER_PLAYER_ID, "Computadora Prolog", disc);
        this.advisor = advisor;
        this.searchDepth = searchDepth;
    }

    public ColumnAdvice chooseColumn(Board board) throws LogicUnavailableException {
        return advisor.advise(board, disc(), searchDepth);
    }

    public int searchDepth() {
        return searchDepth;
    }

    @Override
    public boolean isComputer() {
        return true;
    }
}
