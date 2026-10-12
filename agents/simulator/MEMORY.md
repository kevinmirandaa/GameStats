# Memoria del Agente Simulador

## Hechos

- El simulador es un cliente HTTP externo, no parte de Go ni de Scala.
- Debe poder demostrar volumen: decenas de partidas, cientos de eventos por partida en modo rapido.
- Debe incluir casos para que cada regla de `docs/STATS.md` se dispare y otros donde no.

## Aprendizajes

- Fase 05 cerrada. Historias Natural, Comeback, Streak y Quiet; entrega aplicada despues de generar. Ver D23 a D26.
- Las pruebas usan calculos de reglas propios en `internal/testsupport`, independientes de Scala.
- Los empates de tiempo acumulado se desempatan por orden de parrilla.
- Para repetir una corrida contra el mismo servidor hay que cambiar `-run-id`.
- El Dockerfile esta pendiente de construir en la fase 06 (Docker Desktop estaba cerrado).
- Para reproducir una corrida exacta hay que fijar `-seed`, `-run-id` y `-start-time`. La interfaz se llama `GameMatchGenerator`.
- En macOS `sed` no soporta `\\b`; usar perl para renombres con limites de palabra, y `find | xargs` en lugar de `grep --include` sin comillas en zsh.
