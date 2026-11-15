package gameconsole.games.connect4;

import java.util.List;

public final class ConnectFourMatch {

    public enum Status {
        IN_PROGRESS,
        WON,
        DRAWN
    }

    private final Board board = new Board();
    private final List<ConnectFourPlayer> players;
    private int currentIndex;
    private int moveCount;
    private Status status = Status.IN_PROGRESS;
    private ConnectFourPlayer winner;
    private List<int[]> winningLine = List.of();

    public ConnectFourMatch(List<ConnectFourPlayer> players, int firstPlayerIndex) {
        this.players = List.copyOf(players);
        this.currentIndex = firstPlayerIndex;
    }

    public Board board() {
        return board;
    }

    public List<ConnectFourPlayer> players() {
        return players;
    }

    public ConnectFourPlayer currentPlayer() {
        return players.get(currentIndex);
    }

    public ConnectFourPlayer playerById(String playerId) {
        return players.stream().filter(player -> player.playerId().equals(playerId)).findFirst().orElse(null);
    }

    public ConnectFourPlayer opponentOf(ConnectFourPlayer player) {
        return players.stream().filter(other -> other != player).findFirst().orElseThrow();
    }

    public Status status() {
        return status;
    }

    public ConnectFourPlayer winner() {
        return winner;
    }

    public List<int[]> winningLine() {
        return winningLine;
    }

    public int moveCount() {
        return moveCount;
    }

    public Placement play(ConnectFourPlayer player, int column) {
        if (status != Status.IN_PROGRESS) {
            throw new IllegalStateException("La partida ya termino");
        }
        if (player != currentPlayer()) {
            throw new IllegalStateException("No es el turno de " + player.displayName());
        }
        Placement placement = board.drop(player.disc(), column);
        moveCount++;
        List<int[]> line = board.winningLineThrough(placement);
        if (!line.isEmpty()) {
            status = Status.WON;
            winner = player;
            winningLine = line;
        } else if (board.isFull()) {
            status = Status.DRAWN;
        } else {
            currentIndex = (currentIndex + 1) % players.size();
        }
        return placement;
    }
}
