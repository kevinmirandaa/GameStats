# QA fase 05 Simulador

## Revalidacion

Veredicto final: APROBADO

Los dos bloqueantes del primer informe estan corregidos y verificados ejecutando el binario real. Los menores revisados tambien. Quedan solo observaciones menores nuevas, sin impacto funcional.

| Punto | Resultado | Evidencia |
|---|---|---|
| gofmt, vet, test | OK | `gofmt -l .` sin salida, `go vet ./...` limpio, `go test -race -count=2 ./...` en verde en los 6 paquetes con pruebas |
| Bloqueante 1 reproducibilidad | OK | Servidor falso Python en 18092 con `json.dumps(sort_keys=True)`, escenario mixed-parallel, `-run-id same`. Con `-seed 42 -start-time 2026-10-12T18:00:00Z`: `-parallel 1` dos veces y `-parallel 8` dos veces dan 965 eventos y el mismo hash ordenado (0bd5e3fa...) en las cuatro ejecuciones, tambien entre parallel 1 y 8. Semilla 43: 933 eventos, hash distinto. Otro `-start-time` (2026-10-13): hash distinto. Sin `-start-time`: timestamps validos de 22:08:40Z a 22:24:45Z, con la hora actual del sistema (22:08:40Z) como inicio |
| Bloqueante 2 interfaz | OK | Busqueda de la palabra `Generator` suelta en simulator/ sin resultados; `GameMatchGenerator` aparece 16 veces (runner, configuration, main, games) |
| `-events-per-second NaN` y `0.000001` | OK | ambos rechazados: "events per second must be 0 or at least 0.01" |
| `SIMULATOR_SEED=12abc` | OK | rechazado: "SIMULATOR_SEED must be an integer" |
| `-games racing,racing` | OK | rechazado: "games must not repeat" |
| `-run-id 'a b/c'` y `a_b/c` | OK | rechazados: solo letras, digitos, guion y guion bajo |
| `-h` | OK | codigo de salida 0 |
| disconnect sin acciones tras PLAYER_LEFT | OK | 10 semillas (1, 2, 3, 4, 5, 7, 42, 99, 123, 2024), 8 partidas cada una, 8 PLAYER_LEFT por semilla. Comparando timestamps como fechas, 0 GAME_ACTION del jugador que sale posterior a su PLAYER_LEFT. La ultima accion del jugador queda 1 ms antes de la salida. Siguen existiendo acciones de otros jugadores que lo tienen como `targetPlayerId` (dano recibido), legitimas |
| PLAYER_LEFT valido | OK | todos los PLAYER_LEFT capturados tienen exactamente los campos del sobre mas `playerId` y `data` objeto con `reason`; mismo formato que `contracts/examples/valid/player-left-combat.json` |
| Comentarios, emojis, errores ignorados | OK | cero comentarios en .go; cero caracteres no ASCII; los unicos `_` descartados son los `_ , err` / `_ , known` de uso normal y los `value, _ :=` ya conocidos de `internal/testsupport/rules.go` (menor 11 previo) |
| Funciones largas | Observacion | mayores de 30 lineas: `Load` 43 (configuration.go), `EnvelopeProblems` 44 (testsupport), `run` 36 (main.go), `runner.Run` 34, `Racing.Generate` 34 (ya senalada). Ninguna se considera bloqueante |
| Limpieza | OK | procesos Python y simulator terminados; nada escuchando en 18092 |

Notas de la ejecucion: el servidor falso respondia con un cuerpo propio, por lo que el simulador informo `failed` y salio con codigo 1; no afecta a la comparacion porque se compara lo recibido. Con `-events-per-second` y las validaciones se uso un destino inalcanzable-en-la-practica solo para fallar antes de enviar.

Observaciones menores nuevas, agente simulator: dividir `configuration.Load` (43 lineas) en lectura de banderas, lectura de entorno y validacion; los menores 7 a 12 del informe anterior siguen aceptados como estaban.

---


## Informe anterior (primera revision)

Veredicto de la primera revision: RECHAZADO

Dos bloqueantes, ambos de correccion corta. El resto del modulo cumple y la integracion real con Go y Scala funciona.

## Comandos ejecutados

