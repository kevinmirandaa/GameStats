package gameconsole.games.memory;

public record FlipOutcome(Kind kind, int cardIndex, String symbol, int flipInTurn, int turnNumber, int otherCardIndex) {

    public enum Kind {
        FIRST_CARD,
        PAIR_FOUND,
        PAIR_MISSED
    }
}
