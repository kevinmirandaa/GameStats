# Resultados esperados

Referencia de las pruebas de Scala. Los valores completos y exactos estan en los archivos `contracts/examples/games/{juego}/*.expected.json`. Aqui se resumen para poder leerlos.

Verificacion: cada valor se calculo con una implementacion de referencia y se recalculo con una segunda implementacion independiente (otro lenguaje y otra estructura de calculo). Las posiciones de racing se comprobaron ademas contra los tiempos acumulados de cada vuelta. Se revisaron a mano la desviacion estandar de player-04 (1755.705) y las cuentas de disparos y daño de player-23. Todos los eventos pasan `contracts/event.schema.json`.

Tolerancia de comparacion: 0.001. Los valores estan redondeados a 3 decimales.

## racing / patterns-triggered (match-racing-001)

Pista monza, 4 pilotos, 4 vueltas, 30 eventos, duracion 383.3 segundos, ranking player-04, player-01, player-02, player-03.

Conteos por type: GAME_ACTION 24, MATCH_FINISHED 1, MATCH_STARTED 1, PLAYER_JOINED 4.

Conteos por action: LAP_COMPLETED 16, PENALTY_APPLIED 1, PIT_STOP 1, POSITION_CHANGED 6.

Metricas de partida:

| Metrica | Valor |
|---|---|
| totalLapsCompleted | 16 |
| fastestLapMs | 89500 |
| averageLapMs | 92006.25 |
| penaltyCount | 1 |
| pitStopCount | 1 |
| positionChangesTotal | 6 |
| leadChanges | 1 |
| finishedPlayers | 4 |

Metricas por jugador:

| Metrica | player-01 | player-02 | player-03 | player-04 |
|---|---|---|---|---|
| eventCount | 6 | 7 | 7 | 8 |
| lapsCompleted | 4 | 4 | 4 | 4 |
| bestLapMs | 90000 | 91800 | 92200 | 89500 |
| averageLapMs | 91875 | 92275 | 92825 | 91050 |
| lapTimeStdDevMs | 1515.544 | 420.565 | 402.337 | 1755.705 |
| topSpeedKmh | 285.1 | 281 | 278.5 | 291.3 |
| penaltySecondsTotal | 0 | 5 | 0 | 0 |
| pitStopCount | 0 | 0 | 1 | 0 |
| pitStopTotalMs | 0 | 0 | 2400 | 0 |
| overtakesMade | 0 | 0 | 0 | 3 |
| effectiveRaceTimeMs | 367500 | 374100 | 371300 | 364200 |
| finalPosition | 2 | 3 | 4 | 1 |
| positionsGained | -1 | -1 | -1 | 3 |

Patrones, en orden:

1. IMPROVING_LAP_STREAK de player-04: streakLength 4, firstLapMs 94000, lastLapMs 89500. Desde el evento evt-match-racing-001-0009 hasta evt-match-racing-001-0026.
2. RACE_COMEBACK de player-04: worstPosition 4, worstLap 1, finalPosition 1. Desde evt-match-racing-001-0009 hasta evt-match-racing-001-0030.
3. IMPROVING_LAP_STREAK de player-03: streakLength 3, firstLapMs 93300, lastLapMs 92200. Desde evt-match-racing-001-0017 hasta evt-match-racing-001-0029.

El primer y el segundo patron empiezan en el mismo evento, por eso se ordenan por nombre.

No hay patron para player-01 (tiempos siempre en aumento) ni player-02 (la mejor racha es de dos vueltas).

## racing / patterns-not-triggered (match-racing-002)

Pista suzuka, 4 pilotos, 3 vueltas, 21 eventos, duracion 284.5 segundos, ranking player-11, player-13, player-12, player-14.

Metricas de partida: totalLapsCompleted 12, fastestLapMs 88000, averageLapMs 89516.667, penaltyCount 0, pitStopCount 1, positionChangesTotal 2, leadChanges 0, finishedPlayers 4.

Metricas por jugador:

| Metrica | player-11 | player-12 | player-13 | player-14 |
|---|---|---|---|---|
| eventCount | 4 | 5 | 5 | 5 |
| bestLapMs | 88000 | 89300 | 89200 | 90500 |
| averageLapMs | 88233.333 | 89566.667 | 89433.333 | 90833.333 |
| lapTimeStdDevMs | 205.48 | 249.444 | 262.467 | 235.702 |
| topSpeedKmh | 290.8 | 288 | 287.9 | 284.1 |
| pitStopCount | 0 | 0 | 0 | 1 |
| pitStopTotalMs | 0 | 0 | 0 | 2600 |
| overtakesMade | 0 | 0 | 1 | 0 |
| effectiveRaceTimeMs | 264700 | 268700 | 268300 | 272500 |
| finalPosition | 1 | 3 | 2 | 4 |
| positionsGained | 0 | -1 | 1 | 0 |

Todos con lapsCompleted 3 y penaltySecondsTotal 0.

Patrones: ninguno. Casi disparos: player-14 fue cuarto en todas las vueltas pero no gano; cada piloto tiene como maximo una racha de dos vueltas; player-14 repite 91000 dos veces, lo que no cuenta como mejora.

## combat / patterns-triggered (match-combat-001)

Mapa dust-yard, 3 jugadores, 10 eliminaciones, 49 eventos, duracion 99.4 segundos, ranking player-21, player-22, player-23.

