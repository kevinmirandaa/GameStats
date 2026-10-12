# Memoria del Arquitecto

## Decisiones vigentes

- Comunicacion Go a Scala: REST con JSON. Sin broker.
- Evento con sobre comun y campo `data` libre. Go valida solo el sobre.
- `type` es un conjunto cerrado generico. `action` es abierto y lo define cada juego.
- Metricas en Scala como mapas texto a numero para permitir juegos nuevos sin cambiar el modelo.
- Analizadores por juego registrados por `gameId`.
- Juegos iniciales: racing y combat.

## Aprendizajes

Agregar aqui lo que se descubra durante las fases.
