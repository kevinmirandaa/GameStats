package gameconsole.games.memory;

import gameconsole.api.ActionResult;
import gameconsole.api.ActionValues;
import gameconsole.api.GameView;
import gameconsole.api.ObservableGameSession;
import gameconsole.api.Participant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

public final class MemoryAdapter extends ObservableGameSession {

    private final MemoryMatch match;
    private final String mode;
    private final String size;
    private final int columns;

    MemoryAdapter(String matchId, String mode, String size, int columns, MemoryMatch match) {
        super(MemoryModule.GAME_ID, MemoryModule.GAME_VERSION, matchId);
        this.match = match;
        this.mode = mode;
        this.size = size;
        this.columns = columns;
    }

    @Override
    protected void onStart() {
        List<Participant> participants = new ArrayList<>();
        for (MemoryPlayer player : match.players()) {
            participants.add(new Participant(player.playerId(), player.displayName(), Participant.HUMAN));
        }
        Map<String, Object> matchData = new LinkedHashMap<>();
        matchData.put("mode", mode);
        matchData.put("size", size);
        matchData.put("pairs", match.cards().size() / 2);
        matchData.put("firstPlayerId", match.currentPlayer().playerId());
        beginMatch(matchData, participants);
    }

    @Override
    protected ActionResult handle(String playerId, Map<String, Object> action) {
        MemoryPlayer player = match.playerById(playerId);
        if (player == null) {
            return ActionResult.rejected("Jugador desconocido: " + playerId);
        }
        match.acknowledgeMiss();
        if (player != match.currentPlayer()) {
            return ActionResult.rejected("No es tu turno");
        }
        if (Boolean.TRUE.equals(action.get("continue"))) {
            return ActionResult.ok();
        }
        OptionalInt cell = ActionValues.integer(action.get("cell"));
        if (cell.isEmpty()) {
            return ActionResult.rejected("Indica la carta");
        }
        int cardIndex = cell.getAsInt();
        if (!match.canFlip(cardIndex)) {
            return ActionResult.rejected("Esa carta no se puede voltear");
        }
        FlipOutcome outcome = match.flip(player, cardIndex);
        announceFlip(player, outcome);
        if (match.isOver()) {
            announceOutcome();
        }
        return ActionResult.ok();
    }

    private void announceFlip(MemoryPlayer player, FlipOutcome outcome) {
        Map<String, Object> flip = new LinkedHashMap<>();
        flip.put("cardIndex", outcome.cardIndex());
        flip.put("symbol", outcome.symbol());
        flip.put("flipInTurn", outcome.flipInTurn());
        flip.put("turnNumber", outcome.turnNumber());
        announceAction(player.playerId(), "FLIP_CARD", flip);
        if (outcome.kind() == FlipOutcome.Kind.FIRST_CARD) {
            return;
        }
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("firstCardIndex", outcome.otherCardIndex());
        pair.put("secondCardIndex", outcome.cardIndex());
        pair.put("turnNumber", outcome.turnNumber());
        pair.put("symbol", outcome.symbol());
        pair.put("pairsFound", player.pairsFound());
        announceAction(player.playerId(), outcome.kind() == FlipOutcome.Kind.PAIR_FOUND ? "PAIR_FOUND" : "PAIR_MISSED", pair);
    }

    private void announceOutcome() {
        List<MemoryPlayer> leaders = match.leaders();
        boolean singleWinner = leaders.size() == 1 || match.players().size() == 1;
        Map<String, Object> finishData = new LinkedHashMap<>();
        finishData.put("mode", mode);
        finishData.put("attempts", match.totalAttempts());
        finishData.put("pairs", match.cards().size() / 2);
        Map<String, Object> scores = new LinkedHashMap<>();
        for (MemoryPlayer player : match.players()) {
            scores.put(player.playerId(), player.pairsFound());
        }
        finishData.put("scores", scores);
        if (singleWinner) {
            MemoryPlayer winner = leaders.get(0);
            for (MemoryPlayer player : match.players()) {
                if (player == winner) {
                    announceAction(player.playerId(), "PLAYER_WON", Map.of("pairsFound", player.pairsFound(), "attempts", match.totalAttempts()));
                } else {
                    announceAction(player.playerId(), "PLAYER_LOST", Map.of("pairsFound", player.pairsFound(), "attempts", match.totalAttempts()));
                }
            }
            finishData.put("result", "WIN");
            finishData.put("winnerPlayerId", winner.playerId());
        } else {
            for (MemoryPlayer player : match.players()) {
                announceAction(player.playerId(), "PLAYER_DRAW", Map.of("pairsFound", player.pairsFound(), "attempts", match.totalAttempts()));
            }
            finishData.put("result", "DRAW");
        }
        finishMatch(finishData);
    }

    @Override
    protected GameView buildView() {
        List<GameView.CellView> cells = new ArrayList<>();
        for (int index = 0; index < match.cards().size(); index++) {
            Card card = match.cards().get(index);
            int symbolStyle = Deck.symbolPosition(card.symbol());
            if (card.isMatched()) {
                cells.add(new GameView.CellView(card.symbol(), "matched sym-" + symbolStyle, false));
            } else if (card.isFaceUp()) {
                cells.add(new GameView.CellView(card.symbol(), "revealed sym-" + symbolStyle, false));
            } else {
                cells.add(new GameView.CellView("", "hidden", !finished()));
            }
        }
        List<GameView.PlayerView> players = new ArrayList<>();
        for (MemoryPlayer player : match.players()) {
            players.add(new GameView.PlayerView(player.playerId(), player.displayName(), Participant.HUMAN, player.pairsFound() + " parejas"));
        }
        String winnerId = null;
        if (match.isOver() && match.leaders().size() == 1) {
            winnerId = match.leaders().get(0).playerId();
        }
        List<String> extraActions = match.hasPendingMiss() ? List.of("continue") : List.of();
        return new GameView(
                matchId(), gameId(), "Memoria", cells.size() / columns, columns, cells, players,
                finished() ? "" : match.currentPlayer().playerId(),
                statusText(), match.hasPendingMiss() ? "Sin pareja. Sigue jugando para continuar." : "", finished(), winnerId, extraActions);
    }

    private String statusText() {
        if (match.isOver()) {
            List<MemoryPlayer> leaders = match.leaders();
            return leaders.size() == 1 ? "Gana " + leaders.get(0).displayName() : "Empate";
        }
        return "Turno de " + match.currentPlayer().displayName();
    }
}
