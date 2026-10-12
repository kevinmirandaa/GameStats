# Prompts por fase

Cada archivo es una orden completa para un agente. Se ejecuta desde la raiz del repositorio con Claude Code (en Antigravity o en la terminal de GoLand).

## Comando de ejecucion

```bash
claude "Actua como el agente de agents/NOMBRE y ejecuta prompts/ARCHIVO.md"
```

## Orden de fases

| Fase | Agente | Prompt |
|---|---|---|
| 00 | architect | prompts/00-foundation.md |
| 01 | stats-designer | prompts/01-stats-design.md |
| 02 | go-ingest | prompts/02-go-ingest.md |
| 03 | scala-analytics | prompts/03-scala-core.md |
| 04 | scala-analytics | prompts/04-scala-game-analyzers.md |
| 05 | simulator | prompts/05-simulator.md |
| 06 | devops | prompts/06-integration-docker.md |
| 07 | qa | prompts/07-qa-full.md |
| 08 | devops | prompts/08-cicd-deploy.md |
| 09 | documenter | prompts/09-docs-demo.md |

La compuerta de calidad antes de cada merge es `prompts/qa-gate.md`:

```bash
claude "Actua como el agente de agents/qa y ejecuta prompts/qa-gate.md para la fase NN"
```

## Ciclo de cada fase

1. Crear la rama `feature/fase-NN-nombre`.
2. Ejecutar el comando del agente.
3. Pasar el resumen del agente (queda en `reports/NN-nombre.md`) a Claude de planificacion.
4. Ejecutar la compuerta QA.
5. Con veredicto APROBADO, abrir Pull Request y hacer merge.
6. Si es RECHAZADO, se corrige con el mismo agente usando la lista del reporte QA.

## Fases que pueden hacerse en paralelo

Despues de la fase 01, las fases 02 y 03 son independientes porque comparten solo el contrato. La fase 05 puede empezar cuando 01 termina.

## Estructura comun de cada prompt

Agente, contexto a leer, objetivo, entregables, criterios de aceptacion, fuera de alcance y formato del reporte.
