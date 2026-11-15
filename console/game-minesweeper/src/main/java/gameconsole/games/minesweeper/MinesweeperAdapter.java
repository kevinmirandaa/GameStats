package gameconsole.games.minesweeper;

import gameconsole.api.ActionResult;
import gameconsole.api.ActionValues;
import gameconsole.api.GameView;
import gameconsole.api.ObservableGameSession;
import gameconsole.api.Participant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class MinesweeperAdapter extends ObservableGameSession {

    private enum Status {
        IN_PROGRESS,
        WON,
        LOST
    }

    private final MineField field;
    private final Clock clock;
    private final String playerId;
    private final String displayName;
    private final String difficulty;
    private Instant startedAt;
    private Status status = Status.IN_PROGRESS;
    private int explodedRow = -1;
    private int explodedColumn = -1;

    MinesweeperAdapter(String matchId, String playerId, String displayName, String difficulty, MineField field, Clock clock) {
        super(MinesweeperModule.GAME_ID, MinesweeperModule.GAME_VERSION, matchId);
        this.field = field;
        this.clock = clock;
        this.playerId = playerId;
        this.displayName = displayName;
        this.difficulty = difficulty;
    }

    @Override
    protected void onStart() {
        startedAt = clock.instant();
        Map<String, Object> matchData = new LinkedHashMap<>();
        matchData.put("mode", MinesweeperModule.SOLO);
        matchData.put("difficulty", difficulty);
        matchData.put("rows", field.rows());
        matchData.put("columns", field.columns());
        matchData.put("mines", field.mineCount());
        beginMatch(matchData, List.of(new Participant(playerId, displayName, Participant.HUMAN)));
    }

    @Override
    protected ActionResult handle(String actingPlayerId, Map<String, Object> action) {
        if (!playerId.equals(actingPlayerId)) {
            return ActionResult.rejected("Jugador desconocido: " + actingPlayerId);
        }
        OptionalInt cell = ActionValues.integer(action.get("cell"));
        if (cell.isEmpty()) {
            return ActionResult.rejected("Indica la casilla");
        }
        int row = cell.getAsInt() / field.columns();
        int column = cell.getAsInt() % field.columns();
        if (cell.getAsInt() < 0 || !field.isInside(row, column)) {
            return ActionResult.rejected("Casilla fuera del tablero");
        }
        if (Boolean.TRUE.equals(action.get("flag"))) {
            return flag(row, column);
        }
        return reveal(row, column);
    }

    private ActionResult flag(int row, int column) {
        if (field.isRevealed(row, column)) {
            return ActionResult.rejected("No se puede marcar una casilla descubierta");
        }
        boolean flagged = field.toggleFlag(row, column);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("row", row);
        data.put("column", column);
        data.put("flagged", flagged);
        data.put("flagsInPlay", field.flagCount());
        announceAction(playerId, "FLAG_CELL", data);
        return ActionResult.ok();
    }

    private ActionResult reveal(int row, int column) {
        if (field.isRevealed(row, column)) {
            return ActionResult.rejected("La casilla ya esta descubierta");
        }
        if (field.isFlagged(row, column)) {
            return ActionResult.rejected("Quita la bandera antes de descubrir");
        }
        RevealResult result = field.reveal(row, column);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("row", row);
        data.put("column", column);
        data.put("hitMine", result.hitMine());
        data.put("adjacentMines", result.adjacentMines());
        data.put("cellsRevealed", result.cellsRevealed());
        data.put("cascade", result.cascade());
        data.put("safeCellsRemaining", field.rows() * field.columns() - field.mineCount() - field.revealedSafeCells());
        announceAction(playerId, "REVEAL_CELL", data);
        if (result.hitMine()) {
            status = Status.LOST;
            explodedRow = row;
            explodedColumn = column;
            announceEnd("PLAYER_LOST", "LOSS", Map.of("reason", "MINE_HIT", "row", row, "column", column));
        } else if (field.isCleared()) {
            status = Status.WON;
            announceEnd("PLAYER_WON", "WIN", Map.of("reason", "ALL_SAFE_CELLS_REVEALED"));
        }
        return ActionResult.ok();
    }

    private void announceEnd(String action, String result, Map<String, Object> reasonData) {
        long seconds = Duration.between(startedAt, clock.instant()).toSeconds();
        Map<String, Object> playerData = new LinkedHashMap<>(reasonData);
        playerData.put("secondsElapsed", seconds);
        playerData.put("cellsRevealed", field.revealedSafeCells());
        playerData.put("flagsInPlay", field.flagCount());
        announceAction(playerId, action, playerData);
        Map<String, Object> finishData = new LinkedHashMap<>();
        finishData.put("mode", MinesweeperModule.SOLO);
        finishData.put("result", result);
        finishData.put("secondsElapsed", seconds);
        finishData.put("cellsRevealed", field.revealedSafeCells());
        if (status == Status.WON) {
            finishData.put("winnerPlayerId", playerId);
        }
        finishMatch(finishData);
    }

    @Override
    protected GameView buildView() {
        List<GameView.CellView> cells = new ArrayList<>();
        for (int row = 0; row < field.rows(); row++) {
            for (int column = 0; column < field.columns(); column++) {
                cells.add(cellView(row, column));
            }
        }
        int minesLeft = field.mineCount() - field.flagCount();
        String statusText = switch (status) {
            case WON -> "Despejaste el campo";
            case LOST -> "Pisaste una mina";
            case IN_PROGRESS -> "Minas por marcar: " + minesLeft;
        };
        return new GameView(
                matchId(), gameId(), "Buscaminas", field.rows(), field.columns(), cells,
                List.of(new GameView.PlayerView(playerId, displayName, Participant.HUMAN, field.revealedSafeCells() + " casillas")),
                finished() ? "" : playerId, statusText, "", finished(),
                status == Status.WON ? playerId : null, List.of("flag"));
    }

    private GameView.CellView cellView(int row, int column) {
        boolean ended = status != Status.IN_PROGRESS;
        if (field.isRevealed(row, column)) {
            if (field.isMine(row, column)) {
                boolean exploded = row == explodedRow && column == explodedColumn;
                return new GameView.CellView("", exploded ? "mine exploded" : "mine", false);
            }
            int adjacent = field.adjacentMines(row, column);
            return new GameView.CellView(adjacent == 0 ? "" : String.valueOf(adjacent), "revealed n" + adjacent, false);
        }
        if (ended && field.isMine(row, column) && !field.isFlagged(row, column)) {
            return new GameView.CellView("", "mine", false);
        }
        if (field.isFlagged(row, column)) {
            return new GameView.CellView("", "flag", !ended);
        }
        return new GameView.CellView("", "hidden", !ended);
    }
}
