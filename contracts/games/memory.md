# Contrato del juego memory

Eventos que emite GameConsole para Memoria de parejas.

## Identificacion

- `gameId`: `memory`, `gameVersion`: `1.0`
- `matchId`: `memory-` seguido de 12 caracteres hexadecimales.
- `playerId`: `player-` mas el nombre sin acentos en minusculas.

## Eventos del ciclo de vida

| type | playerId | data |
|---|---|---|
| MATCH_STARTED | no se envia | mode (TWO_PLAYERS o SOLO), size, pairs, firstPlayerId |
| PLAYER_JOINED | jugador | displayName, kind (HUMAN) |
| PLAYER_LEFT | jugador | reason (FINISHED o ABANDONED) |
| MATCH_FINISHED | no se envia | mode, attempts, pairs, scores (mapa playerId a parejas), result (WIN, DRAW o ABANDONED), winnerPlayerId cuando result es WIN |

## Acciones (type GAME_ACTION)

| action | playerId | data |
|---|---|---|
| FLIP_CARD | quien voltea | cardIndex, symbol, flipInTurn (1 o 2), turnNumber |
| PAIR_FOUND | quien la encontro | firstCardIndex, secondCardIndex, symbol, turnNumber, pairsFound |
| PAIR_MISSED | quien fallo | firstCardIndex, secondCardIndex, symbol, turnNumber, pairsFound |
| PLAYER_WON | ganador | pairsFound, attempts |
| PLAYER_LOST | perdedor | pairsFound, attempts |
| PLAYER_DRAW | cada jugador empatado | pairsFound, attempts |

Cada intento de pareja produce dos FLIP_CARD y un PAIR_FOUND o PAIR_MISSED. Un jugador que acierta conserva el turno.

## Que calcula GameStats con esto

Por partida: totalFlips, pairsFound, attempts, accuracyPercent, drawn, abandoned.

Por jugador: flips, pairsFound, pairsMissed, accuracyPercent, won, lost, drawn.
