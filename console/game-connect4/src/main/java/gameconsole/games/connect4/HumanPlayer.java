package gameconsole.games.connect4;

public final class HumanPlayer extends ConnectFourPlayer {

    public HumanPlayer(String playerId, String displayName, Disc disc) {
        super(playerId, displayName, disc);
    }

    @Override
    public boolean isComputer() {
        return false;
    }
}
