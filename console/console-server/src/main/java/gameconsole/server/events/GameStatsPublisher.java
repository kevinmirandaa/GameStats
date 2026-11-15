package gameconsole.server.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class GameStatsPublisher implements EventPublisher, AutoCloseable {

    public record Settings(int maxAttempts, Duration initialBackoff, Duration maximumBackoff, Duration requestTimeout) {
    }

    private static final class Outbox {
        private final Deque<Map<String, Object>> events = new ArrayDeque<>();
        private boolean draining;
    }

    private final URI eventsUri;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Settings settings;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, Outbox> outboxesByMatchId = new ConcurrentHashMap<>();
    private final DeliveryStatistics statistics = new DeliveryStatistics();
    private final List<String> recentProblems = new ArrayList<>();
    private volatile boolean closed;

    public GameStatsPublisher(String ingestBaseUrl, Settings settings) {
        this.eventsUri = URI.create(ingestBaseUrl + "/events");
        this.settings = settings;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    public DeliveryStatistics.Snapshot statistics() {
        return statistics.snapshot();
    }

    public synchronized List<String> recentProblems() {
        return List.copyOf(recentProblems);
    }

    @Override
    public void publish(Map<String, Object> event) {
        String matchId = String.valueOf(event.get("matchId"));
        Outbox outbox = outboxesByMatchId.computeIfAbsent(matchId, key -> new Outbox());
        boolean startDrain;
        synchronized (outbox) {
            outbox.events.addLast(event);
            statistics.eventQueued();
            startDrain = !outbox.draining;
            outbox.draining = true;
        }
        if (startDrain) {
            executor.submit(() -> drain(matchId, outbox));
        }
    }

    public boolean awaitDelivery(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (statistics.snapshot().pending() <= 0) {
                return true;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return statistics.snapshot().pending() <= 0;
    }

    @Override
    public void close() {
        closed = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void drain(String matchId, Outbox outbox) {
        while (true) {
            Map<String, Object> next;
            synchronized (outbox) {
                next = outbox.events.pollFirst();
                if (next == null) {
                    outbox.draining = false;
                    outboxesByMatchId.remove(matchId, outbox);
                    return;
                }
            }
            deliver(next);
        }
    }

    private void deliver(Map<String, Object> event) {
        String body;
        try {
            body = mapper.writeValueAsString(event);
        } catch (JsonProcessingException unserializable) {
            statistics.eventFailed();
            remember("evento no serializable " + event.get("eventId"));
            return;
        }
        HttpRequest request = HttpRequest.newBuilder(eventsUri)
                .timeout(settings.requestTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        Duration backoff = settings.initialBackoff();
        for (int attempt = 1; attempt <= settings.maxAttempts(); attempt++) {
            Outcome outcome = send(request);
            switch (outcome) {
                case DELIVERED -> {
                    statistics.eventDelivered();
                    return;
                }
                case REJECTED -> {
                    statistics.eventRejected();
                    remember("evento rechazado por GameStats " + event.get("eventId"));
                    return;
                }
                case RETRY -> {
                    if (attempt == settings.maxAttempts() || closed) {
                        statistics.eventFailed();
                        remember("evento no entregado tras " + attempt + " intentos " + event.get("eventId"));
                        return;
                    }
                    statistics.retryScheduled();
                    pause(backoff);
                    backoff = backoff.multipliedBy(2).compareTo(settings.maximumBackoff()) > 0 ? settings.maximumBackoff() : backoff.multipliedBy(2);
                }
            }
        }
    }

    private enum Outcome {
        DELIVERED,
        REJECTED,
        RETRY
    }

    private Outcome send(HttpRequest request) {
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status == 202 || status == 200) {
                return Outcome.DELIVERED;
            }
            if (status == 409 || (status >= 400 && status < 500)) {
                return Outcome.REJECTED;
            }
            return Outcome.RETRY;
        } catch (IOException unreachable) {
            return Outcome.RETRY;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Outcome.RETRY;
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void remember(String problem) {
        recentProblems.add(problem);
        if (recentProblems.size() > 50) {
            recentProblems.remove(0);
        }
    }
}