- `gofmt -l .`: sin salida. `go vet ./...`: limpio.
- `go test -race -count=2 ./...` en `simulator/`: todos los paquetes en verde.
- `go build -o scratchpad/simulator ./cmd/simulator`, servidor falso Python en 18090 que registra cada POST con su ruta, y los 10 escenarios ejecutados dos veces con `-run-id qa -seed 42`.
- Validador Python propio (sin jsonschema, reglas del esquema a mano). Autoprueba: acepta los 12 ejemplos validos y rechaza los 19 invalidos de `contracts/examples`.
- Integracion: ingest-go (18080, idle 3 s) con analytics-scala (18081) y el escenario `unfinished-match`. Procesos terminados al acabar.

## Criterios

| Criterio | Resultado | Evidencia |
|---|---|---|
| gofmt, vet, test -race | OK | salida vacia, vet limpio, 6 paquetes ok con count=2 |
| Eventos de escenarios validos pasan el esquema | OK | normal, comeback, killstreak, out-of-order, duplicates, disconnect, unfinished-match, bulk: 100% validos (418, 265, 299, 418, 504, 426, 332, 20980) |
| Volumen igual al reporte | OK | enviados por escenario coinciden con la tabla del reporte, mixed-parallel 965 |
| Ids unicos por partida | OK | 0 repetidos en todos los escenarios salvo duplicates (86) y mixed-parallel (21, las partidas duplicadas) |
| Orden de timestamps en normal | OK | 0 inversiones en las 8 partidas, empieza con MATCH_STARTED |
| out-of-order desordena | OK | 8 de 8 partidas con inversiones; mismo conjunto de eventos |
| duplicates repite ids | OK | 8 de 8 partidas con ids repetidos y contenido identico |
| invalid-events inyecta invalidos | OK | 39 invalidos (6 tipos: sin eventId, sin matchId, tipo desconocido, timestamp malo, campo extra, data no objeto); mixed-parallel 7 |
| unfinished-match sin MATCH_FINISHED | OK | 0 de 8 partidas con MATCH_FINISHED |
| disconnect incluye PLAYER_LEFT | OK | 8 de 8 partidas |
| Misma semilla, mismo contenido | FALLA | ver bloqueante 1: el contenido coincide salvo `timestamp` |
| Reglas en comeback (racing >= 4, combat deficit >= 3) | OK | calculo propio: 4 de 4 racing con posicion >= 4 y 4 de 4 combat con deficit >= 3 ganando |
| Reglas en killstreak | OK | 4 de 4 racing con 3 vueltas estrictamente mejores y 4 de 4 combat con racha >= 3 |
| Sin patrones en historia Quiet | OK | en mixed-parallel las 6 partidas Quiet (indices 3, 7, 11) no disparan racha de vueltas, racha de bajas ni remontada |
| killstreak no dispara remontada | OK | racing y combat sin comeback en las 8 partidas |
| Posiciones y ranking coherentes con tiempos acumulados | OK | 0 incoherencias en todas las partidas, ranking ordenado por tiempo total |
| Modo -events-per-second usa POST /events | OK | con 2000 eps: todas las peticiones a `/events` (95), cero a `/events/batch`; sin ritmo, solo `/events/batch` |
| No importa codigo de ingest-go | OK | imports solo `gamestats/simulator/...` y libreria estandar; unica mencion es un texto de ayuda y variables de prueba |
| Sin comentarios, emojis ni separadores | OK | busqueda en .go, .md y Dockerfile sin resultados |
| Tercer juego = un archivo + registro | OK | `games.All()` es el unico punto de registro |
| Interfaz `GameMatchGenerator` | FALLA | la interfaz se llama `Generator` (bloqueante 2) |
| Integracion: Scala recibe INCOMPLETE | OK | 8 partidas `unfinished-match` enviadas, ingest-go las cerro a los 3 s, Scala devolvio status INCOMPLETE en las 8, `matchesCompleted` 0, `finishedPlayers` 0, sin RACE_COMEBACK ni SCORE_COMEBACK |
| Dockerfile (solo lectura) | OK | multi etapa, golang:1.22-alpine a alpine:3.20, usuario no root, `SIMULATOR_TARGET_URL` por defecto. go.mod dice go 1.22, coherente |
| Casos limite de configuracion | Parcial | ver menores |

