# Memoria del Agente Go Ingesta

## Hechos

- Puerto 8080.
- Variables: PORT, ANALYTICS_URL, MATCH_IDLE_TIMEOUT_SECONDS, REJECTED_EVENTS_PATH.
- Payload hacia Scala: MatchBatch con status COMPLETED o INCOMPLETE.
- Los eventos de una partida pueden llegar desordenados: se ordenan al cerrar.

## Aprendizajes

Agregar aqui lo que se descubra durante las fases.
