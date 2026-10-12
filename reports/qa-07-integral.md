# QA fase 07 Integral

Agente: qa. Rama: feature/fase-07-qa-full. Fecha de ejecucion: 2026-10-12.

Veredicto: APROBADO

Bloqueantes: ninguno. Importantes: 3. Menores: 9. Pendientes de la fase 08: CI/CD y deployment (no cuentan como fallo).

Se partio y se termino con `make down`. Tras cada script `docker ps -a --filter label=com.docker.compose.project=gamestats -q | wc -l` devolvio 0. No se modifico codigo de ingest-go, analytics-scala, simulator ni deploy. Solo se usaron los puertos 8080 y 8081.

## Archivos nuevos (propiedad de qa)

- `qa/integration.sh`: extremo a extremo contra Docker (68 verificaciones, sale con 1 si falla alguna, limpia con `make down`).
- `qa/event_builder.py`: genera partidas de cualquier juego (orden natural, inverso o aleatorio, duplicados, invalidos, sin cierre).
- `qa/load.sh`: prueba de carga con 200 partidas por juego, dos rondas, partidas abiertas simultaneas, memoria y latencias.
- `qa/check-standards.sh` y `qa/standards_scanner.py`: revision automatizada de estandares con archivo y linea.
- Los scripts no llevan comentarios ni emojis y usan bash 3.2, curl y python3. `check-standards.sh` se probo contra un conjunto de archivos con defectos plantados (comentarios Go, Scala, shell, Dockerfile, emoji, `var`/`null`/`throw`, `_ =`) y detecto cada uno, sin falsos positivos con `//` o `#` dentro de cadenas.

## Comandos de base ejecutados

| Comando | Resultado |
|---|---|
| `cd ingest-go && gofmt -l . && go vet ./... && go test -race ./...` | sin salida de gofmt, vet limpio, 9 paquetes ok |
| `cd simulator && gofmt -l . && go vet ./... && go test -race ./...` | sin salida de gofmt, vet limpio, 6 paquetes ok |
| `cd analytics-scala && sbt -batch test` | `Passed: Total 72, Failed 0, Errors 0` |
| `make smoke` | 15 verificaciones OK, "PRUEBA DE HUMO APROBADA" |
| `./qa/integration.sh` | 68 OK, "INTEGRACION APROBADA", exit 0 (3 ejecuciones consecutivas; la primera fallo 6 por errores de mis expectativas, que se corrigieron, y demostro que el script falla con exit 1) |
| `./qa/load.sh` | todas las verificaciones OK, "CARGA APROBADA" (3 ejecuciones) |
| `./qa/check-standards.sh` | exit 1 por los hallazgos de la seccion de estandares |

## 1. Matriz de cumplimiento del enunciado

### Funcionalidades minimas (seccion 9 del enunciado)

| N | Funcionalidad | Estado | Evidencia ejecutada |
|---|---|---|---|
| 1 | Registrar o recibir eventos | Cumple | `integration.sh` paso 1: 12 de 12 ejemplos de `contracts/examples/valid` responden 202 en POST /events; paso 4: POST /events/batch acepta 24 de 24 |
| 2 | Validar y procesar eventos | Cumple | Paso 2: 19 de 19 ejemplos de `contracts/examples/invalid` responden 400; JSON roto 400; objeto en /events/batch 400; GET /rejected lista 20 rechazos; paso 3: reenvio responde 409 |
| 3 | Identificar juegos, partidas y jugadores | Cumple | Paso 10: `/stats/matches/{id}` devuelve gameId, matchId y `players[]`; `/stats/games/chess` y `/stats/players/kasparov` responden 200 |
| 4 | Procesar multiples eventos de una misma partida | Cumple | Paso 6: 45 partidas concurrentes con eventos mezclados entre si, todas con exactamente 45 eventos en Scala (0 partidas con conteo incorrecto); paso 8b: una partida de 30004 eventos llega completa |
| 5 | Calcular estadisticas definidas por el grupo | Cumple | `/stats/matches/match-racing-smoke-...-001` devuelve `matchMetrics` (averageLapMs 89735.45, fastestLapMs 85500, leadChanges 1, ...) y metricas por jugador (bestLapMs, lapTimeStdDevMs, overtakesMade, ...). Detalle en `docs/STATS.md` |
| 6 | Estadistica agregada por jugador | Cumple | `GET /stats/players/kasparov` -> `matchesPlayed` 1, `wins` 1; en el smoke `fighter-02` con 3 partidas. Codigo: `aggregation/Aggregation.scala` (`PlayerAggregation`) |
| 7 | Estadistica agregada por partida | Cumple | `matchMetrics` y `eventCountsByType/ByAction` por partida; `GET /stats/games/racing` -> `matchesAnalyzed` 14, `mean_`, `min_`, `max_` por metrica |
| 8 | Estadistica que combina tipos de evento | Cumple | `effectiveRaceTimeMs` combina LAP_COMPLETED y PENALTY_APPLIED (`RacingMetrics.scala` lineas 33 a 50); en combat la precision combina SHOT_FIRED y DAMAGE_DEALT (`CombatMetrics.scala` lineas 36 a 54) |
| 9 | Regla o patron por secuencia | Cumple | `GET /patterns` tras el smoke: 39 patrones: IMPROVING_LAP_STREAK 17, KILL_STREAK 16, RACE_COMEBACK 3, SCORE_COMEBACK 3. Codigo: `RacingPatterns.scala`, `CombatPatterns.scala` (foldLeft sobre secuencias) |
| 10 | Consultar resultados | Cumple | Siete endpoints GET respondiendo 200 con JSON; latencias de 14 a 29 ms con 2800 partidas almacenadas |

