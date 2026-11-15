package gameconsole.games.connect4;

import java.util.ArrayList;
import java.util.List;

public final class Board {

    public static final int ROWS = 6;
    public static final int COLUMNS = 7;
    private static final int WINNING_LENGTH = 4;
    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

    private final Disc[][] cells = new Disc[ROWS][COLUMNS];
    private int discCount;

    public Disc discAt(int row, int column) {
        return cells[row][column];
    }

    public boolean isColumnFull(int column) {
        return cells[0][column] != null;
    }

    public boolean isFull() {
        return discCount == ROWS * COLUMNS;
    }

    public int discCount() {
        return discCount;
    }

    public List<Integer> playableColumns() {
        List<Integer> playable = new ArrayList<>();
        for (int column = 0; column < COLUMNS; column++) {
            if (!isColumnFull(column)) {
                playable.add(column);
            }
        }
        return playable;
    }

    public Placement drop(Disc disc, int column) {
        if (column < 0 || column >= COLUMNS) {
            throw new IllegalArgumentException("Columna fuera del tablero: " + column);
        }
        if (isColumnFull(column)) {
            throw new IllegalArgumentException("La columna " + column + " esta llena");
        }
        int row = ROWS - 1;
        while (cells[row][column] != null) {
            row--;
        }
        cells[row][column] = disc;
        discCount++;
        return new Placement(row, column, disc);
    }

    public List<int[]> winningLineThrough(Placement placement) {
        for (int[] direction : DIRECTIONS) {
            List<int[]> line = lineAlong(placement, direction[0], direction[1]);
            if (line.size() >= WINNING_LENGTH) {
                return line;
            }
        }
        return List.of();
    }

    public List<List<Integer>> rowsAsCodes() {
        List<List<Integer>> rows = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            List<Integer> codes = new ArrayList<>();
            for (int column = 0; column < COLUMNS; column++) {
                codes.add(cells[row][column] == null ? 0 : cells[row][column].prologCode());
            }
            rows.add(codes);
        }
        return rows;
    }

    public String asText() {
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < ROWS; row++) {
            if (row > 0) {
                text.append('/');
            }
            for (int column = 0; column < COLUMNS; column++) {
                Disc disc = cells[row][column];
                text.append(disc == null ? '.' : disc == Disc.RED ? 'R' : 'Y');
            }
        }
        return text.toString();
    }

    private List<int[]> lineAlong(Placement placement, int rowStep, int columnStep) {
        List<int[]> line = new ArrayList<>();
        int backward = countMatching(placement, -rowStep, -columnStep);
        for (int offset = backward; offset >= 1; offset--) {
            line.add(new int[] {placement.row() - offset * rowStep, placement.column() - offset * columnStep});
        }
        line.add(new int[] {placement.row(), placement.column()});
        int forward = countMatching(placement, rowStep, columnStep);
        for (int offset = 1; offset <= forward; offset++) {
            line.add(new int[] {placement.row() + offset * rowStep, placement.column() + offset * columnStep});
        }
        return line;
    }

    private int countMatching(Placement placement, int rowStep, int columnStep) {
        int count = 0;
        int row = placement.row() + rowStep;
        int column = placement.column() + columnStep;
        while (row >= 0 && row < ROWS && column >= 0 && column < COLUMNS && cells[row][column] == placement.disc()) {
            count++;
            row += rowStep;
            column += columnStep;
        }
        return count;
    }
}
