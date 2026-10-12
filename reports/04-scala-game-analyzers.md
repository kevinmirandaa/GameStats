# Reporte fase 04 Scala analizadores de juego

## Resultado

`RacingAnalyzer` y `CombatAnalyzer` implementados con todas las metricas y reglas de `docs/STATS.md`, registrados en `Main`. El nucleo de la fase 03 solo cambio en dos puntos: la duracion de la partida paso a `MatchDuration` para reutilizarla, y `Main` registra los dos analizadores.

## Verificado ejecutando

- `sbt test`: 72 pruebas en verde.
- Las 4 partidas de ejemplo se comparan completas contra sus `*.expected.json`: metricas de partida, metricas y cantidad de eventos de cada jugador (incluidas las metricas omitidas), y los patrones con nombre, jugador, eventos de inicio y fin y detalles.
- Casos limite de cada regla: rachas de 2 y de 3, tiempos iguales, partida interrumpida por una eliminacion, desventaja de 2 y de 3, partida sin ganador, partida incompleta, jugador que solo aparece como victima, division por cero en K/D.
- Agregados de jugador y de juego sobre las partidas reales, documentados en `docs/STATS_EXPECTED_RESULTS.md` (resuelve el pendiente de la fase 01).
- Extremo a extremo con los binarios reales: Go acepto 135 eventos de las 4 partidas, Scala detecto exactamente los 6 patrones esperados y ninguno en las dos partidas sin disparos.
- Sin `var`, `null`, `throw` ni comentarios. Los juegos solo se nombran dentro de `analysis/games/` y en el registro de `Main`.

## Estructura

```
analysis/games/
  MatchResult      ganador, ranking y evento final (solo si la partida esta COMPLETED)
  Statistics       media, desviacion estandar poblacional y mapas de metricas definidas
  Racing*          RacingAnalyzer, RacingMetrics, RacingPatterns
  Combat*          CombatAnalyzer, CombatMetrics, CombatPatterns
```

Las rachas se detectan con `foldLeft` sobre los eventos ordenados, sin estado mutable.

## Pendientes

- Docker de ambos modulos sin construir (Docker Desktop cerrado). Fase 06.
- Estrategia como tercer juego sigue siendo opcional.
