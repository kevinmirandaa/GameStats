package gameconsole.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gameconsole.api.LogicUnavailableException;
import gameconsole.server.logic.PrologHttpEngine;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PrologHttpEngineTest {

    @Test
    void theAnswerOfPrologIsReturnedAsAMap() throws Exception {
        try (FakeHttpService prolog = new FakeHttpService()) {
            prolog.respondWith(request -> new FakeHttpService.Reply(200, "{\"column\":3,\"score\":-2}"));
            PrologHttpEngine engine = new PrologHttpEngine(prolog.url(), Duration.ofSeconds(2), 4, Duration.ofSeconds(2));

            Map<String, Object> answer = engine.ask("decide", Map.of("player", 1));

            assertEquals(3, answer.get("column"));
            assertEquals("/decide", prolog.received().get(0).path());
            assertTrue(prolog.received().get(0).body().contains("\"player\":1"));
        }
    }

    @Test
    void anErrorStatusOrAnUnreachableServiceIsReportedAsUnavailable() throws IOException {
        FakeHttpService prolog = new FakeHttpService();
        prolog.respondWith(request -> new FakeHttpService.Reply(400, "{\"error\":\"tablero invalido\"}"));
        PrologHttpEngine engine = new PrologHttpEngine(prolog.url(), Duration.ofSeconds(2), 4, Duration.ofSeconds(2));

        assertThrows(LogicUnavailableException.class, () -> engine.ask("decide", Map.of()));
        prolog.close();
        assertThrows(LogicUnavailableException.class, () -> engine.ask("decide", Map.of()));
    }

    @Test
    void neverMoreQueriesThanThePermitsAreSentToPrologAtTheSameTime() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger highest = new AtomicInteger();
        try (FakeHttpService prolog = new FakeHttpService()) {
            prolog.respondWith(request -> {
                int now = inFlight.incrementAndGet();
                highest.accumulateAndGet(now, Math::max);
                try {
                    Thread.sleep(80);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                inFlight.decrementAndGet();
                return new FakeHttpService.Reply(200, "{\"column\":1}");
            });
            PrologHttpEngine engine = new PrologHttpEngine(prolog.url(), Duration.ofSeconds(10), 2, Duration.ofSeconds(10));
            List<Thread> threads = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                threads.add(Thread.ofVirtual().start(() -> {
                    try {
                        engine.ask("decide", Map.of());
                    } catch (LogicUnavailableException failure) {
                        throw new IllegalStateException(failure);
                    }
                }));
            }
            for (Thread thread : threads) {
                thread.join();
            }

            assertEquals(12, prolog.received().size());
            assertTrue(highest.get() <= 2, "simultaneas: " + highest.get());
        }
    }

    @Test
    void whenPrologStaysBusyLongerThanTheTimeoutTheWaitingQueryIsGivenUp() throws Exception {
        try (FakeHttpService prolog = new FakeHttpService()) {
            prolog.respondWith(request -> {
                try {
                    Thread.sleep(600);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                return new FakeHttpService.Reply(200, "{\"column\":1}");
            });
            PrologHttpEngine engine = new PrologHttpEngine(prolog.url(), Duration.ofSeconds(5), 1, Duration.ofMillis(150));
            Thread slow = Thread.ofVirtual().start(() -> {
                try {
                    engine.ask("decide", Map.of());
                } catch (LogicUnavailableException ignored) {
                    return;
                }
            });
            Thread.sleep(50);

            LogicUnavailableException busy = assertThrows(LogicUnavailableException.class, () -> engine.ask("decide", Map.of()));

            assertTrue(busy.getMessage().contains("ocupado"));
            slow.join();
        }
    }
}
