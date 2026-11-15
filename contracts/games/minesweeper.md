# Contrato del juego minesweeper

Eventos que emite GameConsole para Buscaminas.

## Identificacion

- `gameId`: `minesweeper`, `gameVersion`: `1.0`
- `matchId`: `minesweeper-` seguido de 12 caracteres hexadecimales.
- `playerId`: `player-` mas el nombre sin acentos en minusculas.

## Eventos del ciclo de vida

| type | playerId | data |
|---|---|---|
| MATCH_STARTED | no se envia | mode (SOLO), difficulty, rows, columns, mines |
| PLAYER_JOINED | jugador | displayName, kind (HUMAN) |
| PLAYER_LEFT | jugador | reason (FINISHED o ABANDONED) |
| MATCH_FINISHED | no se envia | mode, result (WIN, LOSS o ABANDONED), secondsElapsed, cellsRevealed, winnerPlayerId cuando result es WIN |

## Acciones (type GAME_ACTION)

| action | playerId | data |
|---|---|---|
| REVEAL_CELL | jugador | row, column, hitMine, adjacentMines, cellsRevealed (incluye la cascada), cascade, safeCellsRemaining |
| FLAG_CELL | jugador | row, column, flagged, flagsInPlay |
| PLAYER_WON | jugador | reason (ALL_SAFE_CELLS_REVEALED), secondsElapsed, cellsRevealed, flagsInPlay |
| PLAYER_LOST | jugador | reason (MINE_HIT), row, column, secondsElapsed, cellsRevealed, flagsInPlay |

La primera casilla descubierta nunca es una mina ni tiene minas alrededor.

## Que calcula GameStats con esto

Por partida: reveals, flagsPlaced, cellsRevealed, cascades, secondsElapsed, abandoned.

Por jugador: reveals, flagsPlaced, cellsRevealed, won, lost.
