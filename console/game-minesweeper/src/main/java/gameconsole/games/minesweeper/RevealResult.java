package gameconsole.games.minesweeper;

public record RevealResult(boolean hitMine, int cellsRevealed, int adjacentMines, boolean cascade) {
}
