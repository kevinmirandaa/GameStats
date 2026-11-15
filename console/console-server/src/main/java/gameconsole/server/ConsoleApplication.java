package gameconsole.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import gameconsole.api.GameObserver;
import gameconsole.api.SessionServices;
import gameconsole.server.activity.RecentActivityObserver;
import gameconsole.server.config.ConsoleConfiguration;
import gameconsole.server.events.EventTranslator;
import gameconsole.server.events.GameStatsPublisher;
import gameconsole.server.logic.PrologHttpEngine;
import gameconsole.server.plugins.GameCatalog;
import gameconsole.server.plugins.PluginLoader;
import gameconsole.server.sessions.SessionManager;
import gameconsole.server.stats.GameStatsGateway;
import gameconsole.server.web.ConsoleRoutes;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.http.staticfiles.Location;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ConsoleApplication {

    private static final Duration PROLOG_TIMEOUT = Duration.ofSeconds(8);
    private static final int ACTIVITY_CAPACITY = 300;
    private static final long SWEEP_PERIOD_SECONDS = 30;

    private final ConsoleConfiguration configuration;
    private final PluginLoader pluginLoader;
    private final GameStatsPublisher publisher;
    private final SessionManager sessions;
    private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor();
    private final Javalin server;

    public ConsoleApplication(ConsoleConfiguration configuration) {
        this.configuration = configuration;
        this.pluginLoader = new PluginLoader(configuration.pluginsDirectory(), ConsoleApplication.class.getClassLoader());
        GameCatalog catalog = new GameCatalog(pluginLoader.load());
        PrologHttpEngine prolog = new PrologHttpEngine(configuration.prologUrl(), PROLOG_TIMEOUT, configuration.prologMaximumConcurrentQueries(), PROLOG_TIMEOUT);
        this.publisher = new GameStatsPublisher(
                configuration.gameStatsIngestUrl(),
                new GameStatsPublisher.Settings(
                        configuration.deliveryMaxAttempts(),
                        configuration.deliveryInitialBackoff(),
                        configuration.deliveryMaximumBackoff(),
                        Duration.ofSeconds(5)));
        RecentActivityObserver activity = new RecentActivityObserver(ACTIVITY_CAPACITY);
        List<GameObserver> observers = List.of(new EventTranslator(publisher), activity);
        this.sessions = new SessionManager(catalog, new SessionServices(prolog), observers, configuration.maximumOpenSessions());
        GameStatsGateway gateway = new GameStatsGateway(configuration.gameStatsAnalyticsUrl(), configuration.gameStatsIngestUrl());
        ConsoleRoutes routes = new ConsoleRoutes(catalog, sessions, publisher, gateway, prolog, activity);

        this.server = Javalin.create(config -> {
            config.useVirtualThreads = false;
            config.showJavalinBanner = false;
            config.jsonMapper(new JavalinJackson(new ObjectMapper(), true));
            config.staticFiles.add("/web", Location.CLASSPATH);
        });
        routes.register(server);
    }

    public int start() {
        server.start(configuration.port());
        sweeper.scheduleWithFixedDelay(
                () -> sessions.sweep(Instant.now(), configuration.idleSessionTimeout(), configuration.finishedSessionRetention()),
                SWEEP_PERIOD_SECONDS, SWEEP_PERIOD_SECONDS, TimeUnit.SECONDS);
        return server.port();
    }

    public GameStatsPublisher publisher() {
        return publisher;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public void stop() {
        sweeper.shutdownNow();
        server.stop();
        sessions.abandonAll();
        publisher.awaitDelivery(Duration.ofSeconds(15));
        publisher.close();
        pluginLoader.close();
    }

    public static void main(String[] arguments) {
        ConsoleApplication application = new ConsoleApplication(ConsoleConfiguration.fromEnvironment(System.getenv()));
        Runtime.getRuntime().addShutdownHook(new Thread(application::stop, "console-shutdown"));
        application.start();
    }
}