### Go (seccion 6.1)

| Requisito | Estado | Evidencia |
|---|---|---|
| Recibir eventos | Cumple | POST /events y /events/batch (`httpapi/server.go`); pasos 1 y 4 |
| Validar estructura basica | Cumple | `validation/validator.go`; 19 invalidos rechazados con motivos; rechazados registrados en `/data/rejected-events.log` y GET /rejected |
| Administrar el flujo | Cumple | Validar, deduplicar (`deduplication`), enrutar por matchId (`matchregistry`), cerrar por MATCH_FINISHED (gracia 500 ms) o inactividad (5 s en las pruebas), ordenar por timestamp y enviar. Paso 5: llegada en orden inverso y aleatorio produce duracion y ganador correctos |
| Estado por partida | Cumple | Un `matchWorker` por partida acumula los eventos. Metrica `activeMatches` = 2000 con 2000 partidas abiertas y 0 al final (carga) |
| Concurrencia con goroutines y channels | Cumple | `registry.go`: una goroutine por partida con un `chan event.Event` como buzon, `stopSignal` y `WaitGroup` para el cierre. Pruebas con `-race` limpias; 128 emisores paralelos y 2000 partidas simultaneas sin perdida |
| Enviar a Scala | Cumple | `analyticsclient` con reintentos y espera creciente; 800 partidas entregadas, 0 fallos (carga) |

### Scala (seccion 6.2)

| Requisito | Estado | Evidencia |
|---|---|---|
| Recibir y decodificar | Cumple | `decoding/BatchDecoder.scala`, POST /analyze (400 con motivo si es invalido) |
| Colecciones inmutables | Cumple | `check-standards.sh`: 0 usos de `var`, `null` o `throw` en `src/main` |
| filter | Cumple | `Aggregation.scala:22`, `CombatMetrics.scala:9`, `RacingMetrics.scala:14` |
| map | Cumple | `MatchAnalysisPipeline.scala:11,21`, `GameAnalyzer.scala` (registro por gameId) |
| flatMap | Cumple | `MatchAnalysisPipeline.scala:22` (`flatMap(_.action)`), `Aggregation.scala:8,27` |
| groupBy | Cumple | `CombatMetrics.scala:31`, `RacingPatterns.scala:16`, `Aggregation.scala:28` (`groupMap`) |
| fold | Cumple | `foldLeft` en `CombatPatterns.scala:18,56` y `RacingPatterns.scala:28` |
| reduce | Parcial | No hay `reduce` literal. Se usa `groupMapReduce` (`MatchAnalysisPipeline.scala:32`, `Aggregation.scala:17`) y `sum`/`maxOption`. Ver menor 6 |
| collect | Cumple | `Statistics.scala:14`, `BatchDecoder.scala:95`, `Event.scala:19,32` |
| Composicion de funciones | Parcial | Pipeline de funciones puras (`MatchAnalysisPipeline.analyze` encadena orden, analizador, metricas, patrones), pero sin `andThen`/`compose` explicitos. `sliding` (citado en Blueprint seccion 8) no se usa. Ver menor 6 |
| Estadisticas | Cumple | Punto 5 de funcionalidades minimas; 72 pruebas, incluidas comparaciones contra `docs/STATS_EXPECTED_RESULTS.md` |
| Reglas por secuencia | Cumple | 4 patrones detectados en ejecucion real |
| Resultados JSON | Cumple | `encoding/AnalysisEncoder.scala`; todos los endpoints devuelven JSON |
| Efectos en el borde | Cumple | Dominio y analisis puros; efectos solo en `http/` y `storage/` (AtomicReference) |
| Genericidad | Cumple | Ver seccion 3 |

