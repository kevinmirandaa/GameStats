package gameconsole.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gameconsole.server.config.ConsoleConfiguration;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsoleConfigurationTest {

    @Test
    void theDefaultsPointToTheLocalServices() {
        ConsoleConfiguration configuration = ConsoleConfiguration.fromEnvironment(Map.of());

        assertEquals(8100, configuration.port());
        assertEquals(Path.of("plugins"), configuration.pluginsDirectory());
        assertEquals("http://localhost:8080", configuration.gameStatsIngestUrl());
        assertEquals("http://localhost:8081", configuration.gameStatsAnalyticsUrl());
        assertEquals("http://localhost:8090", configuration.prologUrl());
        assertEquals(Duration.ofMinutes(20), configuration.idleSessionTimeout());
        assertEquals(14, configuration.deliveryMaxAttempts());
        assertEquals(4, configuration.prologMaximumConcurrentQueries());
    }

    @Test
    void environmentValuesOverrideTheDefaultsAndTrailingSlashesAreRemoved() {
        ConsoleConfiguration configuration = ConsoleConfiguration.fromEnvironment(Map.of(
                "PORT", "9000",
                "PLUGINS_DIRECTORY", "/app/plugins",
                "GAMESTATS_INGEST_URL", "http://ingest-go:8080/",
                "PROLOG_URL", "http://prolog-ai:8090",
                "IDLE_SESSION_MINUTES", "5"));

        assertEquals(9000, configuration.port());
        assertEquals(Path.of("/app/plugins"), configuration.pluginsDirectory());
        assertEquals("http://ingest-go:8080", configuration.gameStatsIngestUrl());
        assertEquals("http://prolog-ai:8090", configuration.prologUrl());
        assertEquals(Duration.ofMinutes(5), configuration.idleSessionTimeout());
    }

    @Test
    void invalidNumbersFallBackToTheDefaults() {
        ConsoleConfiguration configuration = ConsoleConfiguration.fromEnvironment(Map.of("PORT", "abc", "MAXIMUM_OPEN_SESSIONS", " "));

        assertEquals(8100, configuration.port());
        assertEquals(200, configuration.maximumOpenSessions());
    }
}
