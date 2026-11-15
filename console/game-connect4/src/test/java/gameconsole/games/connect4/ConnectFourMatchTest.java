package gameconsole.games.connect4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectFourMatchTest {

    private final HumanPlayer ana = new HumanPlayer("player-ana", "Ana", Disc.RED);
    private final HumanPlayer luis = new HumanPlayer("player-luis", "Luis", Disc.YELLOW);

    private ConnectFourMatch newMatch() {
        return new ConnectFourMatch(List.of(ana, luis), 0);
    }

    @Test
    void turnsAlternateBetweenPlayers() {
        ConnectFourMatch match = newMatch();

        match.play(ana, 0);

        assertSame(luis, match.currentPlayer());
    }

    @Test
    void playingOutOfTurnIsRejected() {
        ConnectFourMatch match = newMatch();

        assertThrows(IllegalStateException.class, () -> match.play(luis, 0));
    }

    @Test
    void fourVerticalDiscsWinTheMatch() {
        ConnectFourMatch match = newMatch();
        match.play(ana, 0);
        match.play(luis, 1);
        match.play(ana, 0);
        match.play(luis, 1);
        match.play(ana, 0);
        match.play(luis, 1);
        match.play(ana, 0);

        assertEquals(ConnectFourMatch.Status.WON, match.status());
        assertSame(ana, match.winner());
        assertEquals(4, match.winningLine().size());
        assertEquals(7, match.moveCount());
    }

    @Test
    void noMovesAreAllowedAfterTheMatchEnds() {
        ConnectFourMatch match = newMatch();
        for (int round = 0; round < 3; round++) {
            match.play(ana, 0);
            match.play(luis, 1);
        }
        match.play(ana, 0);

        assertThrows(IllegalStateException.class, () -> match.play(luis, 2));
    }

    @Test
    void aMatchInProgressHasNoWinner() {
        ConnectFourMatch match = newMatch();
        match.play(ana, 3);

        assertEquals(ConnectFourMatch.Status.IN_PROGRESS, match.status());
        assertNull(match.winner());
    }

    @Test
    void theOpponentOfAPlayerIsTheOtherOne() {
        ConnectFourMatch match = newMatch();

        assertSame(luis, match.opponentOf(ana));
        assertSame(ana, match.opponentOf(luis));
    }

    @Test
    void aFilledBoardWithoutLineEndsInADraw() {
        ConnectFourMatch match = newMatch();
        int[] order = {5, 4, 5, 0, 6, 2, 4, 5, 5, 0, 4, 1, 1, 0, 4, 5, 6, 5, 3, 1, 1, 2, 2, 6, 2, 6, 6, 3, 6, 2, 0, 3, 0, 3, 3, 4, 3, 1, 4, 2, 1, 0};
        for (int column : order) {
            match.play(match.currentPlayer(), column);
        }

        assertEquals(ConnectFourMatch.Status.DRAWN, match.status());
        assertNull(match.winner());
        assertEquals(42, match.moveCount());
    }
}
