# Memoria del Agente Go Ingesta

## Hechos

- Puerto 8080.
- Variables: PORT, ANALYTICS_URL, MATCH_IDLE_TIMEOUT_SECONDS, REJECTED_EVENTS_PATH.
- Payload hacia Scala: MatchBatch con status COMPLETED o INCOMPLETE.
- Los eventos de una partida pueden llegar desordenados: se ordenan al cerrar.

## Aprendizajes

- Fase 02 cerrada. Actor por partida, cierre con retiro bajo mutex y drenaje del channel, espera corta tras MATCH_FINISHED. Ver D16 a D19.
- Los timestamps salen en RFC 3339 con fracciones sin ceros finales.
- El Dockerfile esta pendiente de construir en la fase 06 (Docker Desktop estaba cerrado).
