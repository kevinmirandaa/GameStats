# Fase 03 Scala nucleo y API

Agente: scala-analytics

## Leer primero

`agents/scala-analytics/AGENT.md`, `agents/scala-analytics/MEMORY.md`, `agents/scala-analytics/SKILLS.md`, `agents/shared/PROJECT_CONTEXT.md`, `docs/BLUEPRINT.md`, `agents/shared/CODE_STANDARDS.md`, `docs/STATS.md`.

## Objetivo

Construir el nucleo generico de `analytics-scala`: dominio, decodificacion, analisis generico, registro de analizadores, almacenamiento y API. Los analizadores de racing y combat se hacen en la fase 04.

## Entregables

1. Proyecto sbt con Scala 3, Cask, upickle y munit.
2. Dominio inmutable: Event, EventType, MatchBatch, MatchAnalysis, PlayerAnalysis, DetectedPattern.
3. Decodificacion de MatchBatch con errores explicitos (`Either`).
4. Analisis generico: conteos por tipo y accion, duracion de la partida, cantidad de eventos por jugador, jugadores participantes, resultado desde MATCH_FINISHED.
5. Trait `GameAnalyzer` y `GameAnalyzerRegistry`. Un analizador de ejemplo de prueba para demostrar el mecanismo sin depender de racing ni combat.
6. Almacenamiento en memoria detras de un trait, seguro para uso concurrente.
7. Todos los endpoints de la seccion 6 del Blueprint. Los agregados de jugador y juego se calculan con fold sobre los analisis guardados.
8. Pruebas munit del decodificador, del analisis generico, del registro y de la API.
9. Dockerfile multi etapa y README corto del modulo.

## Decisiones tomadas en la fase 01

- `GameAnalyzer` incluye una funcion opcional `additionalPlayerIds(events)` con valor por defecto vacio, para que un juego declare participantes que no emiten eventos propios. Combat la usa para las victimas.
- `DetectedPattern.details` es `Map[String, Double]`.
- Los agregados entre partidas son genericos: prefijos `mean_`, `min_`, `max_` sobre las metricas del analisis, sin conocer ningun juego.

## Pendiente de la fase 01

- Calcular y guardar los resultados esperados de `/stats/players/{playerId}` y `/stats/games/{gameId}` a partir de las cuatro partidas de `contracts/examples/games/`, en `docs/STATS_EXPECTED_RESULTS.md`, antes de implementar los agregados. Verificarlos con una segunda implementacion independiente.

## Criterios de aceptacion

- `sbt test` en verde.
- Un juego desconocido produce analisis generico sin error.
- No hay `var`, `null` ni excepciones para control de flujo.
- Ninguna referencia a racing ni combat en el nucleo.
- Sin comentarios, sin emojis, sin separadores decorativos.
- Se puede enviar un MatchBatch con curl y consultar el resultado.

## Fuera de alcance

Metricas y reglas especificas de juegos. Docker Compose.

## Reporte

Escribir `reports/03-scala-core.md` con el diseno del dominio, como agregar un analizador, comandos para probar y pendientes. Mostrar el resumen en pantalla.
