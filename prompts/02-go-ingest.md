# Fase 02 Go ingesta

Agente: go-ingest

## Leer primero

`agents/go-ingest/AGENT.md`, `agents/go-ingest/MEMORY.md`, `agents/go-ingest/SKILLS.md`, `agents/shared/PROJECT_CONTEXT.md`, `docs/BLUEPRINT.md`, `agents/shared/CODE_STANDARDS.md`, `contracts/event.schema.json`, `contracts/examples/`.

## Objetivo

Implementar `ingest-go` completo segun las secciones 5, 6 y 7 del Blueprint.

## Entregables

1. Modulo Go con `go.mod`, entrada en `cmd/ingest/`, paquetes en `internal/`.
2. Endpoints: `POST /events`, `POST /events/batch`, `GET /health`, `GET /metrics`, `GET /rejected`.
3. Validacion del sobre que reporta todos los motivos de rechazo a la vez.
4. Deduplicacion por eventId.
5. Registro de partidas con una goroutine por partida y channels. Cierre por MATCH_FINISHED o por inactividad.
6. Ordenamiento por timestamp al cerrar y envio de MatchBatch a Scala con reintentos y espera creciente.
7. Registro de eventos rechazados en archivo y en memoria.
8. Configuracion por variables de entorno con valores por defecto.
9. Cierre ordenado ante senal de terminacion sin perder partidas activas: las enviadas como INCOMPLETE.
10. Pruebas: validacion con todos los ejemplos de `contracts/examples/`, concurrencia con muchas partidas simultaneas bajo `go test -race`, cierre por inactividad, reintentos con un cliente falso, API con httptest.
11. Dockerfile multi etapa y README corto del modulo.

## Criterios de aceptacion

- `go vet ./...`, `gofmt -l .` sin salida y `go test -race ./...` en verde.
- Los eventos de un juego ficticio nuevo pasan sin tocar el codigo.
- Ninguna referencia a racing ni combat en el codigo.
- Sin comentarios, sin emojis, sin separadores decorativos.
- Puede probarse a mano con curl contra un servidor Scala falso incluido en las pruebas.

## Fuera de alcance

Calcular estadisticas, Docker Compose, el simulador.

## Reporte

Escribir `reports/02-go-ingest.md` con arquitectura de goroutines (un diagrama de texto), decisiones, comandos para probar y problemas pendientes. Mostrar el resumen en pantalla.
