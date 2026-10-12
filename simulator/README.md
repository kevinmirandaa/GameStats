# simulator

Fuente de eventos simulada. Genera partidas de varios juegos en paralelo y las envia a `ingest-go` por HTTP, igual que lo haria un juego real. No comparte codigo con los otros modulos: solo conoce el contrato de `docs/BLUEPRINT.md`.

Go, solo libreria estandar.

## Escenarios

| Escenario | Que genera | Partidas por juego |
|---|---|---|
| normal | partidas aleatorias sin forzar nada | 4 |
| comeback | remontada de carrera y remontada por marcador | 4 |
| killstreak | racha de eliminaciones y racha de vueltas mejores | 4 |
| out-of-order | eventos con vecinos intercambiados | 4 |
| duplicates | algunos eventos enviados dos veces | 4 |
| invalid-events | un evento corrupto cada diez (seis tipos de error) | 4 |
| disconnect | un jugador que abandona la partida | 4 |
| unfinished-match | partida sin MATCH_FINISHED y sin el final | 4 |
| bulk | volumen alto sin ritmo | 200 |
| mixed-parallel | mezcla todo lo anterior, es el escenario de la demo | 12 |

Cada partida tiene una historia: `Natural`, `Comeback`, `Streak` o `Quiet`. La historia `Quiet` garantiza que no se dispara ninguna regla (casi disparos incluidos), y las otras garantizan el patron que dicen. Las pruebas lo comprueban con calculos propios, independientes del analizador de Scala.

## Uso

```bash
go run ./cmd/simulator -target http://localhost:8080 -scenario mixed-parallel
go run ./cmd/simulator -scenario bulk -matches 50 -seed 7
go run ./cmd/simulator -scenario normal -events-per-second 20
```

| Bandera | Variable de entorno | Defecto |
|---|---|---|
| -target | SIMULATOR_TARGET_URL | http://localhost:8080 |
| -scenario | SIMULATOR_SCENARIO | mixed-parallel |
| -matches | SIMULATOR_MATCHES_PER_GAME | 0 (el del escenario) |
| -players | SIMULATOR_PLAYERS_PER_MATCH | 4 (de 4 a 8) |
| -seed | SIMULATOR_SEED | 42 |
| -events-per-second | SIMULATOR_EVENTS_PER_SECOND | 0 |
| -run-id | SIMULATOR_RUN_ID | segundos desde 1970 |
| -games | SIMULATOR_GAMES | racing,combat |
| -batch-size | SIMULATOR_BATCH_SIZE | 25 |
| -parallel | SIMULATOR_PARALLEL | 16 |
| -start-time | SIMULATOR_START_TIME | ahora (RFC 3339) |
| -wait-seconds | SIMULATOR_WAIT_SECONDS | 30 |

Con `-events-per-second` mayor que cero cada evento se envia con `POST /events` al ritmo indicado (tiempo real simulado). Con cero se usa `POST /events/batch` en bloques (volcado rapido).

La misma semilla genera las mismas partidas. Para que ademas los timestamps sean identicos hay que fijar tambien `-start-time`. El `-run-id` va dentro de los identificadores de partida, asi que repetir la simulacion con el mismo servidor no choca con partidas ya cerradas.

Al terminar imprime `matches=... sent=... accepted=... rejected=... failed=...` y sale con codigo distinto de cero si algun evento no pudo entregarse. Los rechazos esperados (duplicados y eventos invalidos) no cuentan como fallo.

## Agregar un juego

1. Crear `internal/games/nuevojuego.go` con un tipo que cumpla `GameMatchGenerator` (`GameId`, `PlayerPrefix`, `Generate`).
2. Agregarlo a la lista de `All()` en `internal/games/generator.go`.

Los escenarios y el ejecutor lo usan sin cambios.

## Comandos

```bash
go test -race ./...
go vet ./...
docker build -t gamestats-simulator .
```
