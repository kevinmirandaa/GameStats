# Reporte fase 01 Diseno de estadisticas

Agente: stats-designer

## Creado

- `docs/STATS.md`: convenciones de calculo, agregados genericos, y para racing y combat los eventos, metricas por jugador y partida, y reglas formales. Incluye la propuesta de una pagina para estrategia.
- `contracts/games/racing.md` y `contracts/games/combat.md`: contratos autocontenidos para quien implemente un juego real.
- `contracts/examples/games/{racing,combat}/`: dos partidas por juego en formato MatchBatch (una dispara las reglas, otra no) y su `.expected.json`.
- `contracts/examples/games/README.md`.
- `docs/STATS_EXPECTED_RESULTS.md`: resultados esperados resumidos.

## Resumen de diseno

Racing, acciones: LAP_COMPLETED, POSITION_CHANGED, PENALTY_APPLIED, PIT_STOP.
- Por jugador: lapsCompleted, bestLapMs, averageLapMs, lapTimeStdDevMs, topSpeedKmh, penaltySecondsTotal, pitStopCount, pitStopTotalMs, overtakesMade, effectiveRaceTimeMs, finalPosition, positionsGained.
- Por partida: totalLapsCompleted, fastestLapMs, averageLapMs, penaltyCount, pitStopCount, positionChangesTotal, leadChanges, finishedPlayers.
- Combinadas: effectiveRaceTimeMs (vueltas y penalizaciones) y positionsGained (alta de jugador y resultado final).
- Reglas: RACE_COMEBACK e IMPROVING_LAP_STREAK.

Combat, acciones: ELIMINATION, DAMAGE_DEALT, SHOT_FIRED.
- Por jugador: eliminations, deaths, killDeathRatio, damageDealtTotal, damageDealtAverage, shotsFired, shotsHit, accuracyPercent, damagePerElimination, longestEliminationDistanceMeters.
- Por partida: totalEliminations, totalDamageDealt, eliminationsPerMinute, mostEliminationsByOnePlayer, averageEliminationDistanceMeters.
- Combinada: damagePerElimination (daño y eliminaciones).
- Reglas: KILL_STREAK y SCORE_COMEBACK.

Agregados entre partidas definidos de forma generica (`mean_`, `min_`, `max_`, `wins`, `matchesPlayed`, `patternCount_`), de modo que el nucleo de Scala no necesita conocer ningun juego.

## Verificacion

- Los 135 eventos de las cuatro partidas pasan `contracts/event.schema.json` (Ajv 2020-12 con formatos). IDs unicos, cabeceras consistentes, timestamps ordenados.
- Posiciones de racing coherentes con los tiempos acumulados de cada vuelta.
- Resultados esperados recalculados con una segunda implementacion independiente en Python (metricas clave y los cuatro conjuntos de patrones): 0 diferencias.
- Revision a mano de dos valores (desviacion estandar de player-04, disparos y daño de player-23).
- Cada regla tiene caso positivo y negativo. Las partidas negativas incluyen casi disparos.
- Revalidados los 29 ejemplos de la fase 00: sin cambios.
- Sin emojis ni lineas decorativas en los documentos.

## Propuestas y puntos a decidir

1. Sin cambios al sobre del evento.
2. Los identificadores de jugador mencionados en `data` (victimPlayerId) cuentan como participantes. Esto es una regla del analizador de combat, no del contrato. Scala debe poder listar jugadores adicionales por juego. Propuesta para la fase 03: el trait `GameAnalyzer` expone una funcion opcional `additionalPlayerIds(events)` con valor por defecto vacio.
3. Los patrones llevan `details` solo numerico (`Map[String, Double]`). Esto afecta el dominio de la fase 03.
4. RACE_COMEBACK solo puede dispararse con al menos 4 pilotos. El simulador debe respetarlo en sus escenarios de racing.
5. Los scripts que generaron las partidas y calcularon los resultados estan en el directorio temporal de la sesion y no se incluyeron en el repositorio. La logica de generacion se implementara en el simulador (fase 05) usando estas partidas como referencia.

## Estado de las reglas de entrega

No se ejecuto ningun comando Git.
