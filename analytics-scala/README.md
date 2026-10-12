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

Variable de entorno: `PORT` (8081 por defecto).

## Agregar un analizador de juego

1. Crear una clase que extienda `GameAnalyzer` en `analysis/games/`, con su `gameId` y la funcion pura `analyze(batch)`.
2. Si el juego menciona jugadores solo dentro de `data`, sobrescribir `additionalPlayerIds`.
3. Registrarla en `Main` dentro de `GameAnalyzerRegistry`.

Sin analizador, un juego igual recibe el analisis generico.

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
