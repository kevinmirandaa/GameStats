package gameconsole.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gameconsole.server.sessions.PlayerIdentity;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerIdentityTest {

    @Test
    void namesBecomeStableLowercaseIdentifiersWithoutAccents() {
        assertEquals("player-jose-perez", PlayerIdentity.idFor("José Pérez"));
        assertEquals("player-ana", PlayerIdentity.idFor("  ANA  "));
    }

    @Test
    void symbolsAreCollapsedAndEmptyNamesGetAFallback() {
        assertEquals("player-a-b", PlayerIdentity.idFor("a!!b"));
        assertEquals("player-jugador", PlayerIdentity.idFor("!!!"));
    }

    @Test
    void longNamesAreCutToAnIdentifierOfBoundedLength() {
        String id = PlayerIdentity.idFor("x".repeat(100));

        assertTrue(id.length() <= "player-".length() + 30);
    }

    @Test
    void blankNamesAreReplacedByTheSeatPosition() {
        assertEquals("Jugador 2", PlayerIdentity.cleanName("   ", 2));
        assertEquals("Jugador 1", PlayerIdentity.cleanName(null, 1));
        assertEquals("Ana Maria", PlayerIdentity.cleanName("  Ana   Maria ", 1));
        assertEquals(24, PlayerIdentity.cleanName("y".repeat(60), 1).length());
    }

    @Test
    void sameNamesInOneMatchReceiveDistinctIdentifiers() {
        assertEquals(List.of("player-ana", "player-ana-2", "player-ana-3"), PlayerIdentity.distinctIds(List.of("Ana", "ana", "ANA")));
    }

    @Test
    void theSameNameAlwaysProducesTheSameIdentifierAcrossMatches() {
        assertEquals(PlayerIdentity.idFor("Kevin"), PlayerIdentity.idFor("Kevin"));
    }
}
