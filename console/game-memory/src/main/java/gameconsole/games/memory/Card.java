package gameconsole.games.memory;

public final class Card {

    private final String symbol;
    private boolean faceUp;
    private boolean matched;

    public Card(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public boolean isFaceUp() {
        return faceUp;
    }

    public boolean isMatched() {
        return matched;
    }

    public void reveal() {
        faceUp = true;
    }

    public void hide() {
        faceUp = false;
    }

    public void markMatched() {
        matched = true;
        faceUp = true;
    }
}
