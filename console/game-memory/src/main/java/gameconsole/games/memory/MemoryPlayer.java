package gameconsole.games.memory;

public final class MemoryPlayer {

    private final String playerId;
    private final String displayName;
    private int pairsFound;

    public MemoryPlayer(String playerId, String displayName) {
        this.playerId = playerId;
        this.displayName = displayName;
    }

    public String playerId() {
        return playerId;
    }

    public String displayName() {
        return displayName;
    }

    public int pairsFound() {
        return pairsFound;
    }

    void addPair() {
        pairsFound++;
    }
}
