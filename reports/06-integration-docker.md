# Reporte fase 06 Integracion con Docker Compose

## Resultado

El sistema completo corre con un solo comando en Docker. Se construyeron y probaron las tres imagenes, que quedaron pendientes en las fases 02, 03 y 05.

## Archivos

- `deploy/docker-compose.yml`: Scala, Go y simulador (perfil `simulation`), red interna, healthchecks, orden de arranque, volumen para rechazados.
- `Makefile`: `build`, `test`, `up`, `down`, `simulate`, `smoke`, `logs`.
- `qa/smoke.sh`: prueba de humo de extremo a extremo.
- `deploy/README.md`: explica en lenguaje simple imagen, contenedor, Dockerfile, Compose, healthcheck y volumen, con los comandos exactos.
- `deploy/.env.example`.

## Verificado ejecutando

- `docker compose config`, y `make build` construye las tres imagenes sin cambios en los Dockerfile (1 minuto con descargas).
- Tamanos: analytics 421 MB, ingest 21.4 MB, simulator 21.2 MB. Los tres corren con usuario sin privilegios.
- `make up`: ambos servicios sanos en unos 12 segundos con imagenes ya construidas.
- `make smoke` pasa con 15 verificaciones en unos 25 segundos: 12 partidas de racing y 12 de combat analizadas y COMPLETED, 35 patrones detectados con los cuatro tipos presentes, agregados de juego y de jugador, 28 eventos rechazados (duplicados e invalidos) y 0 fallos de entrega, 0 partidas activas, y las 4 partidas del escenario `unfinished-match` llegando como INCOMPLETE (esto era lo no verificado de la fase 05).
- Cierre ordenado: `docker compose stop ingest-go` con una partida activa la entrega a Scala como INCOMPLETE antes de terminar.
- La primera version del script de humo fallo por una sintaxis de `case` incompatible con el bash 3.2 de macOS dentro de `$(...)`. Se reemplazo por una funcion auxiliar y quedo en verde.

## No verificado

- `make test` completo (Go y Scala) no se ejecuto en esta fase: se ejecuto por separado en cada fase anterior.
- El script de humo no se probo en Linux. Usa `bash`, `curl` y `python3`, presentes en el entorno de GitHub Actions.

## Decisiones nuevas

D27 y D28 en `docs/DECISIONS.md`.

## Para las fases siguientes

- Fase 07: la prueba de humo es la base del QA integral. Falta probar Scala caido, muchas partidas en paralelo y un juego ficticio.
- Fase 08: el pipeline puede ejecutar `make smoke` tal cual. Las imagenes ya tienen nombre `gamestats-*:local`; para publicar se etiquetaran con el registro.

## Despues del QA

El QA independiente (`reports/qa-06-integration-docker.md`) aprobo la fase, sin bloqueantes. Comprobo que el script de humo falla de verdad con umbrales alterados, que los contenedores no corren como root y que `make down` no deja restos.

Hallazgo de robustez: con Scala caido, Go reintenta unos 3 segundos y despues pierde la partida. Se amplio la ventana en Compose a unos 15 segundos (D29) y los limites quedaron documentados en `deploy/README.md`. Quedan aceptados como menores: lineas largas y numeros repetidos en `qa/smoke.sh`, y los puertos publicados en todas las interfaces.
