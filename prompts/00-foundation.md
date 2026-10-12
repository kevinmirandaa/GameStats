# Fase 00 Fundacion

Agente: architect

## Leer primero

`agents/architect/AGENT.md`, `agents/architect/MEMORY.md`, `agents/shared/PROJECT_CONTEXT.md`, `docs/BLUEPRINT.md`, `agents/shared/CODE_STANDARDS.md`, `contracts/event.schema.json`.

## Objetivo

Dejar el repositorio listo para que los demas agentes trabajen sin pisarse.

## Entregables

1. Estructura de carpetas segun la seccion 11 del Blueprint, con un `README.md` corto en cada modulo vacio (`ingest-go`, `analytics-scala`, `simulator`, `deploy`, `qa`, `reports`).
2. `.gitignore` para Go, Scala/sbt, IDEs (GoLand, VS Code, Antigravity) y archivos del sistema.
3. `README.md` raiz con descripcion, diagrama y enlaces.
4. `docs/DECISIONS.md` con las decisiones ya tomadas (REST contra broker, JSON, sobre comun, juegos iniciales, extensibilidad por analizadores) y su razon.
5. `docs/ADDING_A_GAME.md` con los pasos para agregar un juego nuevo, incluido uno externo en otro lenguaje.
6. `docs/GIT_GUIDE.md` para Kevin: que hace cada comando que usara (`init`, `status`, `add`, `commit`, `branch`, `switch`, `push`, `pull request`, `merge`) con un ejemplo de este proyecto. Corto y sin relleno.
7. `contracts/examples/valid/` con al menos 8 eventos validos y `contracts/examples/invalid/` con al menos 8 invalidos (cada uno con un nombre que indique el motivo). Incluir tambien eventos de un tercer juego ficticio para probar la genericidad.
8. Revisar `contracts/event.schema.json` contra todos los ejemplos y corregirlo si hace falta.

## Criterios de aceptacion

- Todos los ejemplos validos pasan el esquema y los invalidos fallan.
- Ningun archivo contiene emojis ni lineas decorativas.
- Los documentos son consistentes con el Blueprint.

## Fuera de alcance

Codigo de aplicacion. No ejecutar comandos Git: Kevin los ejecuta.

## Reporte

Escribir `reports/00-foundation.md` con lo creado, decisiones nuevas y riesgos. Mostrar el resumen en pantalla.
