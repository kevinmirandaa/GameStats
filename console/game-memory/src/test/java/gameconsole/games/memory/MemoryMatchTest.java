package gameconsole.games.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MemoryMatchTest {

    private final MemoryPlayer ana = new MemoryPlayer("player-ana", "Ana");
    private final MemoryPlayer luis = new MemoryPlayer("player-luis", "Luis");

    private MemoryMatch newMatch(int pairs) {
        return new MemoryMatch(new Deck(pairs, new Random(7)), List.of(ana, luis));
    }

    private static int partnerOf(MemoryMatch match, int index) {
        String symbol = match.cards().get(index).symbol();
        for (int other = 0; other < match.cards().size(); other++) {
            if (other != index && match.cards().get(other).symbol().equals(symbol)) {
                return other;
            }
        }
        throw new IllegalStateException("sin pareja");
    }

    private static int strangerOf(MemoryMatch match, int index) {
        String symbol = match.cards().get(index).symbol();
        for (int other = 0; other < match.cards().size(); other++) {
            if (!match.cards().get(other).symbol().equals(symbol)) {
                return other;
            }
        }
        throw new IllegalStateException("sin carta distinta");
    }

    @Test
    void aDeckHasEveryPairTwiceAndIsShuffledDeterministicallyBySeed() {
        Deck first = new Deck(6, new Random(3));
        Deck second = new Deck(6, new Random(3));

        assertEquals(12, first.cards().size());
        assertEquals(first.cards().stream().map(Card::symbol).toList(), second.cards().stream().map(Card::symbol).toList());
        for (Card card : first.cards()) {
            assertEquals(2, first.cards().stream().filter(other -> other.symbol().equals(card.symbol())).count());
        }
    }

    @Test
    void aDeckRejectsUnsupportedPairCounts() {
        assertThrows(IllegalArgumentException.class, () -> new Deck(0, new Random()));
        assertThrows(IllegalArgumentException.class, () -> new Deck(99, new Random()));
    }

    @Test
    void theFirstFlipOnlyRevealsTheCard() {
        MemoryMatch match = newMatch(6);

        FlipOutcome outcome = match.flip(ana, 0);

        assertEquals(FlipOutcome.Kind.FIRST_CARD, outcome.kind());
        assertTrue(match.cards().get(0).isFaceUp());
        assertSame(ana, match.currentPlayer());
    }

    @Test
    void aMatchingPairScoresAndKeepsTheTurn() {
        MemoryMatch match = newMatch(6);
        match.flip(ana, 0);

        FlipOutcome outcome = match.flip(ana, partnerOf(match, 0));

        assertEquals(FlipOutcome.Kind.PAIR_FOUND, outcome.kind());
        assertEquals(1, ana.pairsFound());
        assertSame(ana, match.currentPlayer());
        assertTrue(match.cards().get(0).isMatched());
    }

    @Test
    void aMissedPairStaysVisibleUntilAcknowledgedAndThenPassesTheTurn() {
        MemoryMatch match = newMatch(6);
        match.flip(ana, 0);
        int stranger = strangerOf(match, 0);

        FlipOutcome outcome = match.flip(ana, stranger);

        assertEquals(FlipOutcome.Kind.PAIR_MISSED, outcome.kind());
        assertTrue(match.hasPendingMiss());
        assertTrue(match.cards().get(0).isFaceUp());
        assertSame(ana, match.currentPlayer());

        match.acknowledgeMiss();

        assertFalse(match.hasPendingMiss());
        assertFalse(match.cards().get(0).isFaceUp());
        assertSame(luis, match.currentPlayer());
    }

    @Test
    void flippingAfterAMissAcknowledgesItAutomaticallyForTheNextPlayer() {
        MemoryMatch match = newMatch(6);
        match.flip(ana, 0);
        match.flip(ana, strangerOf(match, 0));

        assertThrows(IllegalStateException.class, () -> match.flip(ana, 5));
        match.acknowledgeMiss();
        FlipOutcome outcome = match.flip(luis, 3);

        assertEquals(FlipOutcome.Kind.FIRST_CARD, outcome.kind());
        assertEquals(2, outcome.turnNumber());
    }

    @Test
    void matchedOrRevealedCardsCannotBeFlippedAgain() {
        MemoryMatch match = newMatch(6);
        match.flip(ana, 0);

        assertFalse(match.canFlip(0));
        assertThrows(IllegalArgumentException.class, () -> match.flip(ana, 0));
        assertFalse(match.canFlip(-1));
        assertFalse(match.canFlip(99));
    }

    @Test
    void theMatchEndsWhenEveryPairIsFoundAndTheLeaderIsReported() {
        MemoryMatch match = newMatch(2);
        for (int index = 0; index < match.cards().size(); index++) {
            if (match.canFlip(index)) {
                match.flip(ana, index);
                match.flip(ana, partnerOf(match, index));
            }
        }

        assertTrue(match.isOver());
        assertEquals(List.of(ana), match.leaders());
        assertEquals(2, match.totalAttempts());
    }

    @Test
    void equalScoresShareTheLead() {
        MemoryMatch match = newMatch(4);
        List<List<Integer>> pairs = pairsOf(match);

        match.flip(ana, pairs.get(0).get(0));
        match.flip(ana, pairs.get(0).get(1));
        match.flip(ana, pairs.get(1).get(0));
        match.flip(ana, pairs.get(1).get(1));
        match.flip(ana, pairs.get(2).get(0));
        match.flip(ana, pairs.get(3).get(0));
        match.acknowledgeMiss();
        match.flip(luis, pairs.get(2).get(0));
        match.flip(luis, pairs.get(2).get(1));
        match.flip(luis, pairs.get(3).get(0));
        match.flip(luis, pairs.get(3).get(1));

        assertTrue(match.isOver());
        assertEquals(2, ana.pairsFound());
        assertEquals(2, luis.pairsFound());
        assertEquals(List.of(ana, luis), match.leaders());
    }

    private static List<List<Integer>> pairsOf(MemoryMatch match) {
        java.util.Map<String, List<Integer>> positions = new java.util.LinkedHashMap<>();
        for (int index = 0; index < match.cards().size(); index++) {
            positions.computeIfAbsent(match.cards().get(index).symbol(), key -> new java.util.ArrayList<>()).add(index);
        }
        return new java.util.ArrayList<>(positions.values());
    }
}
