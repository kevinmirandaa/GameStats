package gameconsole.games.memory;

import java.util.List;

public final class MemoryMatch {

    private static final int NO_CARD = -1;

    private final List<Card> cards;
    private final List<MemoryPlayer> players;
    private int currentIndex;
    private int firstFlippedIndex = NO_CARD;
    private int missedFirstIndex = NO_CARD;
    private int missedSecondIndex = NO_CARD;
    private int turnNumber = 1;
    private int pairsFound;
    private int attempts;

    public MemoryMatch(Deck deck, List<MemoryPlayer> players) {
        this.cards = deck.cards();
        this.players = List.copyOf(players);
    }

    public List<Card> cards() {
        return cards;
    }

    public List<MemoryPlayer> players() {
        return players;
    }

    public MemoryPlayer currentPlayer() {
        return players.get(currentIndex);
    }

    public MemoryPlayer playerById(String playerId) {
        return players.stream().filter(player -> player.playerId().equals(playerId)).findFirst().orElse(null);
    }

    public boolean hasPendingMiss() {
        return missedFirstIndex != NO_CARD;
    }

    public boolean isOver() {
        return pairsFound * 2 == cards.size();
    }

    public int totalAttempts() {
        return attempts;
    }

    public void acknowledgeMiss() {
        if (!hasPendingMiss()) {
            return;
        }
        cards.get(missedFirstIndex).hide();
        cards.get(missedSecondIndex).hide();
        missedFirstIndex = NO_CARD;
        missedSecondIndex = NO_CARD;
        currentIndex = (currentIndex + 1) % players.size();
        turnNumber++;
    }

    public boolean canFlip(int cardIndex) {
        if (cardIndex < 0 || cardIndex >= cards.size() || isOver()) {
            return false;
        }
        Card card = cards.get(cardIndex);
        return !card.isMatched() && !card.isFaceUp();
    }

    public FlipOutcome flip(MemoryPlayer player, int cardIndex) {
        acknowledgeMiss();
        if (player != currentPlayer()) {
            throw new IllegalStateException("No es el turno de " + player.displayName());
        }
        if (!canFlip(cardIndex)) {
            throw new IllegalArgumentException("La carta " + cardIndex + " no se puede voltear");
        }
        Card card = cards.get(cardIndex);
        card.reveal();
        if (firstFlippedIndex == NO_CARD) {
            firstFlippedIndex = cardIndex;
            return new FlipOutcome(FlipOutcome.Kind.FIRST_CARD, cardIndex, card.symbol(), 1, turnNumber, NO_CARD);
        }
        int firstIndex = firstFlippedIndex;
        firstFlippedIndex = NO_CARD;
        attempts++;
        Card first = cards.get(firstIndex);
        if (first.symbol().equals(card.symbol())) {
            first.markMatched();
            card.markMatched();
            pairsFound++;
            player.addPair();
            return new FlipOutcome(FlipOutcome.Kind.PAIR_FOUND, cardIndex, card.symbol(), 2, turnNumber, firstIndex);
        }
        missedFirstIndex = firstIndex;
        missedSecondIndex = cardIndex;
        return new FlipOutcome(FlipOutcome.Kind.PAIR_MISSED, cardIndex, card.symbol(), 2, turnNumber, firstIndex);
    }

    public List<MemoryPlayer> leaders() {
        int best = players.stream().mapToInt(MemoryPlayer::pairsFound).max().orElse(0);
        return players.stream().filter(player -> player.pairsFound() == best).toList();
    }
}
