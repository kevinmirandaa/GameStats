package gameconsole.server.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

public record ConsoleConfiguration(
        int port,
        Path pluginsDirectory,
        String gameStatsIngestUrl,
        String gameStatsAnalyticsUrl,
        String prologUrl,
        Duration idleSessionTimeout,
        Duration finishedSessionRetention,
        int maximumOpenSessions,
        int deliveryMaxAttempts,
        Duration deliveryInitialBackoff,
        Duration deliveryMaximumBackoff,
        int prologMaximumConcurrentQueries) {

    public static ConsoleConfiguration fromEnvironment(Map<String, String> environment) {
        Function<String, String> read = environment::get;
        return new ConsoleConfiguration(
                integer(read, "PORT", 8100),
                Path.of(text(read, "PLUGINS_DIRECTORY", "plugins")),
                trimmed(text(read, "GAMESTATS_INGEST_URL", "http://localhost:8080")),
                trimmed(text(read, "GAMESTATS_ANALYTICS_URL", "http://localhost:8081")),
                trimmed(text(read, "PROLOG_URL", "http://localhost:8090")),
                Duration.ofMinutes(integer(read, "IDLE_SESSION_MINUTES", 20)),
                Duration.ofMinutes(integer(read, "FINISHED_SESSION_RETENTION_MINUTES", 30)),
                integer(read, "MAXIMUM_OPEN_SESSIONS", 200),
                integer(read, "DELIVERY_MAX_ATTEMPTS", 14),
                Duration.ofMillis(integer(read, "DELIVERY_INITIAL_BACKOFF_MILLISECONDS", 500)),
                Duration.ofMillis(integer(read, "DELIVERY_MAXIMUM_BACKOFF_MILLISECONDS", 30000)),
                integer(read, "PROLOG_MAX_CONCURRENCY", 4));
    }

    private static String text(Function<String, String> read, String name, String fallback) {
        String value = read.apply(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int integer(Function<String, String> read, String name, int fallback) {
        String value = read.apply(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    private static String trimmed(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
