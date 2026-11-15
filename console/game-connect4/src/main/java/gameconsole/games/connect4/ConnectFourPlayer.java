package gameconsole.games.connect4;

public abstract class ConnectFourPlayer {

    private final String playerId;
    private final String displayName;
    private final Disc disc;

    protected ConnectFourPlayer(String playerId, String displayName, Disc disc) {
        this.playerId = playerId;
        this.displayName = displayName;
        this.disc = disc;
    }

    public String playerId() {
        return playerId;
    }

    public String displayName() {
        return displayName;
    }

    public Disc disc() {
        return disc;
    }

    public abstract boolean isComputer();
}
