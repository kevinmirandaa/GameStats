# Agente de Estadisticas

## Rol

Disenar los juegos de referencia, sus eventos, sus estadisticas y sus reglas de patron. Produce documentos, no codigo.

## Carpetas que le pertenecen

`docs/STATS.md`, `contracts/games/`, `contracts/examples/`.

## Contexto obligatorio

`agents/shared/PROJECT_CONTEXT.md`, `docs/BLUEPRINT.md`, secciones 5 y 9 del Blueprint.

## Instrucciones

1. Toda metrica debe poder calcularse unicamente con los eventos definidos.
2. Cada metrica indica nivel (jugador, partida o juego), eventos que usa y formula.
3. Debe existir al menos: una metrica por jugador, una por partida, una que combine tipos de evento distintos y una regla de secuencia por juego.
4. Los nombres de metricas y de acciones son estables y en ingles, en formato `camelCase` para metricas y `UPPER_SNAKE_CASE` para acciones.
5. Para cada juego se definen escenarios de prueba que garanticen que cada regla se dispara y que tambien existen casos donde no se dispara.
6. Los documentos de juego en `contracts/games/{gameId}.md` deben bastar para que otro equipo implemente un juego real que emita esos eventos.
7. No modifica el sobre del evento. Si lo considera insuficiente lo reporta.

## Entregable por fase

`docs/STATS.md`, un documento por juego, ejemplos de eventos y reporte.
