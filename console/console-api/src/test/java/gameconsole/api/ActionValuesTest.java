package gameconsole.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActionValuesTest {

    @Test
    void wholeNumbersAreAccepted() {
        assertEquals(3, ActionValues.integer(3).getAsInt());
        assertEquals(4, ActionValues.integer(4.0).getAsInt());
        assertEquals(-1, ActionValues.integer(-1L).getAsInt());
    }

    @Test
    void fractionsHugeValuesAndOtherTypesAreRejected() {
        assertTrue(ActionValues.integer(2.9).isEmpty());
        assertTrue(ActionValues.integer(1e30).isEmpty());
        assertTrue(ActionValues.integer(4294967296L).isEmpty());
        assertTrue(ActionValues.integer("3").isEmpty());
        assertTrue(ActionValues.integer(null).isEmpty());
        assertTrue(ActionValues.integer(Double.NaN).isEmpty());
    }
}
