# Fase 01 Diseno de estadisticas

Agente: stats-designer

## Leer primero

`agents/stats-designer/AGENT.md`, `agents/stats-designer/MEMORY.md`, `agents/shared/PROJECT_CONTEXT.md`, `docs/BLUEPRINT.md`, `contracts/event.schema.json`, `project/Proyecto1 - GameStats_Enunciado.docx.pdf`.

## Objetivo

Definir con precision los dos juegos, sus eventos, estadisticas y reglas, de modo que Scala, el simulador y las pruebas puedan implementarse sin ambiguedad.

## Entregables

1. `docs/STATS.md` con, para cada juego (racing y combat):
   - Descripcion corta y justificacion de la eleccion.
   - Tabla de acciones: nombre, quien es `playerId`, campos de `data` con tipo y unidad.
   - Tabla de metricas por jugador, por partida y por juego: nombre, formula, eventos usados.
   - Al menos una metrica que combine tipos de evento distintos por juego.
   - Al menos dos reglas de secuencia por juego, con definicion exacta y formal (condicion de inicio, condicion de fin, que se reporta en `details`).
   - Secuencia tipica de una partida completa.
2. `contracts/games/racing.md` y `contracts/games/combat.md`: version autocontenida para quien implemente un juego real.
3. `contracts/examples/games/racing/` y `contracts/examples/games/combat/` con partidas completas pequenas en JSON que sirvan de datos de prueba: una donde cada regla se dispara y una donde no.
4. `docs/STATS_EXPECTED_RESULTS.md`: para cada partida de ejemplo, el MatchAnalysis esperado calculado a mano. Esto sera la referencia de las pruebas de Scala.
5. Una seccion final en `docs/STATS.md` con estadisticas del tercer dominio opcional (estrategia), solo como propuesta de una pagina, sin escenarios.

## Criterios de aceptacion

- Cada metrica es calculable solo con los eventos documentados.
- Cada regla tiene al menos un caso positivo y uno negativo en los ejemplos.
- Los resultados esperados fueron verificados dos veces.
- Todos los eventos de ejemplo pasan `contracts/event.schema.json`.
- Existe al menos una metrica por jugador, una por partida, una combinada y una regla de secuencia, como exige el enunciado.

## Fuera de alcance

Codigo. Cambios al sobre del evento (solo se proponen en el reporte).

## Reporte

Escribir `reports/01-stats-design.md` con el resumen de metricas y reglas y cualquier propuesta de cambio al contrato. Mostrar el resumen en pantalla.
