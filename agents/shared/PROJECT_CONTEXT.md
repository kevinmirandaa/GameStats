# Contexto compartido del proyecto

Todo agente lee esto primero, luego `docs/BLUEPRINT.md`, luego `agents/shared/CODE_STANDARDS.md`.

## Quien es el usuario

Kevin Miranda, trabaja solo. Esta aprendiendo Git, Docker, Go y Scala mientras construye el proyecto. Prefiere respuestas cortas y directas. Quiere entender lo que se hace, pero tiene poco tiempo: el proyecto se construye en pocas horas.

## Que es el proyecto

GameStats, proyecto del curso de Lenguajes de Programacion. Dos modulos independientes que se comunican por REST con JSON: Go ingesta y valida eventos de partidas, Scala los analiza de forma funcional. Los videojuegos no se implementan: se simulan. El enunciado original esta en `project/`.

## Requisitos duros del enunciado

- Go: recibir, validar, administrar flujo, mantener estado por partida, concurrencia con goroutines y channels, enviar a Scala.
- Scala: colecciones y operaciones funcionales, estadisticas, reglas de secuencia, resultados en JSON.
- Minimo: estadistica por jugador, por partida, una que combine tipos de evento, una regla por secuencia, y consulta de resultados.
- Simulador con varias partidas, varios jugadores, juegos en paralelo, casos normales y especiales.
- Git con historial, build reproducible, Docker, ejecucion integrada, CI/CD, deployment.
- Entregables: codigo, especificacion de eventos, justificacion de juegos, descripcion de estadisticas y reglas, Dockerfiles, CI/CD, documentacion de instalacion, ejecucion y despliegue, demo.
- El sistema debe ser generico: un conjunto nuevo de eventos entra sin rehacer el sistema.

## Reglas de trabajo con agentes

- Un agente solo modifica las carpetas que le pertenecen (ver su `AGENT.md`).
- Si necesita cambiar un contrato, lo propone en su reporte y no lo modifica.
- Al terminar escribe `reports/NN-nombre.md` y muestra el resumen en pantalla.
- Registra decisiones nuevas en `docs/DECISIONS.md` y aprendizajes utiles en su `MEMORY.md`.
- Piensa siempre en juegos futuros: nada en Go ni en el nucleo de Scala puede depender de un juego concreto.

## Criterio de decision

Kevin delega las decisiones tecnicas. Ante cualquier duda se elige la opcion mas simple que cumpla el enunciado y que funcione de verdad. No se agrega nada que el enunciado no pida. Toda decision se registra en `docs/DECISIONS.md` con una linea de razon. Si algo parece necesitar complejidad extra, se reporta en lugar de implementarlo.
