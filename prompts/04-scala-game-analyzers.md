# Fase 04 Scala analizadores de juego

Agente: scala-analytics

## Leer primero

`agents/scala-analytics/AGENT.md`, `agents/scala-analytics/MEMORY.md`, `agents/shared/CODE_STANDARDS.md`, `docs/BLUEPRINT.md`, `docs/STATS.md`, `docs/STATS_EXPECTED_RESULTS.md`, `contracts/games/`, `contracts/examples/games/`, `reports/03-scala-core.md`.

## Objetivo

Implementar los analizadores de racing y combat con todas las metricas y reglas definidas en `docs/STATS.md`.

## Entregables

1. `RacingAnalyzer` y `CombatAnalyzer`, cada uno en su archivo, registrados por gameId.
2. Todas las metricas de jugador, partida y juego definidas.
3. La metrica combinada de cada juego.
4. Las reglas de secuencia de cada juego, implementadas con foldLeft o sliding sobre eventos ordenados, devolviendo DetectedPattern con `details`.
5. Pruebas munit que cargan las partidas de `contracts/examples/games/` y comparan con `docs/STATS_EXPECTED_RESULTS.md`, incluyendo los casos donde las reglas no se disparan.
6. Actualizar el README del modulo con las metricas disponibles por juego.

## Criterios de aceptacion

- `sbt test` en verde y los resultados coinciden exactamente con lo esperado.
- El nucleo de la fase 03 no se modifica salvo el registro de los analizadores.
- Las funciones de metricas son puras y componibles.
- Sin comentarios, sin emojis, sin separadores decorativos.

## Fuera de alcance

Cambios al contrato, al modulo Go o al simulador.

## Reporte

Escribir `reports/04-scala-game-analyzers.md` con metricas implementadas, reglas, ejemplos de salida y pendientes. Mostrar el resumen en pantalla.
