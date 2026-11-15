package gameconsole.server.stats;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class GameStatsGateway {

    public record Answer(int status, String body) {
    }

    private final String analyticsBaseUrl;
    private final String ingestBaseUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public GameStatsGateway(String analyticsBaseUrl, String ingestBaseUrl) {
        this.analyticsBaseUrl = analyticsBaseUrl;
        this.ingestBaseUrl = ingestBaseUrl;
    }

    public Answer analytics(String pathAndQuery) {
        return get(analyticsBaseUrl + pathAndQuery);
    }

    public Answer ingest(String pathAndQuery) {
        return get(ingestBaseUrl + pathAndQuery);
    }

    private Answer get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new Answer(response.statusCode(), response.body());
        } catch (IOException unreachable) {
            return new Answer(502, "{\"error\":\"GameStats no responde\"}");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new Answer(502, "{\"error\":\"consulta interrumpida\"}");
        }
    }
}
