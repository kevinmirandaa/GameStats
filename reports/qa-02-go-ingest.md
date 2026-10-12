# QA fase 02 Go ingesta

## Veredicto: APROBADO

Sin hallazgos bloqueantes. Hay 5 hallazgos menores; el primero conviene corregirlo antes de la fase 06.

## Comandos ejecutados

- `gofmt -l .` sin salida. `go vet ./...` sin salida.
- `go test -race -count=3 ./...` en verde en todos los paquetes con pruebas. Cobertura entre 87% y 100% por paquete.
- Binario real en el puerto 18080 con servidor Scala falso (escrito por QA fuera del repositorio) en el 18081, `MATCH_IDLE_TIMEOUT_SECONDS=2`. Procesos terminados y puertos liberados al final.

## Criterios de aceptacion

| Criterio | Resultado | Evidencia |
|---|---|---|
| gofmt, vet y test -race en verde | Cumple | Salidas vacias y todos los paquetes `ok` con -count=3 |
| Juego ficticio nuevo sin tocar codigo | Cumple | `third-game-*`, partida `zz` y `treasure-hunt` aceptadas y entregadas con 202 |
| Sin referencias a racing ni combat en codigo de produccion | Cumple | grep en archivos no `_test.go`: cero coincidencias |
| Sin comentarios, emojis ni separadores | Cumple | grep de `//` y `/*` (salvo URLs), escaneo Python de emojis y lineas decorativas: cero coincidencias |
| Prueba manual con curl y Scala falso | Cumple | Ver pruebas siguientes |
| 12 ejemplos validos aceptados | Cumple | 12 respuestas 202 |
| 19 ejemplos invalidos rechazados (incluye `data-is-null` y `timestamp-without-time`) | Cumple | 19 respuestas 400 |
| Todos los motivos a la vez | Cumple | `{"type":"x","data":null}` devolvio 8 motivos en una respuesta |
| Duplicado | Cumple | Reenvio de `evt-000001`: 409 y `duplicatesRejected` en 1 |
| Partida desordenada | Cumple | 49 eventos barajados: entregada COMPLETED, orden verificado con fechas parseadas |
| Partida sin MATCH_FINISHED | Cumple | 48 eventos: entregada INCOMPLETE a los 2 s de inactividad |
| Cierre con SIGTERM sin perder partidas | Cumple | Dos partidas activas entregadas al recibir la senal (una INCOMPLETE, otra COMPLETED) y proceso terminado |
| Endpoints batch, health, metrics, rejected | Cumple | Batch `{accepted,rejected,errors}`; metrics con contadores; `/rejected` y archivo con 52 lineas |
| Sin eventos duplicados en el lote enviado | Cumple | eventIds unicos en las 5 partidas recibidas por el falso |
| Variables de entorno con defecto | Cumple | `configuration.go` y su prueba; PORT, ANALYTICS_URL, MATCH_IDLE_TIMEOUT_SECONDS, REJECTED_EVENTS_PATH presentes |
| Reintentos con espera creciente | Cumple | Revision de `analyticsclient` (backoff exponencial) y su prueba con cliente falso |
| Dockerfile multi etapa y README | Parcial | Dockerfile multi etapa y README presentes y leidos. NO VERIFICADO: la imagen no se construyo (Docker Desktop cerrado) |
| Decisiones D16 a D19 en DECISIONS.md | Cumple | Encabezados presentes |

## Revision de concurrencia

- `Submit` mantiene el mutex del registro al encolar y `retire` lo toma antes de drenar el inbox, por lo que ningun evento aceptado queda sin entregar. No se encontro carrera ni perdida de eventos en el cierre.
- Cada goroutine de partida termina por temporizador o por `stopSignal`, y `Shutdown` espera con `WaitGroup`.

## Hallazgos bloqueantes

Ninguno.

## Hallazgos menores

1. Agente go-ingest. En `ingestion/service.go`, el eventId se registra como visto antes de `registry.Submit`. Si Submit falla (inbox lleno 503, apagado 503, partida cerrada), el eventId queda quemado. Verificado: reenvio tras "match is already closed" responde 409 "eventId was already received". Un cliente que reintente tras un 503 pierde el evento. Corregir: liberar el eventId cuando Submit falla (agregar `Forget` en deduplication) o registrar despues de encolar.
2. Agente go-ingest. `serveUntilTerminated` comparte un contexto de 20 s entre `httpServer.Shutdown` y `registry.Shutdown`, pero el envio a Scala permite 30 s y hasta 5 intentos con espera creciente. Con Scala caido al apagar, el proceso puede salir antes de terminar el envio. Documentarlo o alinear los tiempos.
3. Agente go-ingest. `closedMatches` crece sin limite durante la vida del proceso, igual que el registro de eventIds. Aceptable para el alcance (D18), anotarlo como pendiente.
4. Agente go-ingest. Un evento que llega en la ventana de gracia tras MATCH_FINISHED se acepta aunque sea MATCH_STARTED (verificado: partida COMPLETED con 31 eventos). Comportamiento coherente con D17, solo advertir en el README.
5. Agente go-ingest. Los paquetes `ingestion` y `metrics` y `cmd/ingest` no tienen pruebas propias; se cubren indirectamente desde httpapi. Agregar pruebas directas del servicio.

## Notas

- Solo `Server` sin `ReadTimeout`, solo `ReadHeaderTimeout`; riesgo bajo para este proyecto.
- Ningun comando git ejecutado. Archivos de trabajo de QA quedaron en el scratchpad, no en el repositorio.