### Comunicacion, simulacion e infraestructura

| Requisito | Estado | Evidencia |
|---|---|---|
| REST documentado | Cumple | `docs/BLUEPRINT.md` secciones 5 y 6, `ingest-go/README.md`, `analytics-scala/README.md`, `contracts/event.schema.json`. Menor: el 503 de Go no esta en el Blueprint |
| Varias partidas, jugadores, resultados | Cumple | Bulk 50 por juego: 100 partidas, 5063 eventos; carga: 400 partidas por ronda |
| Juegos en paralelo | Cumple | racing y combat intercalados con `parallel` 16, 64 y 128 |
| Casos normales y especiales | Cumple | 10 escenarios (`simulator/README.md`); mixed-parallel: 965 enviados, 937 aceptados, 28 rechazados (duplicados e invalidos), desorden y desconexiones; unfinished-match: 4 de 4 INCOMPLETE |
| Git con historial | Cumple | `git log --oneline`: 29 commits con prefijos feat/fix/docs/test/build, 8 ramas por fase, 7 merges de PR; `.gitignore` excluye `target/` y binarios |
| Build reproducible | Cumple con matiz | Los tres modulos construyen desde cero en Docker (`up -d --build --wait` en cada ejecucion); Dockerfile multi etapa y versiones fijadas por etiqueta (no por digest; sin lockfile de sbt). Menor 7 |
| Docker | Cumple | 3 Dockerfile, usuario no root (verificado en QA 06), imagenes 421 MB / 21 MB / 21 MB |
| Compose / ejecucion integrada | Cumple | `make up`, `make simulate`, `make smoke`, `make down` |
| CI/CD | PENDIENTE fase 08 | No existe `.github/` |
| Deployment | PENDIENTE fase 08 | Sin entorno de despliegue definido |

### Entregables (seccion 11)

| Entregable | Estado | Ruta |
|---|---|---|
| Codigo Go y Scala | Cumple | `ingest-go/`, `analytics-scala/`, `simulator/` |
| Especificacion del formato de eventos | Cumple | `contracts/event.schema.json`, `docs/BLUEPRINT.md` seccion 5, `contracts/examples/` |
| Tipos de juegos y justificacion | Cumple | `contracts/games/racing.md`, `contracts/games/combat.md`, `docs/STATS.md` (secciones "Justificacion" en lineas 61 y 150) |
| Estadisticas y reglas | Cumple | `docs/STATS.md` (235 lineas), `docs/STATS_EXPECTED_RESULTS.md` |
| Dockerfiles y configuracion | Cumple | `*/Dockerfile`, `deploy/docker-compose.yml`, `deploy/.env.example` |
| Pipeline CI/CD | PENDIENTE fase 08 | `.github/workflows/` no existe |
| Documentacion de instalacion, ejecucion y despliegue | Parcial | Instalacion y ejecucion en `deploy/README.md` y READMEs de modulo; despliegue pendiente fase 08 |
| Demostracion funcional | PENDIENTE fase 09 | `make smoke` y `make simulate` sirven de base; no hay guion de demo |

## 2. Pruebas de extremo a extremo (`qa/integration.sh`)