## Bloqueantes

1. La semilla no reproduce el contenido exacto. `StartTime` sale de `time.Now()` truncado al segundo (`configuration.go`, `StartTime: now.UTC().Truncate(time.Second)`) y no hay forma de fijarlo. Dos ejecuciones con `-seed 42 -run-id qa` separadas por un segundo producen eventos distintos en `timestamp` (todos los demas campos son identicos en los 10 escenarios, comparados ignorando timestamp). Las pruebas pasan porque inyectan una hora fija. Correccion: agregar bandera `-start-time` y variable `SIMULATOR_START_TIME` (RFC 3339), con un valor por defecto fijo o documentado, y una prueba de extremo a extremo que ejecute dos veces el binario o `Run` con la misma semilla y compare todo el contenido. Agente: simulator.
2. Nombre de la interfaz. El prompt de la fase pide `GameMatchGenerator` y el codigo la llama `Generator` (`internal/games/generator.go`, usada en runner, configuration y main). Correccion: renombrar a `GameMatchGenerator` y actualizar usos y README. Agente: simulator.

## Menores

1. Un jugador que abandona sigue actuando: en `disconnect`, tras `PLAYER_LEFT` el jugador aun emite o recibe entre 2 y 21 eventos, y en 3 de 8 partidas es el ganador. Es contractualmente valido pero poco realista. Sugerencia: generar el abandono y descartar eventos posteriores del jugador, o excluirlo del ranking.
2. `-events-per-second` admite `NaN` (cae al modo por lotes sin avisar) y valores minusculos como `1e-12` dejan el proceso esperando indefinidamente. Validar con `math.IsNaN`/`IsInf` y un minimo.
3. Las variables de entorno enteras se leen con `Sscanf`, asi que `SIMULATOR_SEED=12abc` se acepta como 12 y `SIMULATOR_EVENTS_PER_SECOND=0.5` no admite decimales aunque la bandera si. Usar `strconv.ParseInt` y `ParseFloat`.
4. `-games racing,racing` genera dos partidas con el mismo `matchId`. Deduplicar la lista de juegos o rechazarla.
5. `-run-id "a b/c"` se acepta y entra en los ids de partida. Validar un conjunto de caracteres seguro.
6. `-h` termina con codigo 1 y un log de error "flag: help requested". Debe terminar con 0.
7. Mensaje de validacion confuso: "must not be negative or zero where a positive value is required" mezcla casos. Separar por campo.
8. Numeros magicos restantes en los generadores: `500`, `400`, `300`, `2000`, `1000`, `0.5`, `5`, `2*time.Second`, `1+random.Intn(3)`, `3`, `2` (racing.go y combat.go); y la cadena `"disconnected"` y `1<<` en sender. Nombrarlos como constantes (regla 6).
9. `Racing.Generate` tiene unas 40 lineas y `recordLaps` mezcla tres eventos distintos; conviene extraer `recordPitStop` y `recordPositionChange`.
10. Reintento de `POST /events` ante 5xx: si el primer intento llego y la respuesta se perdio, el reintento recibira 409 y se contara como rechazado. Documentarlo o tratar 409 tras reintento como aceptado (vigilar el patron de la fase 02).
11. `internal/testsupport` (codigo no de prueba) ignora el segundo valor de las aserciones de tipo en `number` y `text`. Es idiomatico, pero una ayuda de pruebas con nombres `_test` evitaria exportar utilidades.
12. Empates de posicion en racing: los timestamps usan `+slot` ms, asi que con tiempos acumulados iguales la posicion y el orden de llegada pueden discrepar. No se observo en 400 partidas.

## Bloque para el agente simulator

1. Agregar `-start-time` / `SIMULATOR_START_TIME` y una prueba que ejecute dos veces con la misma semilla y compare todo el contenido, timestamps incluidos.
2. Renombrar `Generator` a `GameMatchGenerator`.
3. Atender menores 2 a 6 (validaciones de configuracion).

## Memoria QA

Anadir a `agents/qa/MEMORY.md`: "Fase 05: la reproducibilidad por semilla debe comprobarse ejecutando el binario dos veces con segundos de diferencia, no solo con pruebas que inyectan la hora. Contrastar nombres literales de interfaces del prompt."
