package gameconsole.server.logic;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import gameconsole.api.LogicEngine;
import gameconsole.api.LogicUnavailableException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public final class PrologHttpEngine implements LogicEngine {

    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Duration requestTimeout;
    private final Semaphore permits;
    private final Duration waitTimeout;

    public PrologHttpEngine(String baseUrl, Duration requestTimeout, int maximumConcurrentQueries, Duration waitTimeout) {
        this.waitTimeout = waitTimeout;
        this.baseUrl = baseUrl;
        this.requestTimeout = requestTimeout;
        this.permits = new Semaphore(maximumConcurrentQueries, true);
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Override
    public Map<String, Object> ask(String queryName, Map<String, Object> arguments) throws LogicUnavailableException {
        try {
            if (!permits.tryAcquire(waitTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new LogicUnavailableException("Prolog esta ocupado con otras partidas");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LogicUnavailableException("Consulta a Prolog interrumpida", interrupted);
        }
        try {
            return query(queryName, arguments);
        } finally {
            permits.release();
        }
    }

    private Map<String, Object> query(String queryName, Map<String, Object> arguments) throws LogicUnavailableException {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/" + queryName))
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(arguments)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LogicUnavailableException("Prolog respondio " + response.statusCode() + ": " + response.body());
            }
            return mapper.readValue(response.body(), new TypeReference<Map<String, Object>>() { });
        } catch (IOException unreachable) {
            throw new LogicUnavailableException("No se pudo consultar Prolog: " + unreachable.getMessage(), unreachable);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LogicUnavailableException("Consulta a Prolog interrumpida", interrupted);
        }
    }

    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/health")).timeout(Duration.ofSeconds(2)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (IOException unreachable) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
