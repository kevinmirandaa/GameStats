package gameconsole.api;

import java.util.List;

public record GameView(
        String matchId,
        String gameId,
        String title,
        int rows,
        int columns,
        List<CellView> cells,
        List<PlayerView> players,
        String currentPlayerId,
        String status,
        String message,
        boolean finished,
        String winnerPlayerId,
        List<String> extraActions) {

    public record CellView(String text, String style, boolean enabled) {
    }

    public record PlayerView(String playerId, String displayName, String kind, String score) {
    }
}
