# Memoria del Agente de Estadisticas

## Punto de partida sugerido

Racing: acciones LAP_COMPLETED, POSITION_CHANGED, PENALTY_APPLIED, PIT_STOP. Metricas como mejor vuelta, promedio, consistencia, evolucion de posicion. Reglas como remontada.

Combat: acciones ELIMINATION, DAMAGE_DEALT, SHOT_FIRED. Metricas como eliminaciones, derrotas, K/D, dano promedio, precision. Reglas como racha de eliminaciones y remontada.

El agente puede ajustar estos nombres si lo justifica, y debe dejar el resultado final escrito en `docs/STATS.md`.

## Aprendizajes

- Fase 01 cerrada. Definiciones finales en `docs/STATS.md`. Acciones y metricas finales: racing (LAP_COMPLETED, POSITION_CHANGED, PENALTY_APPLIED, PIT_STOP) y combat (ELIMINATION, DAMAGE_DEALT, SHOT_FIRED).
- Reglas finales: RACE_COMEBACK, IMPROVING_LAP_STREAK, KILL_STREAK, SCORE_COMEBACK. Umbrales: 4 posiciones, 3 vueltas, 3 eliminaciones, desventaja de 3.
- Metodo que funciono: generar las partidas con un script, calcular lo esperado con una implementacion de referencia y verificarlo con una segunda en otro lenguaje.
- Las posiciones de racing deben ser coherentes con los tiempos acumulados; se comprobo explicitamente.
- Los agregados entre partidas son genericos (`mean_`, `min_`, `max_`) para que el nucleo no conozca juegos.
