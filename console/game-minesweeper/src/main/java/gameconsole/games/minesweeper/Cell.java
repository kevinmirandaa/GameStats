package gameconsole.games.minesweeper;

final class Cell {

    private boolean mine;
    private int adjacentMines;
    private boolean revealed;
    private boolean flagged;

    boolean isMine() {
        return mine;
    }

    int adjacentMines() {
        return adjacentMines;
    }

    boolean isRevealed() {
        return revealed;
    }

    boolean isFlagged() {
        return flagged;
    }

    void placeMine() {
        mine = true;
    }

    void setAdjacentMines(int adjacentMines) {
        this.adjacentMines = adjacentMines;
    }

    void reveal() {
        revealed = true;
        flagged = false;
    }

    void toggleFlag() {
        flagged = !flagged;
    }
}
