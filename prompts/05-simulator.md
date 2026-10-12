# Fase 05 Simulador

Agente: simulator

## Leer primero

`agents/simulator/AGENT.md`, `agents/simulator/MEMORY.md`, `agents/shared/CODE_STANDARDS.md`, `docs/BLUEPRINT.md`, `docs/STATS.md`, `contracts/games/`, `contracts/examples/`.

## Objetivo

Construir la fuente externa simulada que alimenta a GameStats.

## Entregables

1. Modulo Go independiente en `simulator/` con interfaz `GameMatchGenerator` y dos implementaciones, racing y combat, una por archivo.
2. Escenarios nombrados: `normal`, `comeback`, `killstreak`, `out-of-order`, `duplicates`, `invalid-events`, `disconnect`, `unfinished-match`, `bulk`, `mixed-parallel`.
3. Ejecucion en paralelo de varias partidas de ambos juegos con goroutines.
4. Modo tiempo real simulado con ritmo configurable y modo volcado rapido.
5. Semilla configurable para repetir resultados exactos.
6. Parametros por banderas y variables de entorno: URL de Go, partidas por juego, jugadores por partida, semilla, ritmo, escenario.
7. Pruebas de los generadores: respetan el esquema, las reglas se disparan en los escenarios que corresponden y no en los otros, la semilla reproduce.
8. Dockerfile multi etapa y README corto con ejemplos de uso.

## Criterios de aceptacion

- `go vet`, `gofmt` y `go test -race` en verde.
- Todos los eventos de los escenarios validos pasan `contracts/event.schema.json`.
- Agregar un tercer juego es agregar un archivo y registrarlo.
- No importa codigo de `ingest-go`.
- Sin comentarios, sin emojis, sin separadores decorativos.

## Fuera de alcance

Docker Compose.

## Reporte

Escribir `reports/05-simulator.md` con escenarios, comandos de ejemplo, volumen generado por escenario y pendientes. Mostrar el resumen en pantalla.
