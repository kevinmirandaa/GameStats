# analytics-scala

Modulo Scala de analisis. Recibe partidas completas desde `ingest-go`, calcula estadisticas y patrones con colecciones inmutables y funciones puras, guarda los resultados en memoria y los expone por HTTP en JSON.

Contrato: secciones 5, 6, 8 y 10 de `docs/BLUEPRINT.md`. Metricas y reglas de cada juego: `docs/STATS.md`.

Scala 3.3 LTS, sbt 1.10, Java 21, Cask, upickle, munit.

## Estructura

```
domain        Event, MatchBatch, MatchAnalysis y demas modelos inmutables
decoding      BatchDecoder: JSON a MatchBatch con errores explicitos (Either)
analysis      GameAnalyzer, GameAnalyzerRegistry y MatchAnalysisPipeline
aggregation   agregados de jugador y de juego, genericos
encoding      AnalysisEncoder: resultados a JSON
storage       AnalysisStore y su version en memoria
service       AnalyticsService une decodificar, analizar, guardar y consultar
http          AnalyticsRoutes, la capa fina de Cask
```

El dominio, el analisis y los agregados son funciones puras. Los efectos (HTTP y almacenamiento) quedan en los bordes.

## Endpoints

- `POST /analyze` recibe un MatchBatch, responde 200 con el MatchAnalysis o 400 con el motivo.
- `GET /stats/matches?gameId=` lista resumida.
- `GET /stats/matches/{matchId}` analisis completo.
- `GET /stats/players/{playerId}` agregado del jugador.
- `GET /stats/games/{gameId}` agregado del juego.
- `GET /patterns?gameId=&type=` patrones detectados.
- `GET /health`.
- `GET /` panel web de resultados (una sola página HTML en `src/main/resources/dashboard/`). Abrir `http://localhost:8081` en el navegador.

Variable de entorno: `PORT` (8081 por defecto).

## Agregar un analizador de juego

1. Crear una clase que extienda `GameAnalyzer` en `analysis/games/`, con su `gameId` y la funcion pura `analyze(batch)`.
2. Si el juego menciona jugadores solo dentro de `data`, sobrescribir `additionalPlayerIds`.
3. Registrarla en `Main` dentro de `GameAnalyzerRegistry`.

Sin analizador, un juego igual recibe el analisis generico.

## Analizadores incluidos

Cada uno vive en `analysis/games/` y se registra en `Main`. Las definiciones exactas estan en `docs/STATS.md`.

| Juego | Metricas de jugador | Metricas de partida | Patrones |
|---|---|---|---|
| racing | lapsCompleted, bestLapMs, averageLapMs, lapTimeStdDevMs, topSpeedKmh, penaltySecondsTotal, pitStopCount, pitStopTotalMs, overtakesMade, effectiveRaceTimeMs, finalPosition, positionsGained | totalLapsCompleted, fastestLapMs, averageLapMs, penaltyCount, pitStopCount, positionChangesTotal, leadChanges, finishedPlayers | IMPROVING_LAP_STREAK, RACE_COMEBACK |
| combat | eliminations, deaths, killDeathRatio, damageDealtTotal, damageDealtAverage, shotsFired, shotsHit, accuracyPercent, damagePerElimination, longestEliminationDistanceMeters | totalEliminations, totalDamageDealt, eliminationsPerMinute, mostEliminationsByOnePlayer, averageEliminationDistanceMeters | KILL_STREAK, SCORE_COMEBACK |

Una metrica sin datos suficientes se omite del resultado, nunca se rellena con cero.

## Comandos

```bash
sbt test
sbt stage
PORT=8081 target/universal/stage/bin/analytics-scala
docker build -t gamestats-analytics .
```

Prueba manual:

```bash
curl -X POST localhost:8081/analyze -d @../contracts/examples/games/racing/patterns-triggered.batch.json
curl localhost:8081/stats/matches
```
