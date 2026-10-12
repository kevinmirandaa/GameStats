# QA fase 04 Scala analizadores de juego

Veredicto: APROBADO

## Criterios

| Criterio | Resultado | Evidencia |
|---|---|---|
| sbt -batch test en verde | Cumple | Passed: Total 72, Failed 0 |
| sbt -batch stage y binario en PORT=18081 | Cumple | Servicio levantado, POST /analyze y GET /stats/matches/{id} respondieron 200 |
| Verificacion independiente (script Python propio, solo *.batch.json y definiciones de STATS.md) | Cumple | 0 diferencias en metricas de partida, metricas y eventCount de jugador, patrones (nombre, jugador, inicio, fin, details) y orden, en las 4 partidas |
| Variantes INCOMPLETE (sin MATCH_FINISHED, matchId distinto) | Cumple | 0 diferencias: sin finalPosition, positionsGained, finishedPlayers 0, sin RACE_COMEBACK ni SCORE_COMEBACK, KILL_STREAK e IMPROVING_LAP_STREAK siguen detectandose |
| Todas las metricas de jugador y partida de STATS.md | Cumple | 12 + 8 racing, 10 + 5 combat presentes; omitidas cuando no hay datos (bestLapMs, accuracyPercent, damagePerElimination, longestEliminationDistanceMeters, averageEliminationDistanceMeters) |
| Umbrales 4 posiciones, 3 vueltas, 3 eliminaciones, desventaja 3 | Cumple | Constantes comebackWorstPosition=4.0, minimumImprovingLaps=3, minimumStreakLength=3, comebackMinimumDeficit=3; pruebas en el borde (3 vs 4, 2 vs 3) |
| foldLeft de rachas | Cumple | Rachas maximas, iguales no continúan, reporte unico; sin bug hallado |
| Orden de patrones | Cumple | Por posicion de startEventId y luego nombre (pipeline); coincide con el script, incluido el empate en racing-001 |
| Regla de la victima en KILL_STREAK | Cumple | La victima cierra su racha y la reporta si valia 3 o mas; el killer sigue; coincide con el script |
| Nucleo sin juegos | Cumple | Nombres de juego solo en analysis/games y Main |
| Sin var, null, throw, comentarios, emojis, separadores | Cumple | grep y script sobre src y README |
| Funciones cortas y nombres | Cumple | La mas larga ronda 20 lineas (RacingMetrics.forPlayer / CombatMetrics.forPlayer), una sola responsabilidad |
| Pruebas significativas | Cumple | Casos positivos y negativos por regla; las 4 partidas completas contra expected.json; INCOMPLETE, sin ganador, victima sola, K/D con 0 muertes |
| README con metricas por juego | Cumple | Tabla en README.md del modulo |
| Docker | No evaluado | Cerrado por instruccion |

## Hallazgos bloqueantes

Ninguno.

## Hallazgos menores

1. CombatPatterns.killStreaks: si killerId == victimId (autoeliminacion) la racha previa se reporta como cerrada y a la vez se extiende y sigue abierta, porque `killerStreak` se lee de `open` y no de `withoutVictim`. STATS.md no define autoeliminaciones y no hay prueba. Agente: scala-analytics.
2. Faltan pruebas unitarias para: dos rachas del mismo jugador separadas por una muerte (ambas reportadas), RACE_COMEBACK con empate de peor posicion en varias vueltas ya cubierto, pero no el caso de ganador sin LAP_COMPLETED.
3. Las pruebas de ejemplos usan `.toOption.get` en el codigo de prueba (aceptable, solo test).
4. Los agregados de /stats/games y /players dependen del almacen compartido; el script dejo partidas -inc en el proceso de QA (efimero, no afecta el repositorio).

## Procesos

Servicio en 18081 detenido tras la verificacion. Script: scratchpad indep.py.
