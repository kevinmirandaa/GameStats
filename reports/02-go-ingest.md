# Reporte fase 02 Go ingesta

## Resultado

Modulo `ingest-go` completo segun las secciones 5, 6 y 7 del Blueprint. Solo libreria estandar.

## Verificado ejecutando

- `go vet ./...` y `gofmt -l .` sin salida.
- `go test -race ./...` en verde, y `-count=5` sobre registro y API sin fallas.
- Los 12 ejemplos validos se aceptan, los 19 invalidos se rechazan (se agregaron `data-is-null` y `timestamp-without-time`), y todos los eventos de las 4 partidas de ejemplo de racing y combat se aceptan.
- Prueba manual con el binario real y un servidor Scala falso: batch de 30 eventos aceptados, evento invalido rechazado con todos los motivos, duplicado con 409, partida entregada COMPLETED con 30 eventos, cierre ordenado con SIGTERM.
- Ninguna referencia a racing ni combat en el codigo de produccion. Sin comentarios.

## No verificado

- El Dockerfile no se construyo porque Docker Desktop no estaba abierto. Se prueba en la fase 06.

## Arquitectura de goroutines

```
HTTP (una goroutine por peticion)
  -> Service.Ingest: validar, deduplicar
  -> Registry.Submit (mutex): elige o crea la partida
        |
        +-- matchWorker partida A (goroutine, channel inbox)
        +-- matchWorker partida B (goroutine, channel inbox)
        +-- matchWorker partida C (goroutine, channel inbox)
              cada una acumula eventos, controla su temporizador
              al cerrar: retirarse del registro, drenar inbox,
              ordenar por timestamp, enviar a Scala con reintentos
```

## Paquetes

event, validation, deduplication, matchregistry, analyticsclient, ingestion, httpapi, rejectedlog, metrics, configuration.

## Decisiones nuevas

D16 a D19 en `docs/DECISIONS.md`.

## Pendientes y notas para otras fases

- El `data` se reenvia tal cual, sin interpretarse.
- El timestamp se normaliza a UTC. Go lo serializa en RFC 3339 con fracciones sin ceros finales, por ejemplo `2026-10-12T18:00:00.25Z`. Scala debe parsearlo con `Instant.parse`.
- Variables nuevas respecto al Blueprint: MATCH_FINISH_GRACE_MILLISECONDS, ANALYTICS_MAX_ATTEMPTS, ANALYTICS_INITIAL_BACKOFF_MILLISECONDS, ANALYTICS_REQUEST_TIMEOUT_SECONDS, MATCH_INBOX_CAPACITY.

## Despues del QA

El QA independiente (`reports/qa-02-go-ingest.md`) aprobo la fase. Se corrigieron dos menores:

- Un evento rechazado por sobrecarga o por apagado ya no queda registrado como visto: puede reenviarse sin recibir 409.
- El tiempo de apagado ahora supera el de envio a Scala.

Quedan aceptados: crecimiento sin limite del registro de eventId y de partidas cerradas (D18), y la ventana de espera tras MATCH_FINISHED (D17).
