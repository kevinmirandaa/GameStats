package gameconsole.games.connect4;

import gameconsole.api.ActionResult;
import gameconsole.api.ActionValues;
import gameconsole.api.GameView;
import gameconsole.api.LogicUnavailableException;
import gameconsole.api.ObservableGameSession;
import gameconsole.api.Participant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class Connect4Adapter extends ObservableGameSession {

    private static final String UNAVAILABLE_NOTICE = "El motor Prolog no responde. Pulsa cualquier columna para reintentar.";

    private final ConnectFourMatch match;
    private final String mode;
    private final String difficulty;
    private String notice = "";

    Connect4Adapter(String matchId, String mode, ConnectFourMatch match, String difficulty) {
        super(Connect4Module.GAME_ID, Connect4Module.GAME_VERSION, matchId);
        this.match = match;
        this.mode = mode;
        this.difficulty = difficulty;
    }

    @Override
    protected void onStart() {
        List<Participant> participants = new ArrayList<>();
        for (ConnectFourPlayer player : match.players()) {
            participants.add(new Participant(player.playerId(), player.displayName(), player.isComputer() ? Participant.COMPUTER : Participant.HUMAN));
        }
        Map<String, Object> matchData = new LinkedHashMap<>();
        matchData.put("mode", mode);
        matchData.put("difficulty", difficulty);
        matchData.put("rows", Board.ROWS);
        matchData.put("columns", Board.COLUMNS);
        matchData.put("firstPlayerId", match.currentPlayer().playerId());
        beginMatch(matchData, participants);
        if (match.currentPlayer().isComputer()) {
            playComputerTurns();
        }
    }

    @Override
    protected ActionResult handle(String playerId, Map<String, Object> action) {
        notice = "";
        if (match.currentPlayer() instanceof ComputerPlayer) {
            return playComputerTurns();
        }
        ConnectFourPlayer player = match.playerById(playerId);
        if (player == null) {
            return ActionResult.rejected("Jugador desconocido: " + playerId);
        }
        if (player != match.currentPlayer()) {
            return ActionResult.rejected("No es tu turno");
        }
        Integer column = columnFrom(action);
        if (column == null) {
            return ActionResult.rejected("Indica la columna");
        }
        if (column < 0 || column >= Board.COLUMNS || match.board().isColumnFull(column)) {
            return ActionResult.rejected("Esa columna no esta disponible");
        }
        applyMove(player, column);
        if (match.status() == ConnectFourMatch.Status.IN_PROGRESS && match.currentPlayer().isComputer()) {
            return playComputerTurns();
        }
        return ActionResult.ok();
    }

    private ActionResult playComputerTurns() {
        while (match.status() == ConnectFourMatch.Status.IN_PROGRESS && match.currentPlayer() instanceof ComputerPlayer computer) {
            ColumnAdvice advice;
            try {
                advice = computer.chooseColumn(match.board());
            } catch (LogicUnavailableException failure) {
                notice = UNAVAILABLE_NOTICE;
                return ActionResult.ok();
            }
            if (advice.column() < 0 || advice.column() >= Board.COLUMNS || match.board().isColumnFull(advice.column())) {
                notice = UNAVAILABLE_NOTICE;
                return ActionResult.ok();
            }
            announceDecision(computer, advice);
            applyMove(computer, advice.column());
        }
        return ActionResult.ok();
    }

    private void announceDecision(ComputerPlayer computer, ColumnAdvice advice) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (ColumnAdvice.Candidate candidate : advice.candidates()) {
            candidates.add(Map.of("column", candidate.column(), "score", candidate.score()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("boardState", match.board().asText());
        data.put("chosenColumn", advice.column());
        data.put("score", advice.score());
        data.put("nodesSearched", advice.nodesSearched());
        data.put("searchDepth", advice.searchDepth());
        data.put("decisionMillis", advice.elapsedMillis());
        data.put("candidates", candidates);
        data.put("engine", "swi-prolog");
        announceAction(computer.playerId(), "AI_DECISION", data);
    }

    private void applyMove(ConnectFourPlayer player, int column) {
        Placement placement = match.play(player, column);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("column", placement.column());
        data.put("row", placement.row());
        data.put("disc", placement.disc().colorName());
        data.put("moveNumber", match.moveCount());
        announceAction(player.playerId(), "MOVE", data);
        if (match.status() != ConnectFourMatch.Status.IN_PROGRESS) {
            announceOutcome();
        }
    }

    private void announceOutcome() {
        Map<String, Object> finishData = new LinkedHashMap<>();
        finishData.put("mode", mode);
        finishData.put("totalMoves", match.moveCount());
        if (match.status() == ConnectFourMatch.Status.WON) {
            ConnectFourPlayer winner = match.winner();
            ConnectFourPlayer loser = match.opponentOf(winner);
            announceAction(winner.playerId(), "PLAYER_WON", Map.of("reason", "FOUR_IN_A_ROW", "totalMoves", match.moveCount()));
            announceAction(loser.playerId(), "PLAYER_LOST", Map.of("reason", "OPPONENT_FOUR_IN_A_ROW", "totalMoves", match.moveCount()));
            finishData.put("result", "WIN");
            finishData.put("winnerPlayerId", winner.playerId());
            finishData.put("winnerKind", winner.isComputer() ? Participant.COMPUTER : Participant.HUMAN);
        } else {
            for (ConnectFourPlayer player : match.players()) {
                announceAction(player.playerId(), "PLAYER_DRAW", Map.of("totalMoves", match.moveCount()));
            }
            finishData.put("result", "DRAW");
        }
        finishMatch(finishData);
    }

    private static Integer columnFrom(Map<String, Object> action) {
        OptionalInt column = ActionValues.integer(action.get("column"));
        if (column.isPresent()) {
            return column.getAsInt();
        }
        OptionalInt cell = ActionValues.integer(action.get("cell"));
        if (cell.isPresent() && cell.getAsInt() >= 0) {
            return cell.getAsInt() % Board.COLUMNS;
        }
        return null;
    }

    @Override
    protected GameView buildView() {
        List<GameView.CellView> cells = new ArrayList<>();
        boolean humanCanMove = match.status() == ConnectFourMatch.Status.IN_PROGRESS;
        for (int row = 0; row < Board.ROWS; row++) {
            for (int column = 0; column < Board.COLUMNS; column++) {
                Disc disc = match.board().discAt(row, column);
                boolean winning = isWinningCell(row, column);
                String style = disc == null ? "empty" : (disc == Disc.RED ? "red" : "yellow") + (winning ? " winning" : "");
                boolean enabled = humanCanMove && !match.board().isColumnFull(column);
                cells.add(new GameView.CellView("", style, enabled));
            }
        }
        List<GameView.PlayerView> players = new ArrayList<>();
        for (ConnectFourPlayer player : match.players()) {
            players.add(new GameView.PlayerView(player.playerId(), player.displayName(), player.isComputer() ? Participant.COMPUTER : Participant.HUMAN, "fichas " + player.disc().feminineColorName() + "s"));
        }
        return new GameView(
                matchId(), gameId(), "Conecta 4", Board.ROWS, Board.COLUMNS, cells, players,
                match.status() == ConnectFourMatch.Status.IN_PROGRESS ? match.currentPlayer().playerId() : "",
                statusText(), notice, finished(),
                match.winner() == null ? null : match.winner().playerId(), List.of());
    }

    private boolean isWinningCell(int row, int column) {
        for (int[] cell : match.winningLine()) {
            if (cell[0] == row && cell[1] == column) {
                return true;
            }
        }
        return false;
    }

    private String statusText() {
        if (match.status() == ConnectFourMatch.Status.WON) {
            return "Gana " + match.winner().displayName();
        }
        if (match.status() == ConnectFourMatch.Status.DRAWN) {
            return "Empate";
        }
        ConnectFourPlayer current = match.currentPlayer();
        return "Turno de " + current.displayName() + " (" + current.disc().colorName() + ")";
    }
}
