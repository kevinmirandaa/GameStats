# Carpeta de entrada de eventos (inbox)

Ademas de HTTP, `ingest-go` vigila continuamente una carpeta, `/inbox`, dentro de su contenedor. Cualquier fuente puede dejar alli archivos con eventos y GameStats los procesa igual que los recibidos por HTTP: misma validacion, misma deduplicacion y mismo analisis.

## Formatos

| Archivo | Contenido |
|---|---|
| `nombre.jsonl` o `nombre.ndjson` | Un evento JSON por linea. El archivo puede seguir creciendo: se leen solo las lineas nuevas y completas |
| `nombre.json` | Un arreglo de eventos, un objeto con un arreglo `events` (como los ejemplos de `contracts/examples/games`) o un solo evento |

Los archivos `.json` ya procesados se mueven a `/inbox/processed/` y los que no se pudieron leer a `/inbox/failed/` (y quedan registrados en `/rejected`). Los archivos ocultos, los temporales (`.tmp`, `.part`) y las subcarpetas se ignoran.

## Como dejar archivos

La carpeta es un volumen de Docker compartido entre `ingest-go` y el simulador. Con el sistema levantado (`make up`):

```bash
make inbox-add FILE=contracts/examples/games/racing/patterns-triggered.batch.json
make inbox-demo
make simulate-files
```

- `make inbox-add` copia un archivo a la carpeta de entrada.
- `make inbox-demo` copia dos partidas de ejemplo (racing y combat).
- `make simulate-files` hace que el simulador escriba sus partidas como archivos `.jsonl` en la carpeta, en lugar de enviarlas por HTTP.

Sin `make`, el equivalente de `inbox-add` es:

```bash
docker compose -f deploy/docker-compose.yml cp archivo.json ingest-go:/inbox/
```

Para ver el estado de la carpeta:

```bash
docker compose -f deploy/docker-compose.yml exec ingest-go ls /inbox /inbox/processed
curl localhost:8080/metrics
```

`/metrics` informa `filesProcessed`, `filesFailed` y `eventsAcceptedByGame`.

## Usar una carpeta real del computador

Si se prefiere arrastrar archivos a una carpeta normal del computador, se crea la carpeta `inbox/` en la raiz del repositorio y se levanta el sistema con el archivo adicional:

```bash
mkdir inbox
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.inbox-folder.yml up -d --build --wait
```

Requiere que Docker tenga permiso para acceder a esa carpeta. En macOS, las carpetas Descargas, Escritorio y Documentos necesitan un permiso del sistema para Docker Desktop (Ajustes del Sistema, Privacidad y seguridad, Archivos y carpetas); si no se concede, Docker no puede montarla. Por eso el volumen de Docker es la opcion por defecto.

## Que hace el sistema por dentro

El vigilante (`ingest-go/internal/inbox`) revisa la carpeta cada segundo. Un `.jsonl` se lee como un registro que crece. Un `.json` se lee cuando el archivo deja de cambiar entre dos revisiones, para no leer un archivo a medio escribir. Variables: `INBOX_DIRECTORY` (vacia desactiva la vigilancia) e `INBOX_POLL_INTERVAL_MILLISECONDS`.
