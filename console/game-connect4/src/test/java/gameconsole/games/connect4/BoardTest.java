package gameconsole.games.connect4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BoardTest {

    @Test
    void discsFallToTheLowestFreeRow() {
        Board board = new Board();

        Placement first = board.drop(Disc.RED, 3);
        Placement second = board.drop(Disc.YELLOW, 3);

        assertEquals(Board.ROWS - 1, first.row());
        assertEquals(Board.ROWS - 2, second.row());
    }

    @Test
    void aFullColumnRejectsMoreDiscs() {
        Board board = new Board();
        for (int count = 0; count < Board.ROWS; count++) {
            board.drop(Disc.RED, 0);
        }

        assertTrue(board.isColumnFull(0));
        assertThrows(IllegalArgumentException.class, () -> board.drop(Disc.YELLOW, 0));
    }

    @Test
    void columnsOutsideTheBoardAreRejected() {
        Board board = new Board();

        assertThrows(IllegalArgumentException.class, () -> board.drop(Disc.RED, -1));
        assertThrows(IllegalArgumentException.class, () -> board.drop(Disc.RED, Board.COLUMNS));
    }

    @Test
    void fourInARowHorizontallyIsDetectedFromTheLastDisc() {
        Board board = new Board();
        board.drop(Disc.RED, 0);
        board.drop(Disc.RED, 1);
        board.drop(Disc.RED, 2);
        Placement last = board.drop(Disc.RED, 3);

        assertEquals(4, board.winningLineThrough(last).size());
    }

    @Test
    void fourInARowVerticallyIsDetected() {
        Board board = new Board();
        Placement last = null;
        for (int count = 0; count < 4; count++) {
            last = board.drop(Disc.YELLOW, 5);
        }

        assertEquals(4, board.winningLineThrough(last).size());
    }

    @Test
    void fourInARowDiagonallyIsDetected() {
        Board board = new Board();
        board.drop(Disc.RED, 0);
        board.drop(Disc.YELLOW, 1);
        board.drop(Disc.RED, 1);
        board.drop(Disc.YELLOW, 2);
        board.drop(Disc.YELLOW, 2);
        board.drop(Disc.RED, 2);
        board.drop(Disc.YELLOW, 3);
        board.drop(Disc.YELLOW, 3);
        board.drop(Disc.YELLOW, 3);
        Placement last = board.drop(Disc.RED, 3);

        assertEquals(4, board.winningLineThrough(last).size());
    }

    @Test
    void threeInARowIsNotAWin() {
        Board board = new Board();
        board.drop(Disc.RED, 0);
        board.drop(Disc.RED, 1);
        Placement last = board.drop(Disc.RED, 2);

        assertTrue(board.winningLineThrough(last).isEmpty());
    }

    @Test
    void anInterruptedLineIsNotAWin() {
        Board board = new Board();
        board.drop(Disc.RED, 0);
        board.drop(Disc.RED, 1);
        board.drop(Disc.YELLOW, 2);
        Placement last = board.drop(Disc.RED, 3);

        assertTrue(board.winningLineThrough(last).isEmpty());
    }

    @Test
    void playableColumnsExcludeFullOnes() {
        Board board = new Board();
        for (int count = 0; count < Board.ROWS; count++) {
            board.drop(Disc.RED, 4);
        }

        assertFalse(board.playableColumns().contains(4));
        assertEquals(Board.COLUMNS - 1, board.playableColumns().size());
    }

    @Test
    void rowsAreExportedTopDownWithPrologCodes() {
        Board board = new Board();
        board.drop(Disc.RED, 0);
        board.drop(Disc.YELLOW, 1);

        List<List<Integer>> rows = board.rowsAsCodes();

        assertEquals(Board.ROWS, rows.size());
        assertEquals(List.of(1, 2, 0, 0, 0, 0, 0), rows.get(Board.ROWS - 1));
        assertEquals(List.of(0, 0, 0, 0, 0, 0, 0), rows.get(0));
    }

    @Test
    void textExportMarksEachDisc() {
        Board board = new Board();
        board.drop(Disc.RED, 0);
        board.drop(Disc.YELLOW, 6);

        String text = board.asText();

        assertTrue(text.endsWith("R.....Y"));
        assertEquals(Board.ROWS * Board.COLUMNS + Board.ROWS - 1, text.length());
    }

    @Test
    void aBoardWithEverySlotUsedIsFull() {
        Board board = new Board();
        for (int column = 0; column < Board.COLUMNS; column++) {
            for (int row = 0; row < Board.ROWS; row++) {
                board.drop(Disc.RED, column);
            }
        }

        assertTrue(board.isFull());
        assertEquals(Board.ROWS * Board.COLUMNS, board.discCount());
    }
}