| Paso | Que se hizo | Resultado |
|---|---|---|
| 1 y 2 | Los 12 validos y los 19 invalidos del repositorio | 12 de 12 con 202, 19 de 19 con 400 |
| 3 | Duplicado | 409 |
| 4 | Lote de 24 validos + 4 duplicados + 3 invalidos en una llamada | accepted 24, rejected 7; Scala recibe 24 |
| 5 | Orden inverso (34 eventos, MATCH_FINISHED primero) y orden aleatorio (65 eventos) | COMPLETED, 34 y 65 eventos, duracion 33 y 64 s, ganador correcto |
| 6 | 45 partidas (racing, combat, chess) enviadas por 45 curl en paralelo | 45 analizadas, 2025 de 2025 eventos, 0 partidas con conteo erroneo |
| 7 | Simulador bulk, 50 por juego | 100 partidas, 5063 enviados = 5063 aceptados por el simulador = 5063 en Scala = delta de `eventsAccepted` en Go; 100 entregadas, 100 COMPLETED, 0 rechazos |
| 8 | mixed-parallel | 24 partidas; 965 enviados = 937 aceptados + 28 rechazados; 937 eventos en Scala (coincide exacto); 0 fallos |
| 8b | Una partida de 30004 eventos en una sola llamada | 30004 aceptados, 0 rechazos por buzon lleno, 30004 en Scala |
| 9 | Partida sin MATCH_FINISHED (15 eventos) | A 1 s aun 404; tras la inactividad llega INCOMPLETE con 15 eventos y sin ganador; un evento tardio de esa partida recibe 409 |
| 11 | Scala detenido, se envian 14 eventos, Scala vuelve 3 s despues | Go acepta los 14; la partida se entrega COMPLETED tras los reintentos; `matchesFailed` sin cambio; Scala responde /health 1 a 2 s despues de `start` |
| 12 | Scala detenido, se envian 14 eventos y se deja caido | Go declara la entrega fallida a los 17 s (`matchesFailed` +1). Al volver Scala la partida no esta (404). Reenviar la partida completa: 0 aceptados, 14 rechazados (eventId y partida ya registrados). Tras cada reinicio Scala pierde todo su historial (0 partidas de la corrida) |

Comportamiento real con Scala caido, resumido: Go sigue aceptando eventos (202/200), entrega en segundo plano tras la gracia, reintenta 6 veces con espera creciente (500 ms, 1, 2, 4, 8 s: unos 15 s). Una caida de menos de ese margen es transparente. Una caida mayor pierde la partida sin cola ni recuperacion y el cliente no puede reenviarla. El almacen de Scala es en memoria: cualquier reinicio borra todo.

## 3. Prueba de genericidad (paso 10 de `integration.sh`)

Se enviaron en un solo POST /events/batch, mezclados al azar, los 2 eventos de `contracts/examples/valid/third-game-*.json` (juego `treasure-hunt`, con matchId e eventId sufijados) y una partida de ajedrez inventada: gameId `chess`, acciones `MOVE` y `CAPTURE`, `data` libre (`{"step":n,"free":{"nested":[n,"x"]}}`), 34 eventos.

| Comprobacion | Resultado |
|---|---|
| Go acepta los 36 eventos, 0 rechazados | OK |
| chess agrupado en una partida con 34 eventos | OK |
| `eventCountsByAction` chess: MOVE 15, CAPTURE 15 | OK |
| Jugadores detectados: karpov, kasparov; eventCount de kasparov 16 | OK |
| Duracion 33 s, ganador kasparov (de MATCH_FINISHED) | OK |
| `matchMetrics` vacio (analisis generico, sin analizador de chess) | OK |
| `GET /stats/games/chess` responde 200 y `matchesAnalyzed` coincide con `/stats/matches?gameId=chess` | OK |
| `GET /stats/players/kasparov` responde 200: `matchesPlayed` 1, `wins` 1 | OK |
| treasure-hunt: INCOMPLETE (sin cierre), ITEM_COLLECTED 1, jugador player-31 | OK |
| Imagenes Docker de ingest-go y analytics-scala con el mismo ID antes y despues (`docker images -q`) | OK, no se cambio ni reconstruyo codigo |

Conclusion: el requisito de genericidad se cumple. Un juego nuevo entra sin cambios y obtiene analisis generico; sus metricas propias requieren un analizador (diseno previsto en `docs/ADDING_A_GAME.md`).

## 4. Estandares de codigo (`qa/check-standards.sh`)

Se escanearon todos los `.go`, `.scala`, `.sbt`, `.sh`, `.yml`, `.py`, Dockerfile, Makefile y los `.md` del repositorio.

