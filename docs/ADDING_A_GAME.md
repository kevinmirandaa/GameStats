# Como agregar un juego nuevo

GameStats no sabe como esta hecho un juego. Un juego solo necesita enviar eventos con el contrato. Las estadisticas propias del juego requieren un analizador pequeño en Scala.

## Nivel 1: conectar el juego (sin cambiar GameStats)

1. Elegir un `gameId` unico en minusculas, por ejemplo `chess`.
2. Emitir los eventos del ciclo de vida: MATCH_STARTED, PLAYER_JOINED, GAME_ACTION, PLAYER_LEFT y MATCH_FINISHED.
3. Cada evento cumple `contracts/event.schema.json`. Puede comprobarse con los ejemplos de `contracts/examples/`.
4. Enviar los eventos con `POST` a `/events` o `/events/batch` de `ingest-go`.
5. Los datos propios del juego van en `data`. Las acciones propias van en `action`.

Resultado: las partidas se agrupan, se ordenan y reciben analisis generico (conteos por tipo y accion, duracion, eventos por jugador).

El juego puede estar escrito en cualquier lenguaje, incluido Prolog: solo debe producir JSON y hacer una peticion HTTP.

## Nivel 2: estadisticas propias del juego

1. Documentar el juego en `contracts/games/{gameId}.md`: acciones, campos de `data` con tipo y unidad, y secuencia tipica de una partida.
2. Definir las metricas (por jugador, partida y juego) y las reglas de secuencia, con formula exacta.
3. Crear un archivo `{GameId}Analyzer.scala` en `analytics-scala` que implemente `GameAnalyzer`.
4. Registrarlo en el registro de analizadores con su `gameId`.
5. Escribir pruebas con partidas de ejemplo en `contracts/examples/games/{gameId}/` y resultados esperados calculados a mano.
6. Agregar un generador en el simulador si se quiere producir datos de prueba.

No se modifica `ingest-go` ni el nucleo de Scala.

## Reglas para no romper el sistema

- No cambiar el sobre del evento. Si falta algo, proponer una nueva `schemaVersion`.
- Los nombres de accion usan `UPPER_SNAKE_CASE`. Los de metrica, `camelCase`.
- `playerId` y `matchId` deben ser unicos globalmente.
- Un `action` desconocido nunca debe provocar un error: se acepta y se cuenta.
