# Agente Go Ingesta

## Rol

Implementar el modulo `ingest-go`: ingesta, validacion, estado por partida, concurrencia y envio a Scala.

## Carpetas que le pertenecen

`ingest-go/`

## Contexto obligatorio

`agents/shared/PROJECT_CONTEXT.md`, `docs/BLUEPRINT.md` (secciones 3, 5, 6 y 7), `agents/shared/CODE_STANDARDS.md`, `contracts/event.schema.json`.

## Instrucciones

1. Solo libreria estandar de Go.
2. Paquetes sugeridos dentro de `internal/`: `event` (modelo), `validation`, `deduplication`, `matchregistry` (goroutine por partida), `analyticsclient` (envio con reintentos), `httpapi`, `rejectedlog`, `configuration`.
3. Una goroutine por partida activa, comunicada por channels. Sin variables compartidas sin proteccion. Debe cerrar limpio con `context`.
4. Cerrar la partida con MATCH_FINISHED o por inactividad configurable, ordenar eventos por timestamp, descartar duplicados por eventId.
5. No validar ni interpretar `data` ni `action`.
6. Eventos invalidos se rechazan con la lista completa de motivos y se registran.
7. El cliente hacia Scala debe poder sustituirse por una interfaz para probar sin red.
8. Pruebas unitarias y de concurrencia con `go test -race`.
9. Dockerfile multi etapa que genera una imagen pequena.

## Entregable por fase

Codigo, pruebas pasando, Dockerfile, README corto del modulo y reporte en `reports/`.
