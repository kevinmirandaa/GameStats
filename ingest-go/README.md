# ingest-go

Modulo Go de ingesta. Recibe eventos de cualquier fuente, los valida, los agrupa por partida con una goroutine por partida y envia cada partida completa y ordenada a `analytics-scala`. No calcula estadisticas ni conoce ningun juego concreto.

Contrato: secciones 5, 6 y 7 de `docs/BLUEPRINT.md`. Solo libreria estandar de Go.

## Flujo

```
POST /events  ->  validar sobre  ->  deduplicar eventId  ->  Registry.Submit
                                                                  |
                                          una goroutine por matchId (channel inbox)
                                                                  |
                          MATCH_FINISHED + espera corta, o inactividad -> cierre
                                                                  |
                                 ordenar por timestamp -> POST {ANALYTICS_URL}/analyze
```

## Endpoints

- `POST /events` un evento. 202 aceptado, 400 con todos los motivos, 409 duplicado o partida ya cerrada, 503 sobrecarga o apagado.
- `POST /events/batch` lista de eventos. 200 con `{accepted, rejected, errors}`.
- `GET /health`, `GET /metrics`, `GET /rejected`.

## Variables de entorno

| Variable | Defecto |
|---|---|
| PORT | 8080 |
| ANALYTICS_URL | http://localhost:8081 |
| MATCH_IDLE_TIMEOUT_SECONDS | 30 |
| MATCH_FINISH_GRACE_MILLISECONDS | 500 |
| REJECTED_EVENTS_PATH | rejected-events.log |
| ANALYTICS_MAX_ATTEMPTS | 5 |
| ANALYTICS_INITIAL_BACKOFF_MILLISECONDS | 200 |
| ANALYTICS_REQUEST_TIMEOUT_SECONDS | 10 |
| MATCH_INBOX_CAPACITY | 8192 |

## Comportamiento de partidas

- Una partida se cierra `COMPLETED` poco despues de recibir MATCH_FINISHED. La espera corta permite incluir eventos tardios o desordenados.
- Sin MATCH_FINISHED se cierra `INCOMPLETE` tras el tiempo de inactividad.
- Un evento de una partida ya cerrada se rechaza con 409.
- Al recibir SIGTERM las partidas activas se envian como `INCOMPLETE`.
- Si Scala no responde, se reintenta con espera creciente. Los errores 4xx no se reintentan.

## Comandos

```bash
go test -race ./...
go vet ./...
go run ./cmd/ingest
docker build -t gamestats-ingest .
```

Prueba manual:

```bash
curl -X POST localhost:8080/events -d @../contracts/examples/valid/match-started-racing.json
curl localhost:8080/metrics
```
