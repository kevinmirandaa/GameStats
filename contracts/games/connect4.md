# Contrato del juego connect4

Eventos que emite GameConsole para Conecta 4. Complementa el sobre de `contracts/event.schema.json`.

## Identificacion

- `gameId`: `connect4`, `gameVersion`: `1.0`
- `matchId`: `connect4-` seguido de 12 caracteres hexadecimales, unico por partida.
- `playerId` humano: `player-` mas el nombre sin acentos en minusculas (por ejemplo `player-ana`). El jugador de la computadora es siempre `ai-prolog-connect4`.

## Eventos del ciclo de vida

| type | playerId | data |
|---|---|---|
| MATCH_STARTED | no se envia | mode (VS_COMPUTER o TWO_PLAYERS), difficulty, rows, columns, firstPlayerId |
| PLAYER_JOINED | jugador | displayName, kind (HUMAN o COMPUTER) |
| PLAYER_LEFT | jugador | reason (FINISHED o ABANDONED) |
| MATCH_FINISHED | no se envia | mode, totalMoves, result (WIN, DRAW o ABANDONED), winnerPlayerId y winnerKind cuando result es WIN |

## Acciones (type GAME_ACTION)

| action | playerId | data |
|---|---|---|
| MOVE | quien juega | column, row, disc (rojo o amarillo), moveNumber |
| AI_DECISION | la computadora | boardState (tablero en texto, filas separadas por /), chosenColumn, score, nodesSearched, searchDepth, decisionMillis, candidates (lista de column y score), engine (swi-prolog) |
| PLAYER_WON | ganador | reason (FOUR_IN_A_ROW), totalMoves |
| PLAYER_LOST | perdedor | reason (OPPONENT_FOUR_IN_A_ROW), totalMoves |
| PLAYER_DRAW | cada jugador | totalMoves |

Secuencia de una jugada contra la computadora: MOVE del humano, AI_DECISION de la computadora, MOVE de la computadora. Cada AI_DECISION precede al MOVE que explica.

## Que calcula GameStats con esto

Por partida: totalMoves, aiDecisions, averageAiNodes, averageAiMillis, humanWin, computerWin, drawn, abandoned.

Por jugador: moves, aiDecisions, averageAiNodes, averageAiMillis, won, lost, drawn.
