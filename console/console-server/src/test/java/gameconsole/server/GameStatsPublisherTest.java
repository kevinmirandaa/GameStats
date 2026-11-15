package gameconsole.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gameconsole.server.events.DeliveryStatistics;
import gameconsole.server.events.GameStatsPublisher;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameStatsPublisherTest {

    private FakeHttpService ingest;
    private GameStatsPublisher publisher;

    @BeforeEach
    void startFakeIngest() throws IOException {
        ingest = new FakeHttpService();
        ingest.respondWith(request -> new FakeHttpService.Reply(202, "{}"));
        publisher = newPublisher(ingest.url(), 4);
    }

    @AfterEach
    void stopEverything() {
        publisher.close();
        ingest.close();
    }

    private static GameStatsPublisher newPublisher(String url, int attempts) {
        return new GameStatsPublisher(url, new GameStatsPublisher.Settings(attempts, Duration.ofMillis(10), Duration.ofMillis(40), Duration.ofSeconds(2)));
    }

    private static Map<String, Object> event(String matchId, int sequence) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", matchId + "-" + sequence);
        event.put("matchId", matchId);
        event.put("sequence", sequence);
        return event;
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "la condicion no se cumplio a tiempo");
    }

    @Test
    void everyPublishedEventReachesTheIngestEndpoint() {
        for (int sequence = 0; sequence < 20; sequence++) {
            publisher.publish(event("match-a", sequence));
        }

        assertTrue(publisher.awaitDelivery(Duration.ofSeconds(10)));
        assertEquals(20, ingest.received().size());
        DeliveryStatistics.Snapshot snapshot = publisher.statistics();
        assertEquals(20, snapshot.delivered());
        assertEquals(0, snapshot.pending());
        assertEquals("POST", ingest.received().get(0).method());
        assertEquals("/events", ingest.received().get(0).path());
    }

    @Test
    void eventsOfOneMatchAreDeliveredInTheOrderTheyWerePublished() {
        for (int sequence = 0; sequence < 60; sequence++) {
            publisher.publish(event("match-ordered", sequence));
        }

        assertTrue(publisher.awaitDelivery(Duration.ofSeconds(10)));
        List<Integer> order = new ArrayList<>();
        for (FakeHttpService.Received request : ingest.received()) {
            String body = request.body();
            int start = body.indexOf("\"sequence\":") + "\"sequence\":".length();
            int end = body.indexOf('}', start);
            order.add(Integer.parseInt(body.substring(start, end).trim()));
        }
        List<Integer> expected = new ArrayList<>();
        for (int sequence = 0; sequence < 60; sequence++) {
            expected.add(sequence);
        }
        assertEquals(expected, order);
    }

    @Test
    void aSlowMatchDoesNotBlockTheOthers() throws InterruptedException {
        ingest.respondWith(request -> {
            if (request.body().contains("match-slow")) {
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return new FakeHttpService.Reply(202, "{}");
        });
        publisher.publish(event("match-slow", 0));
        for (int match = 0; match < 10; match++) {
            publisher.publish(event("match-fast-" + match, 0));
        }

        waitUntil(() -> publisher.statistics().delivered() >= 10);

        assertEquals(1, publisher.statistics().pending());
    }

    @Test
    void manyConcurrentPublishersLoseNoEvents() throws InterruptedException {
        int publishers = 16;
        int eventsEach = 25;
        List<Thread> threads = new ArrayList<>();
        for (int publisherIndex = 0; publisherIndex < publishers; publisherIndex++) {
            String matchId = "match-parallel-" + publisherIndex;
            Thread thread = Thread.ofVirtual().start(() -> {
                for (int sequence = 0; sequence < eventsEach; sequence++) {
                    publisher.publish(event(matchId, sequence));
                }
            });
            threads.add(thread);
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertTrue(publisher.awaitDelivery(Duration.ofSeconds(20)));
        assertEquals(publishers * eventsEach, ingest.received().size());
        assertEquals(publishers * eventsEach, publisher.statistics().delivered());
    }

    @Test
    void temporaryUnavailabilityIsRetriedUntilTheEventIsDelivered() {
        AtomicInteger calls = new AtomicInteger();
        ingest.respondWith(request -> calls.incrementAndGet() <= 3 ? new FakeHttpService.Reply(503, "{}") : new FakeHttpService.Reply(202, "{}"));

        publisher.publish(event("match-retry", 0));

        assertTrue(publisher.awaitDelivery(Duration.ofSeconds(10)));
        DeliveryStatistics.Snapshot snapshot = publisher.statistics();
        assertEquals(1, snapshot.delivered());
        assertEquals(3, snapshot.retries());
        assertEquals(0, snapshot.failed());
    }

    @Test
    void conflictsAndInvalidEventsAreNotRetried() {
        ingest.respondWith(request -> new FakeHttpService.Reply(409, "{}"));

        publisher.publish(event("match-conflict", 0));

        assertTrue(publisher.awaitDelivery(Duration.ofSeconds(10)));
        assertEquals(1, ingest.received().size());
        assertEquals(1, publisher.statistics().rejected());
        assertEquals(0, publisher.statistics().retries());
    }

    @Test
    void anEventIsGivenUpAfterTheMaximumAttemptsAndReported() {
        ingest.respondWith(request -> new FakeHttpService.Reply(503, "{}"));

        publisher.publish(event("match-down", 0));

        assertTrue(publisher.awaitDelivery(Duration.ofSeconds(10)));
        DeliveryStatistics.Snapshot snapshot = publisher.statistics();
        assertEquals(4, ingest.received().size());
        assertEquals(1, snapshot.failed());
        assertEquals(3, snapshot.retries());
        assertFalse(publisher.recentProblems().isEmpty());
    }

    @Test
    void anUnreachableGameStatsDoesNotCrashThePublisherAndLaterEventsStillFlow() throws IOException {
        FakeHttpService unreachable = new FakeHttpService();
        String deadUrl = unreachable.url();
        unreachable.close();
        GameStatsPublisher isolated = newPublisher(deadUrl, 2);
        try {
            isolated.publish(event("match-unreachable", 0));
            isolated.publish(event("match-unreachable", 1));

            assertTrue(isolated.awaitDelivery(Duration.ofSeconds(10)));
            assertEquals(2, isolated.statistics().failed());
        } finally {
            isolated.close();
        }
    }
}
