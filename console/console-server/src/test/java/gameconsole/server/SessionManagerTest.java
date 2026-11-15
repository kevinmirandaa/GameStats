package gameconsole.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gameconsole.api.GameOccurrence;
import gameconsole.api.GameSession;
import gameconsole.api.OccurrenceKind;
import gameconsole.api.SessionServices;
import gameconsole.server.plugins.GameCatalog;
import gameconsole.server.plugins.PluginLoader;
import gameconsole.server.sessions.SessionManager;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionManagerTest {

    @TempDir
    Path plugins;

    private PluginLoader loader;
    private final List<GameOccurrence> seen = new CopyOnWriteArrayList<>();
    private SessionManager manager;

    @BeforeEach
    void setUp() throws IOException {
        DynamicPluginJar.create(plugins, "echo.jar");
        loader = new PluginLoader(plugins, getClass().getClassLoader());
        manager = new SessionManager(new GameCatalog(loader.load()), new SessionServices(null), List.of(seen::add), 3);
    }

    @AfterEach
    void tearDown() {
        loader.close();
    }

    @Test
    void creatingASessionStartsItAfterTheObserversAreAttached() {
        GameSession session = manager.create("echo", "SOLO", List.of("Ana"), Map.of());

        assertEquals(List.of(OccurrenceKind.MATCH_STARTED, OccurrenceKind.PLAYER_JOINED), seen.stream().map(GameOccurrence::kind).toList());
        assertTrue(session.matchId().startsWith("echo-"));
        assertEquals("player-ana", seen.get(1).playerId());
    }

    @Test
    void everySessionGetsAUniqueMatchId() {
        GameSession first = manager.create("echo", "SOLO", List.of("Ana"), Map.of());
        GameSession second = manager.create("echo", "SOLO", List.of("Ana"), Map.of());

        assertNotEquals(first.matchId(), second.matchId());
        assertEquals(2, manager.all().size());
    }

    @Test
    void concurrentSessionsKeepTheirEventsApart() {
        GameSession first = manager.create("echo", "SOLO", List.of("Ana"), Map.of());
        GameSession second = manager.create("echo", "SOLO", List.of("Luis"), Map.of());

        first.perform("player-ana", Map.of());
        first.perform("player-ana", Map.of());
        second.perform("player-luis", Map.of());

        long firstTouches = seen.stream().filter(o -> o.matchId().equals(first.matchId()) && "TOUCH".equals(o.action())).count();
        long secondTouches = seen.stream().filter(o -> o.matchId().equals(second.matchId()) && "TOUCH".equals(o.action())).count();
        assertEquals(2, firstTouches);
        assertEquals(1, secondTouches);
    }

    @Test
    void unknownGamesAndModesAreRejected() {
        assertThrows(SessionManager.InvalidSessionRequestException.class, () -> manager.create("nope", "SOLO", List.of("Ana"), Map.of()));
        assertThrows(SessionManager.InvalidSessionRequestException.class, () -> manager.create("echo", "TEAM", List.of("Ana"), Map.of()));
    }

    @Test
    void missingNamesGetDefaultSeatNames() {
        GameSession session = manager.create("echo", "SOLO", null, null);

        assertEquals("player-jugador-1", seen.get(1).playerId());
        assertEquals("Jugador 1", session.view().players().get(0).displayName());
    }

    @Test
    void theMaximumOfOpenSessionsIsEnforcedAndFinishedOnesDoNotCount() {
        manager.create("echo", "SOLO", List.of("A"), Map.of());
        manager.create("echo", "SOLO", List.of("B"), Map.of());
        GameSession third = manager.create("echo", "SOLO", List.of("C"), Map.of());

        assertThrows(SessionManager.TooManySessionsException.class, () -> manager.create("echo", "SOLO", List.of("D"), Map.of()));

        third.abandon();
        manager.create("echo", "SOLO", List.of("D"), Map.of());
        assertEquals(3, manager.openCount());
    }

    @Test
    void theOpenSessionLimitHoldsWhenManyClientsCreateAtTheSameTime() throws InterruptedException {
        AtomicInteger created = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int index = 0; index < 40; index++) {
            threads.add(Thread.ofVirtual().start(() -> {
                try {
                    manager.create("echo", "SOLO", List.of("Ana"), Map.of());
                    created.incrementAndGet();
                } catch (SessionManager.TooManySessionsException full) {
                    refused.incrementAndGet();
                }
            }));
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(3, created.get());
        assertEquals(37, refused.get());
        assertEquals(3, manager.openCount());
    }

    @Test
    void optionsWithNullValuesAreIgnored() {
        Map<String, String> options = new HashMap<>();
        options.put("difficulty", null);

        GameSession session = manager.create("echo", "SOLO", List.of("Ana"), options);

        assertFalse(session.finished());
    }

    @Test
    void abandoningBySessionIdFinishesTheMatch() {
        GameSession session = manager.create("echo", "SOLO", List.of("Ana"), Map.of());

        assertTrue(manager.abandon(session.matchId()));
        assertFalse(manager.abandon("echo-unknown"));

        assertTrue(session.finished());
        assertEquals("ABANDONED", seen.get(seen.size() - 1).data().get("result"));
    }

    @Test
    void sweepingAbandonsIdleSessionsAndForgetsOldFinishedOnes() {
        GameSession idle = manager.create("echo", "SOLO", List.of("A"), Map.of());
        GameSession done = manager.create("echo", "SOLO", List.of("B"), Map.of());
        done.abandon();
        Instant later = Instant.now().plus(Duration.ofMinutes(40));

        int changes = manager.sweep(later, Duration.ofMinutes(20), Duration.ofMinutes(30));

        assertEquals(2, changes);
        assertTrue(idle.finished());
        assertTrue(manager.find(done.matchId()).isEmpty());
        assertTrue(manager.find(idle.matchId()).isPresent());
    }

    @Test
    void sweepingLeavesRecentSessionsAlone() {
        GameSession active = manager.create("echo", "SOLO", List.of("A"), Map.of());

        int changes = manager.sweep(Instant.now(), Duration.ofMinutes(20), Duration.ofMinutes(30));

        assertEquals(0, changes);
        assertFalse(active.finished());
    }

    @Test
    void abandoningEverythingAnnouncesTheEndOfEveryOpenMatch() {
        manager.create("echo", "SOLO", List.of("A"), Map.of());
        manager.create("echo", "SOLO", List.of("B"), Map.of());

        manager.abandonAll();

        List<GameOccurrence> finishes = new ArrayList<>(seen.stream().filter(o -> o.kind() == OccurrenceKind.MATCH_FINISHED).toList());
        assertEquals(2, finishes.size());
    }
}
