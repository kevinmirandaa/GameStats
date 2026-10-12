# Memoria del Arquitecto

## Decisiones vigentes

- Comunicacion Go a Scala: REST con JSON. Sin broker.
- Evento con sobre comun y campo `data` libre. Go valida solo el sobre.
- `type` es un conjunto cerrado generico. `action` es abierto y lo define cada juego.
- Metricas en Scala como mapas texto a numero para permitir juegos nuevos sin cambiar el modelo.
- Analizadores por juego registrados por `gameId`.
- Juegos iniciales: racing y combat.

## Aprendizajes

- Fase 00: el esquema valida 12 ejemplos validos y rechaza 17 invalidos con Ajv 8 (2020-12). Para revalidar, instalar `ajv` y `ajv-formats` fuera del proyecto.
- El esquema no cubre unicidad de eventId ni validez real de fechas sin formatos activados: lo hace Go.
- `additionalProperties: false` en el sobre: todo campo extra debe ir dentro de `data`.
