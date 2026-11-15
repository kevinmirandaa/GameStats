package gameconsole.games.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class Deck {

    private static final List<String> SYMBOLS = List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L");

    private final List<Card> cards = new ArrayList<>();

    public Deck(int pairCount, Random random) {
        if (pairCount < 1 || pairCount > SYMBOLS.size()) {
            throw new IllegalArgumentException("Cantidad de parejas no soportada: " + pairCount);
        }
        for (int index = 0; index < pairCount; index++) {
            cards.add(new Card(SYMBOLS.get(index)));
            cards.add(new Card(SYMBOLS.get(index)));
        }
        Collections.shuffle(cards, random);
    }

    public List<Card> cards() {
        return Collections.unmodifiableList(cards);
    }

    public static int symbolPosition(String symbol) {
        return SYMBOLS.indexOf(symbol);
    }
}
