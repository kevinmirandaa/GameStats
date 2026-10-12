# Contrato del juego racing

Documento autocontenido para quien implemente un juego de carreras que emita eventos a GameStats. Complementa el sobre de `contracts/event.schema.json`.

## Identificacion

- `gameId`: `racing`
- `gameVersion`: la version de tu juego. El ejemplo usa "1.0".
- Cada partida tiene su propio `matchId`, unico globalmente. Los `playerId` tambien son unicos globalmente.

## Eventos del ciclo de vida

| type | playerId | data |
|---|---|---|
| MATCH_STARTED | no se envia | track (texto), totalLaps (entero) |
| PLAYER_JOINED | piloto | displayName (texto), vehicle (texto), startPosition (entero desde 1) |
| PLAYER_LEFT | piloto | libre, por ejemplo reason |
| MATCH_FINISHED | no se envia | winnerPlayerId (texto), ranking (lista de playerId, el primero es el ganador) |

Envia MATCH_STARTED primero y MATCH_FINISHED al terminar. Sin MATCH_FINISHED la partida se cierra por inactividad como INCOMPLETE y pierde las metricas que dependen del resultado.

## Acciones (type GAME_ACTION)

| action | playerId | data |
|---|---|---|
| LAP_COMPLETED | piloto | lap (entero desde 1), lapTimeMs (entero), position (entero, posicion al cruzar la meta), topSpeedKmh (numero) |
| POSITION_CHANGED | piloto cuya posicion cambia | lap (entero), fromPosition (entero), toPosition (entero) |
| PENALTY_APPLIED | piloto penalizado | lap (entero), seconds (numero), reason (texto) |
| PIT_STOP | piloto | lap (entero), durationMs (entero) |

Reglas de emision:

- Envia LAP_COMPLETED por cada piloto en cada vuelta, con el tiempo en milisegundos.
- Envia POSITION_CHANGED para cada piloto afectado en un adelantamiento: uno con toPosition menor que fromPosition (el que sube) y otro con toPosition mayor (el que baja).
- Los tiempos y posiciones deben ser coherentes: la posicion de LAP_COMPLETED debe reflejar el orden de llegada acumulado.
- `timestamp` en RFC 3339. Se aceptan eventos desordenados: GameStats los ordena.

## Que calcula GameStats con esto

Por piloto: lapsCompleted, bestLapMs, averageLapMs, lapTimeStdDevMs, topSpeedKmh, penaltySecondsTotal, pitStopCount, pitStopTotalMs, overtakesMade, effectiveRaceTimeMs, finalPosition, positionsGained.

Por partida: totalLapsCompleted, fastestLapMs, averageLapMs, penaltyCount, pitStopCount, positionChangesTotal, leadChanges, finishedPlayers.

Patrones: RACE_COMEBACK (el ganador llego a ir cuarto o peor) e IMPROVING_LAP_STREAK (tres o mas vueltas consecutivas cada una mas rapida). Las formulas exactas estan en `docs/STATS.md`.

## Ejemplos

- `contracts/examples/games/racing/patterns-triggered.batch.json`: 4 pilotos, 4 vueltas, dispara ambas reglas.
- `contracts/examples/games/racing/patterns-not-triggered.batch.json`: 4 pilotos, 3 vueltas, ninguna regla (casi disparos incluidos).
- Los resultados esperados estan en los archivos `.expected.json` contiguos.