| Categoria | Hallazgos |
|---|---|
| Comentarios (Go, Scala, shell, yaml, Dockerfile, Makefile) | 0 |
| Emojis (codigo y Markdown) | 0 |
| Lineas decorativas en codigo | 0 |
| Lineas decorativas en Markdown | 1: `reports/qa-05-simulator.md:29` (`---`) |
| `var`, `null`, `throw` en Scala de produccion | 0 |
| Racing o combat en el nucleo de Scala fuera de `analysis/games` y `Main.scala` | 0 (Main.scala:4 y :16 es el registro permitido) |
| Racing o combat en el codigo de produccion de Go (ingest-go) | 0 |
| Racing o combat en pruebas de ingest-go | 10 (datos de ejemplo en `*_test.go`, informativo) |
| `_ =` en Go | 2: `ingest-go/internal/httpapi/server_test.go:98` y `:110` (pruebas) |
| Valor descartado con `, _ :=` en codigo de produccion Go | 1: `ingest-go/internal/analyticsclient/client.go:91` (`responseBody, _ := io.ReadAll(...)`) |
| Valor descartado con `, _ :=` en pruebas y testsupport | 24 (informativo; la mayoria son aserciones de tipo y `Find`) |

Verificacion cruzada: `grep` de lineas que empiezan con `//` o `#` en el codigo da el mismo resultado de cero comentarios.

## 5. Prueba de carga (`qa/load.sh`)

Entorno: Docker Desktop, 7.75 GiB de memoria, sin limites por contenedor, simulador en el mismo equipo. Escenario bulk, 200 partidas por juego, 2 juegos.

| Metrica | parallel 64 | parallel 128 |
|---|---|---|
| Partidas / eventos | 400 / 20980 | 400 / 20980 |
| Tiempo de envio (incluye arrancar el contenedor del simulador) | 1.5 s (unos 14000 eventos/s) | 1.5 s |
| Hasta ver las 400 partidas analizadas en Scala (desde el inicio del envio) | 2.6 s | 2.6 s |
| Rechazos / fallos de envio | 0 / 0 | 0 / 0 |
| Pico de memoria ingest-go (docker stats, muestreo cada 2 s) | 657 MiB | 759 MiB |
| Pico de memoria analytics-scala | 153 MiB (CPU 110%) | 219 MiB (CPU 58%) |

- Perdida de eventos: ninguna. 41960 eventos aceptados por Go = 41960 en Scala; 800 partidas entregadas, `matchesFailed` 0, `activeMatches` 0.
- Memoria en reposo: ingest-go 4 a 6 MiB, analytics-scala 79 MiB.
- Partidas abiertas simultaneamente: 2000 partidas con un solo evento en un POST /events/batch, aceptadas en 0.4 s. Con ellas abiertas ingest-go usa 2.88 GiB (unos 1.4 MB por partida) y Scala 234 MiB. Cerradas por inactividad y analizadas las 2000, ingest-go sigue en 2.88 GiB (la memoria no se devuelve en la ventana observada) y Scala sube a 289 MiB.
- Latencia de consulta con 2800 partidas almacenadas: /stats/matches 29 ms (397 KB), /stats/games/racing 21 ms, /patterns 20 ms, /stats/players/{id} 14 ms.
- Causa del consumo de Go: `matchregistry/match_worker.go` crea `make(chan event.Event, 8192)` por partida (`MATCH_INBOX_CAPACITY` por defecto 8192; el struct Event pesa unos 176 bytes, o sea 1.4 MB por buzon reservado de antemano).

## 6. Brechas priorizadas

Bloqueantes: ninguna.

Importantes:

