package gameconsole.games.minesweeper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class MineFieldTest {

    private static MineField field() {
        return new MineField(9, 9, 10, new Random(5));
    }

    private static int mineTotal(MineField field) {
        int total = 0;
        for (int row = 0; row < field.rows(); row++) {
            for (int column = 0; column < field.columns(); column++) {
                if (field.isMine(row, column)) {
                    total++;
                }
            }
        }
        return total;
    }

    @Test
    void theFirstRevealIsNeverAMineNorNextToOne() {
        for (long seed = 0; seed < 50; seed++) {
            MineField field = new MineField(9, 9, 10, new Random(seed));

            RevealResult result = field.reveal(4, 4);

            assertFalse(result.hitMine());
            assertEquals(0, result.adjacentMines());
            assertTrue(result.cascade());
        }
    }

    @Test
    void minesAreExactlyTheRequestedAmountAfterTheFirstReveal() {
        MineField field = field();
        field.reveal(0, 0);

        assertEquals(10, mineTotal(field));
    }

    @Test
    void adjacentCountsMatchTheNeighborMines() {
        MineField field = field();
        field.reveal(4, 4);

        for (int row = 0; row < field.rows(); row++) {
            for (int column = 0; column < field.columns(); column++) {
                int expected = 0;
                for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
                    for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                        int neighborRow = row + rowOffset;
                        int neighborColumn = column + columnOffset;
                        if ((rowOffset != 0 || columnOffset != 0) && field.isInside(neighborRow, neighborColumn) && field.isMine(neighborRow, neighborColumn)) {
                            expected++;
                        }
                    }
                }
                assertEquals(expected, field.adjacentMines(row, column));
            }
        }
    }

    @Test
    void revealingAMineReportsTheHit() {
        MineField field = field();
        field.reveal(4, 4);
        int mineRow = -1;
        int mineColumn = -1;
        for (int row = 0; row < field.rows() && mineRow < 0; row++) {
            for (int column = 0; column < field.columns(); column++) {
                if (field.isMine(row, column)) {
                    mineRow = row;
                    mineColumn = column;
                    break;
                }
            }
        }

        RevealResult result = field.reveal(mineRow, mineColumn);

        assertTrue(result.hitMine());
    }

    @Test
    void revealingTwiceOrRevealingAFlaggedCellIsRejected() {
        MineField field = field();
        field.reveal(4, 4);

        assertThrows(IllegalStateException.class, () -> field.reveal(4, 4));
        int hiddenRow = 0;
        int hiddenColumn = 0;
        while (field.isRevealed(hiddenRow, hiddenColumn)) {
            hiddenColumn++;
        }
        field.toggleFlag(hiddenRow, hiddenColumn);
        int row = hiddenRow;
        int column = hiddenColumn;
        assertThrows(IllegalStateException.class, () -> field.reveal(row, column));
    }

    @Test
    void flagsToggleAndAreCounted() {
        MineField field = field();

        assertTrue(field.toggleFlag(0, 0));
        assertEquals(1, field.flagCount());
        assertFalse(field.toggleFlag(0, 0));
        assertEquals(0, field.flagCount());
    }

    @Test
    void aRevealedCellCannotBeFlagged() {
        MineField field = field();
        field.reveal(4, 4);

        assertThrows(IllegalStateException.class, () -> field.toggleFlag(4, 4));
    }

    @Test
    void revealingEverySafeCellClearsTheField() {
        MineField field = field();
        field.reveal(4, 4);
        for (int row = 0; row < field.rows(); row++) {
            for (int column = 0; column < field.columns(); column++) {
                if (!field.isMine(row, column) && !field.isRevealed(row, column)) {
                    field.reveal(row, column);
                }
            }
        }

        assertTrue(field.isCleared());
        assertEquals(81 - 10, field.revealedSafeCells());
    }

    @Test
    void invalidMineCountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MineField(9, 9, 0, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new MineField(9, 9, 80, new Random()));
    }

    @Test
    void aCascadeDoesNotRevealCellsMarkedWithAFlag() {
        MineField field = new MineField(9, 9, 10, new Random(5));
        field.toggleFlag(0, 0);

        field.reveal(4, 4);

        assertFalse(field.isRevealed(0, 0));
        assertTrue(field.isFlagged(0, 0));
        assertEquals(1, field.flagCount());
    }
}
