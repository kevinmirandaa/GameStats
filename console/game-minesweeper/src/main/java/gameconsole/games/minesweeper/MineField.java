package gameconsole.games.minesweeper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public final class MineField {

    private final int rows;
    private final int columns;
    private final int mineCount;
    private final Random random;
    private final Cell[][] cells;
    private boolean minesPlaced;
    private int revealedSafeCells;
    private int flagCount;

    public MineField(int rows, int columns, int mineCount, Random random) {
        if (mineCount < 1 || mineCount >= rows * columns - 9) {
            throw new IllegalArgumentException("Cantidad de minas invalida: " + mineCount);
        }
        this.rows = rows;
        this.columns = columns;
        this.mineCount = mineCount;
        this.random = random;
        this.cells = new Cell[rows][columns];
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                cells[row][column] = new Cell();
            }
        }
    }

    public int rows() {
        return rows;
    }

    public int columns() {
        return columns;
    }

    public int mineCount() {
        return mineCount;
    }

    public int flagCount() {
        return flagCount;
    }

    public int revealedSafeCells() {
        return revealedSafeCells;
    }

    public boolean isCleared() {
        return revealedSafeCells == rows * columns - mineCount;
    }

    public boolean isInside(int row, int column) {
        return row >= 0 && row < rows && column >= 0 && column < columns;
    }

    public boolean isRevealed(int row, int column) {
        return cells[row][column].isRevealed();
    }

    public boolean isFlagged(int row, int column) {
        return cells[row][column].isFlagged();
    }

    public boolean isMine(int row, int column) {
        return cells[row][column].isMine();
    }

    public int adjacentMines(int row, int column) {
        return cells[row][column].adjacentMines();
    }

    public boolean toggleFlag(int row, int column) {
        Cell cell = cells[row][column];
        if (cell.isRevealed()) {
            throw new IllegalStateException("No se puede marcar una casilla descubierta");
        }
        cell.toggleFlag();
        flagCount += cell.isFlagged() ? 1 : -1;
        return cell.isFlagged();
    }

    public RevealResult reveal(int row, int column) {
        Cell target = cells[row][column];
        if (target.isRevealed()) {
            throw new IllegalStateException("La casilla ya esta descubierta");
        }
        if (target.isFlagged()) {
            throw new IllegalStateException("La casilla tiene bandera");
        }
        if (!minesPlaced) {
            placeMinesAvoiding(row, column);
        }
        if (target.isMine()) {
            target.reveal();
            return new RevealResult(true, 0, 0, false);
        }
        int revealed = floodReveal(row, column);
        return new RevealResult(false, revealed, target.adjacentMines(), revealed > 1);
    }

    private int floodReveal(int startRow, int startColumn) {
        int revealed = 0;
        Deque<int[]> pending = new ArrayDeque<>();
        pending.push(new int[] {startRow, startColumn});
        while (!pending.isEmpty()) {
            int[] position = pending.pop();
            Cell cell = cells[position[0]][position[1]];
            if (cell.isRevealed() || cell.isMine() || cell.isFlagged()) {
                continue;
            }
            cell.reveal();
            revealedSafeCells++;
            revealed++;
            if (cell.adjacentMines() == 0) {
                for (int[] neighbor : neighborsOf(position[0], position[1])) {
                    pending.push(neighbor);
                }
            }
        }
        return revealed;
    }

    private void placeMinesAvoiding(int safeRow, int safeColumn) {
        List<int[]> candidates = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                boolean nearSafe = Math.abs(row - safeRow) <= 1 && Math.abs(column - safeColumn) <= 1;
                if (!nearSafe) {
                    candidates.add(new int[] {row, column});
                }
            }
        }
        Collections.shuffle(candidates, random);
        for (int index = 0; index < mineCount; index++) {
            int[] position = candidates.get(index);
            cells[position[0]][position[1]].placeMine();
        }
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int adjacent = 0;
                for (int[] neighbor : neighborsOf(row, column)) {
                    if (cells[neighbor[0]][neighbor[1]].isMine()) {
                        adjacent++;
                    }
                }
                cells[row][column].setAdjacentMines(adjacent);
            }
        }
        minesPlaced = true;
    }

    private List<int[]> neighborsOf(int row, int column) {
        List<int[]> neighbors = new ArrayList<>();
        for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
            for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                if ((rowOffset != 0 || columnOffset != 0) && isInside(row + rowOffset, column + columnOffset)) {
                    neighbors.add(new int[] {row + rowOffset, column + columnOffset});
                }
            }
        }
        return neighbors;
    }
}
