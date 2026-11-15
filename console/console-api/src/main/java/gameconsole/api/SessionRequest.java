package gameconsole.api;

import java.util.List;
import java.util.Map;

public record SessionRequest(String matchId, String mode, List<PlayerSeat> players, Map<String, String> options) {

    public SessionRequest {
        players = List.copyOf(players);
        options = Map.copyOf(options);
    }

    public String option(String optionId, String fallback) {
        return options.getOrDefault(optionId, fallback);
    }
}
