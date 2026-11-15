package gameconsole.api;

import java.util.OptionalInt;

public final class ActionValues {

    private ActionValues() {
    }

    public static OptionalInt integer(Object value) {
        if (value instanceof Number number) {
            double asDouble = number.doubleValue();
            if (asDouble == Math.rint(asDouble) && asDouble >= Integer.MIN_VALUE && asDouble <= Integer.MAX_VALUE) {
                return OptionalInt.of((int) asDouble);
            }
        }
        return OptionalInt.empty();
    }
}