| N | Brecha | Agente | Correccion sugerida |
|---|---|---|---|
| 1 | ingest-go reserva un buzon de 8192 eventos por partida (unos 1.4 MB) aunque la partida tenga un evento. 2000 partidas abiertas ocupan 2.88 GiB y la memoria no baja al cerrarlas. Con unas 5000 partidas simultaneas se agota el equipo de pruebas; en Compose no hay limite de memoria | go | Reducir `defaultMatchInboxCapacity` (por ejemplo a 256) o dejar que el buzon crezca bajo demanda; fijar `mem_limit` en Compose |
| 2 | Si Scala permanece caido mas de unos 15 s, la partida se pierde sin cola de reenvio y el cliente no puede reenviarla: sus eventId quedan registrados y la partida figura cerrada, asi que todo reenvio responde 409. `matchesFailed` es el unico rastro. Es lo aceptado en D29, pero no esta documentado que el reenvio sea imposible | go (documentar: devops) | Guardar la partida fallida en el volumen para reenviarla, o al menos documentar el limite y registrar en el log los eventId o la ruta del lote |
| 3 | `client.go:91` descarta el error de `io.ReadAll` (regla 8 de CODE_STANDARDS) | go | Manejar o registrar el error al leer el cuerpo de la respuesta |

Menores:

| N | Brecha | Agente |
|---|---|---|
| 1 | `closedMatches` y el registro de eventId crecen sin limite durante la vida del proceso (una entrada por partida y por evento) | go |
| 2 | Un evento que llega mas de 500 ms despues de MATCH_FINISHED se rechaza con 409 y se pierde (por diseno D17); no consta en el Blueprint | go (documentar: architect) |
| 3 | El almacen de Scala es en memoria: un reinicio borra el historial (aceptado en D09, D29) | scala |
| 4 | `server_test.go:98,110` usan `_ =` sobre `Decode` (pruebas) | go |
| 5 | `reports/qa-05-simulator.md:29` tiene una linea `---` decorativa | simulator |
| 6 | Scala no usa `reduce` literal, `sliding` ni `andThen`/`compose` explicitos; el Blueprint seccion 8 los lista. El enunciado dice que no es obligatorio usarlas todas, pero sumar un uso justificado reforzaria la defensa oral | scala |
| 7 | Las imagenes base estan fijadas por etiqueta, no por digest, y sbt no tiene lockfile; la reproducibilidad exacta no esta garantizada a futuro | devops |
| 8 | Los puertos 8080 y 8081 se publican en todas las interfaces (heredado de QA 06); el 503 de Go no figura en el Blueprint | devops, architect |
| 9 | `qa/smoke.sh` tiene lineas largas y numeros repetidos (heredado de QA 06); `make test` no incluye `gofmt -l` | devops |

Pendientes ya planificados: CI/CD y deployment (fase 08), demo y documentacion final (fase 09).

## Lo que no se verifico

- Ejecucion en Linux o en GitHub Actions.
- Comportamiento con limite de memoria en los contenedores (no hay limites configurados).
- Cargas de mas de 42000 eventos o de mas de 2000 partidas abiertas.
- `make test` como comando unico (se ejecutaron sus tres partes por separado; `sbt -batch test` en local con 72 pruebas).

## Cierre

Todas las funcionalidades minimas, los requisitos de Go y Scala, la comunicacion, la simulacion y la genericidad estan demostrados con ejecuciones reales. No hay bloqueantes. Se recomienda atender la brecha importante 1 antes de la demo y documentar la 2 en `deploy/README.md`.


## Correcciones posteriores al QA

Se corrigieron los hallazgos importantes y los menores de estandares que el QA reporto:

- Buzon por partida reducido a 1024 y `mem_limit` de 1 GB en Compose (D30). Verificado con el sistema real: con 2000 partidas abiertas a la vez, Go usa unos 452 MB (antes 2.88 GiB), y las 2000 se cierran y analizan sin perdida.
- El error de lectura descartado en `analyticsclient/client.go` ahora se maneja, y las dos lecturas descartadas en `httpapi/server_test.go` fallan la prueba si hay error.
- Se elimino el separador decorativo de `reports/qa-05-simulator.md`.
- La perdida de partidas con Scala caido mas de 15 segundos, y que reenviarlas de 409, quedaron documentadas en `deploy/README.md`.

Se repitieron despues de los cambios: `go vet` y `go test -race` de ingest-go, `make smoke` (15 verificaciones), `qa/integration.sh` (68 verificaciones) y `qa/load.sh` (800 partidas entregadas, 0 fallidas, 2000 partidas abiertas analizadas). Todo en verde.

Quedan aceptados como menores: uso de `groupMapReduce` en lugar de `reduce` explicito y ausencia de `sliding` y composicion explicita de funciones en Scala (el enunciado no los exige todos), y el estado de Go sin limite (D18).
