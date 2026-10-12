# QA fase 03 Scala nucleo y API

## Veredicto: APROBADO

No hay hallazgos bloqueantes. Hay hallazgos menores para vigilar en la fase 04.

## Comandos ejecutados

- `sbt -batch test`: 37 pruebas, 0 fallos.
- `sbt -batch stage`: exitoso.
- Binario `target/universal/stage/bin/analytics-scala` con PORT=18081, probado con curl.
- Binario Go compilado con `go build -o <scratchpad>/ingest ./cmd/ingest`, con ANALYTICS_URL=http://127.0.0.1:18081 y PORT=18080. Procesos terminados al final, puertos libres.
- Docker no se construyo (Docker Desktop cerrado).

## Criterios

| Criterio | Resultado | Evidencia |
|---|---|---|
| sbt test en verde | Cumple | 37 de 37 |
| Juego desconocido produce analisis generico sin error | Cumple | POST /analyze con gameId "chess": 200, conteos y jugadores correctos, listado con patternCount 0 |
| Sin var, null ni throw | Cumple | grep sobre src/main: cero coincidencias (solo `.get` de Map/Option seguros). Pruebas usan `.toOption.get`, aceptable en tests |
| Sin referencias a racing ni combat en el nucleo | Cumple | grep sobre src/main sin coincidencias; solo aparecen en pruebas como datos de ejemplo |
| Sin comentarios, emojis ni separadores | Cumple | grep y script Python sobre scala, README y Dockerfile |
| Enviar MatchBatch con curl y consultar | Cumple | 4 partidas de ejemplo: 200 con MatchAnalysis; consultas posteriores correctas |
| Analisis generico igual a expected.json | Cumple | Las pruebas leen los 4 `*.expected.json` y comparan matchId, status, totalEvents, duracion, conteos por tipo y accion, y jugadores con eventCount. Por curl: racing 30 eventos 383.3 s; combat 49 eventos 99.4 s, 21/15/11 eventos por jugador |
| POST /analyze | Cumple | 200 con analisis; reenvio de la misma partida devuelve 200 y no duplica (listado sigue con 4 partidas) |
| POST /analyze invalido | Cumple | `{"matchId":"x"}` 400 "gameId is required"; texto no JSON 400; cuerpo vacio 400 |
| GET /stats/matches y ?gameId= | Cumple | Lista completa y filtrada por combat (2) |
| GET /stats/matches/{id} | Cumple | 200 y 404 con mensaje |
| GET /stats/players/{id} | Cumple | player-21: matchesPlayed 1, wins 1; inexistente 404 |
| GET /stats/games/{id} | Cumple | racing: matchesAnalyzed 2, matchesCompleted 2; inexistente 404 |
| GET /patterns?gameId=&type= | Cumple | 200 con lista (vacia sin analizadores de juego) |
| GET /health | Cumple | `{"status":"UP"}` |
| Decodificacion con Either | Cumple | BatchDecoder devuelve Either[String, MatchBatch], indica la posicion del evento fallido |
| Registro y analizador de ejemplo | Cumple | ToyAnalyzer en pruebas demuestra metricas, patrones, orden de patrones y additionalPlayerIds |
| Decision additionalPlayerIds | Cumple | Funcion con valor vacio por defecto; el pipeline la usa; prueba "players mentioned only in data" |
| Decision details numerico | Cumple | `DetectedPattern.details: Map[String, Double]`, codificado como numeros |
| Agregados genericos con fold | Cumple con matiz | Usa groupMap, groupMapReduce, sum, min, max (sin foldLeft literal); prefijos mean_, min_, max_ sin conocer juegos |
| Almacenamiento detras de trait, concurrente | Cumple | AnalysisStore + AtomicReference; prueba de guardados concurrentes |
| Efectos solo en el borde | Cumple | Dominio, pipeline, agregados y encoder puros; efectos en store, Main y rutas |
| Integracion real con Go | Cumple | POST /events/batch con 30 eventos racing y 49 combat: accepted 79, rejected 0. Metrics: matchesSent 2, matchesFailed 0. Scala listo match-racing-G01 y match-combat-G01 con 30 y 49 eventos, 383.3 s y 99.4 s |
| Dockerfile multi etapa | NO VERIFICADO | Revisado por lectura: etapa sbt+JDK, etapa JRE, usuario no root, HEALTHCHECK con curl instalado, version de sbt coincide con build.properties. No se construyo la imagen |
| README del modulo | Cumple | Estructura, endpoints, como agregar un analizador, comandos |
| Archivos pertenecen al agente | Cumple | Cambios en analytics-scala, agents/scala-analytics/MEMORY.md, docs/BLUEPRINT.md, docs/DECISIONS.md, reports/03 |
| Decisiones registradas | Cumple | D20, D21, D22 y la decision de additionalPlayerIds en DECISIONS.md |

## Hallazgos bloqueantes

Ninguno.

## Hallazgos menores

1. Las pruebas del analisis generico no comparan `winnerPlayerId` con los expected.json (no lo traen); la prueba del ganador usa un valor fijo "player-04". Agregar comparacion contra el MATCH_FINISHED de cada batch.
2. Los agregados se prueban solo con datos sinteticos; no se contrastan con `docs/STATS_EXPECTED_RESULTS.md` (el reporte lo reconoce, queda para la fase 04). Vigilar que la fase 04 lo haga.
3. El decodificador no valida reglas que dependen del tipo (playerId obligatorio en PLAYER_JOINED, action en GAME_ACTION) ni que `gameId` del evento coincida con el del batch. Es coherente con que Go valida el sobre, pero Scala acepta batches directos con esos huecos.
4. Un archivo con varios conceptos: `Aggregation.scala` (MetricStatistics, PlayerAggregation, GameAggregation), `Event.scala` y `Match.scala`. El estandar pide un archivo por concepto principal.
5. `GameAggregation` y `PlayerAggregation` calculan con la lista completa de analisis (`store.all`) en cada consulta; aceptable en memoria, anotar si crece.
6. El `Main` instancia el registro vacio; la fase 04 debe registrar racing y combat ahi.
7. `GET /analyze` responde 405 con texto plano, no JSON (cosmetico).

## Para memoria QA

- Fase 03: las pruebas del nucleo comparan campos genericos contra expected.json pero omiten el ganador; revisar en cada fase que las pruebas cubran todos los campos del expected y no solo los faciles.
