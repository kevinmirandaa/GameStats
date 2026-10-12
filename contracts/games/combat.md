# Contrato del juego combat

Documento autocontenido para quien implemente un juego de combate que emita eventos a GameStats. Complementa el sobre de `contracts/event.schema.json`.

## Identificacion

- `gameId`: `combat`
- `gameVersion`: la version de tu juego. El ejemplo usa "1.0".
- Cada partida tiene su propio `matchId`, unico globalmente. Los `playerId` tambien son unicos globalmente.

## Eventos del ciclo de vida

| type | playerId | data |
|---|---|---|
| MATCH_STARTED | no se envia | map (texto), mode (texto) |
| PLAYER_JOINED | jugador | displayName (texto) |
| PLAYER_LEFT | jugador | libre, por ejemplo reason |
| MATCH_FINISHED | no se envia | winnerPlayerId (texto), ranking (lista de playerId, el primero es el ganador) |

Envia MATCH_STARTED primero y MATCH_FINISHED al terminar. Sin MATCH_FINISHED la partida se cierra por inactividad como INCOMPLETE y no se detecta SCORE_COMEBACK.

## Acciones (type GAME_ACTION)

| action | playerId | data |
|---|---|---|
| ELIMINATION | quien elimina | victimPlayerId (texto), weapon (texto), distanceMeters (numero) |
| DAMAGE_DEALT | quien daña | targetPlayerId (texto), amount (numero), weapon (texto) |
| SHOT_FIRED | quien dispara | weapon (texto), hit (booleano) |

Reglas de emision:

- La muerte de un jugador no se envia como evento aparte. Se informa con `victimPlayerId` en la ELIMINATION del jugador que lo elimina.
- Envia SHOT_FIRED por cada disparo, acertado o no, con `hit` correcto. La precision se calcula con ellos.
- Envia DAMAGE_DEALT por cada impacto que cause daño, incluidos los que no eliminan.
- `timestamp` en RFC 3339. Se aceptan eventos desordenados: GameStats los ordena.

## Que calcula GameStats con esto

Por jugador: eliminations, deaths, killDeathRatio, damageDealtTotal, damageDealtAverage, shotsFired, shotsHit, accuracyPercent, damagePerElimination, longestEliminationDistanceMeters.

Por partida: totalEliminations, totalDamageDealt, eliminationsPerMinute, mostEliminationsByOnePlayer, averageEliminationDistanceMeters.

Patrones: KILL_STREAK (tres o mas eliminaciones seguidas sin morir) y SCORE_COMEBACK (el ganador llego a ir perdiendo por tres o mas eliminaciones). Las formulas exactas estan en `docs/STATS.md`.

## Ejemplos

- `contracts/examples/games/combat/patterns-triggered.batch.json`: 3 jugadores, 10 eliminaciones, dispara ambas reglas.
- `contracts/examples/games/combat/patterns-not-triggered.batch.json`: 3 jugadores, 7 eliminaciones, ninguna regla (casi disparos incluidos).
- Los resultados esperados estan en los archivos `.expected.json` contiguos.