Conteos por type: GAME_ACTION 44, MATCH_FINISHED 1, MATCH_STARTED 1, PLAYER_JOINED 3.

Conteos por action: DAMAGE_DEALT 15, ELIMINATION 10, SHOT_FIRED 19.

Metricas de partida:

| Metrica | Valor |
|---|---|
| totalEliminations | 10 |
| totalDamageDealt | 1015 |
| eliminationsPerMinute | 6.036 |
| mostEliminationsByOnePlayer | 6 |
| averageEliminationDistanceMeters | 26.9 |

Metricas por jugador:

| Metrica | player-21 | player-22 | player-23 |
|---|---|---|---|
| eventCount | 21 | 15 | 11 |
| eliminations | 6 | 4 | 0 |
| deaths | 2 | 3 | 5 |
| killDeathRatio | 3 | 1.333 | 0 |
| damageDealtTotal | 535 | 355 | 125 |
| damageDealtAverage | 89.167 | 88.75 | 25 |
| shotsFired | 8 | 6 | 5 |
| shotsHit | 6 | 4 | 5 |
| accuracyPercent | 75 | 66.667 | 100 |
| damagePerElimination | 89.167 | 88.75 | no definida |
| longestEliminationDistanceMeters | 55.25 | 55.25 | no definida |

Patrones, en orden:

1. KILL_STREAK de player-22: streakLength 4. Desde evt-match-combat-001-0008 hasta evt-match-combat-001-0022.
2. SCORE_COMEBACK de player-21: maxDeficit 4, finalEliminations 6. Desde evt-match-combat-001-0022 hasta evt-match-combat-001-0049.
3. KILL_STREAK de player-21: streakLength 6. Desde evt-match-combat-001-0025 hasta evt-match-combat-001-0048.

player-23 no tiene eliminaciones, por eso damagePerElimination y longestEliminationDistanceMeters se omiten.

## combat / patterns-not-triggered (match-combat-002)

Mapa neon-docks, 3 jugadores, 7 eliminaciones, 35 eventos, duracion 68.6 segundos, ranking player-42, player-41, player-43.

Metricas de partida: totalEliminations 7, totalDamageDealt 705, eliminationsPerMinute 6.122, mostEliminationsByOnePlayer 4, averageEliminationDistanceMeters 30.714.

Metricas por jugador:

| Metrica | player-41 | player-42 | player-43 |
|---|---|---|---|
| eventCount | 10 | 15 | 8 |
| eliminations | 2 | 4 | 1 |
| deaths | 2 | 2 | 3 |
| killDeathRatio | 1 | 2 | 0.333 |
| damageDealtTotal | 210 | 355 | 140 |
| damageDealtAverage | 70 | 88.75 | 46.667 |
| shotsFired | 4 | 6 | 3 |
| shotsHit | 3 | 4 | 3 |
| accuracyPercent | 75 | 66.667 | 100 |
| damagePerElimination | 105 | 88.75 | 140 |
| longestEliminationDistanceMeters | 42.5 | 55.25 | 42.5 |

Patrones: ninguno. Casi disparos: la mejor racha es de dos eliminaciones (player-41 y player-42) y la maxima desventaja del ganador player-42 es de dos eliminaciones, por debajo del umbral de tres.

## Notas para quien implemente

- Los identificadores de evento tienen la forma `evt-{matchId}-{numero}` con numero de cuatro digitos, en orden de timestamp.
- En combat la lista de jugadores incluye a quien solo aparece como `victimPlayerId`.
- La desviacion estandar es poblacional (se divide entre n).
- Las metricas no definidas no aparecen en el mapa de metricas.

## Agregados sobre las cuatro partidas de ejemplo

Se obtienen analizando las cuatro partidas y consultando `/stats/games/{gameId}` y `/stats/players/{playerId}`. Los valores salen de aplicar la media, el minimo y el maximo a los `*.expected.json` de cada par de partidas. Las pruebas de `ExampleAggregatesSpec` los verifican.

Juego racing (2 partidas):

| Agregado | Valor |
|---|---|
| matchesAnalyzed, matchesCompleted | 2, 2 |
| min_fastestLapMs, max_fastestLapMs, mean_fastestLapMs | 88000, 89500, 88750 |
| mean_totalLapsCompleted | 14 |
| patternCount_IMPROVING_LAP_STREAK | 2 |
| patternCount_RACE_COMEBACK | 1 |

Juego combat (2 partidas):

| Agregado | Valor |
|---|---|
| matchesAnalyzed | 2 |
| min_totalEliminations, max_totalEliminations, mean_totalEliminations | 7, 10, 8.5 |
| mean_mostEliminationsByOnePlayer | 5 |
| patternCount_KILL_STREAK | 2 |
| patternCount_SCORE_COMEBACK | 1 |

Jugadores (cada uno juega una sola partida, por eso media, minimo y maximo coinciden):

| Jugador | Agregados |
|---|---|
| player-04 | matchesPlayed 1, wins 1, mean_bestLapMs 89500, min y max_overtakesMade 3, patternCount_IMPROVING_LAP_STREAK 1, patternCount_RACE_COMEBACK 1 |
| player-21 | wins 1, max_eliminations 6, mean_killDeathRatio 3, patternCount_KILL_STREAK 1, patternCount_SCORE_COMEBACK 1 |
| player-23 | wins 0, mean_deaths 5, sin patternCount, sin mean_damagePerElimination (la metrica no esta definida para el) |
