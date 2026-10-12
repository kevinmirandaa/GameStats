# Fase 06 Integracion con Docker Compose

Agente: devops

## Leer primero

`agents/devops/AGENT.md`, `agents/devops/MEMORY.md`, `agents/shared/CODE_STANDARDS.md`, `docs/BLUEPRINT.md`, los `Dockerfile` y `README` de `ingest-go`, `analytics-scala` y `simulator`.

## Objetivo

Levantar todo el sistema con un solo comando y demostrar el flujo completo.

## Entregables

1. `deploy/docker-compose.yml` con simulator, ingest-go y analytics-scala, red interna, healthchecks y orden de arranque correcto.
2. Perfiles para que el simulador corra bajo demanda con el escenario elegido.
3. `Makefile` con `build`, `test`, `up`, `down`, `smoke`, `logs`.
4. `qa/smoke.sh`: levanta el sistema, ejecuta el escenario `mixed-parallel`, espera, y verifica por curl que existen analisis de racing y combat y que al menos un patron fue detectado. Sale con codigo distinto de cero si algo falla.
5. Ajustes minimos a los Dockerfiles si hay problemas, avisando en el reporte.
6. `deploy/README.md` que explica en lenguaje simple que es una imagen, un contenedor y compose, con los comandos exactos para Docker Desktop.

## Criterios de aceptacion

- `make up` deja los tres servicios sanos.
- `make smoke` pasa en una maquina limpia.
- Los puertos y variables coinciden con el Blueprint.
- Sin comentarios en archivos de codigo y scripts, sin emojis, sin separadores decorativos.

## Fuera de alcance

CI/CD y nube.

## Reporte

Escribir `reports/06-integration-docker.md` con los comandos, tiempos de arranque, resultado del smoke test y problemas encontrados. Mostrar el resumen en pantalla.
