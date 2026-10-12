# Ejemplos de eventos

- `valid/` eventos que deben aceptarse. Incluye un juego ficticio llamado `treasure-hunt` para comprobar que el sistema no depende de racing ni de combat.
- `invalid/` eventos que deben rechazarse. El nombre del archivo indica el motivo.

Cada archivo contiene un solo evento. Todos se validan contra `contracts/event.schema.json`.

Notas para quien implemente un validador:

- `timestamp` debe ser RFC 3339. Se aceptan desplazamientos de zona horaria (ver `valid/timestamp-with-offset.json`) y deben normalizarse a UTC.
- `format: date-time` del esquema solo se comprueba si el validador tiene los formatos activados. Go lo valida con `time.Parse`.
- `data` ausente equivale a objeto vacio.
