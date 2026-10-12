# QA fase 08 CI/CD y despliegue

## Veredicto: APROBADO

Sin bloqueantes. Un hallazgo importante (registros de fallo vacios) y varios menores. Los workflows no se ejecutaron en GitHub: lo marcado "razonamiento" es revision experta, no ejecucion.

## Criterios

| Criterio | Resultado | Evidencia |
|---|---|---|
| YAML valido de ci.yml y cd.yml | OK, ejecutado | `ruby -ryaml`: ambos cargan; sin tabuladores |
| Compose publicado valido | OK, ejecutado | `config` con GAMESTATS_REGISTRY muestra ghcr.io/kevinmirandaa/gamestats-*:latest |
| Falla claro sin GAMESTATS_REGISTRY | OK, ejecutado | "required variable GAMESTATS_REGISTRY is missing a value: define GAMESTATS_REGISTRY" (config y up) |
| Simulacion local del despliegue | OK, ejecutado | 3 imagenes `:local`, smoke con compose publicado y SMOKE_BUILD=0: 15 verificaciones OK, 23,5 s |
| `make smoke` normal | OK, ejecutado | PRUEBA DE HUMO APROBADA |
| Limpieza tras repetir el ciclo | OK, ejecutado | tras cada smoke y `make down`: 0 contenedores, volumenes y redes gamestats |
| Comandos de DEPLOY.md | OK, ejecutado | up --wait, simulator run (24 partidas, 28 rechazados), down --volumes funcionan; login y rollback no ejecutables (razonamiento, correctos) |
| Sin secretos | OK, ejecutado | solo `secrets.GITHUB_TOKEN`; grep sin credenciales |
| Estandares | OK, ejecutado | sin comentarios, emojis ni separadores en workflows, compose, smoke, DEPLOY; `standards_scanner.py` rc=0 |
| Documentacion no promete lo inexistente | OK | D31/D32 existen; "Limites" admite que no hay URL permanente |
| Acciones y versiones | OK, razonamiento | checkout@v4, setup-go@v5, setup-java@v4, sbt/setup-sbt@v1, buildx@v3, login@v3, build-push@v6 existen |
| Contextos de expresiones | OK, razonamiento | `matrix` valido en `defaults.run.working-directory` y `with`; `env.*` valido en `with` de pasos posteriores; `github.sha` valido en tags |
| `${GITHUB_REPOSITORY_OWNER,,}` y GITHUB_ENV | OK, razonamiento | bash 5 en el runner; el valor llega a los pasos siguientes y a `with:` via `env.` |
| Permisos minimos | OK, razonamiento | global `contents: read`; `packages: write` solo en publish; `packages: read` en deploy |
| Descarga con GITHUB_TOKEN de paquete privado nuevo | OK, razonamiento | la etiqueta `org.opencontainers.image.source` enlaza el paquete al repo y hereda acceso al token del mismo repo; sin la etiqueta fallaria |
| setup-go sin go.sum | OK, razonamiento | `cache-dependency-path: .../go.mod` existe, asi que hashea; a lo sumo aviso de ruta de cache vacia |
| setup-java `cache: sbt` en subcarpeta | OK, razonamiento | los patrones son recursivos (`**/*.sbt`, `**/project/build.properties`) |
| sbt en ubuntu-latest | OK, razonamiento | no viene preinstalado; `sbt/setup-sbt@v1` tras setup-java es el orden correcto |
| make, curl, python3, docker compose, `--wait`, puertos 8080/8081 | OK, razonamiento | presentes y libres en el runner |
| smoke.sh sin dependencias de macOS | OK, razonamiento | date +%s, xargs -I{}, printf, python3 y bash 3.2/5 son portables |
| CD no corre en PR; deploy usa sha publicado | OK | triggers push main y workflow_dispatch; tags `${{ github.sha }}` en publish y `GAMESTATS_TAG: ${{ github.sha }}` en deploy |
| matrix.context / matrix.image | OK | include con tres pares; usados en name, context y tags |
| concurrency | OK | CI cancela intermedios; CD no cancela |

## Importantes

1. Los registros de contenedores de los pasos `if: failure()` saldran vacios. CONFIRMADO por ejecucion (tras cada smoke no queda ningun contenedor) y por lectura de `qa/smoke.sh`: el `trap cleanup EXIT` ejecuta `down --volumes` antes de que corra el paso de registros, en CI y en CD. Correccion (devops), en ambos pasos de humo:

```yaml
      - name: Construir imagenes, levantar el sistema y verificarlo
        env:
          KEEP_UP: "1"
        run: make smoke
```

y en cd.yml agregar `KEEP_UP: "1"` al bloque `env:` del paso de despliegue. El runner es desechable, no hay que limpiar.

## Menores

1. El prompt pedia `publish.yml`, `deploy.yml`, URL de acceso, ejemplo de variables de produccion y guia de secretos. Se reemplaza por `cd.yml` unico segun D31 (sin URL permanente). Aceptable y documentado; el criterio "direccion donde consultar" no se cumple. Solo documentacion.
2. DEPLOY.md paso 4: `curl localhost:8081/stats/matches` justo tras el simulador devolvio `[]` (CONFIRMADO). Indicar esperar unos segundos.
3. DEPLOY.md paso 1 dice descargar solo el compose, pero los comandos usan `deploy/docker-compose.published.yml`. Aclarar la ruta.
4. Falta `timeout-minutes` en los trabajos (razonamiento). Sugerido: 15 en Go y estandares, 30 en Scala, contenedores y deploy.
5. `workflow_dispatch` desde una rama distinta de main publicaria `latest` desde esa rama (razonamiento). Agregar `if: github.ref == 'refs/heads/main'` al trabajo publish.
6. El CI de contenedores no usa cache de capas: la imagen Scala descarga sbt y dependencias en cada ejecucion (lento, no fallo).
7. Acciones fijadas por etiqueta y no por SHA (aceptable para el alcance).
8. DEPLOY.md fija `kevinmirandaa` y el pipeline usa el propietario del repositorio; coinciden con el remoto actual.
9. CD publica en push a main sin esperar al CI de ese mismo commit; mitigado porque el PR ya paso CI.

## Estado final

`make down` ejecutado al inicio y al final; sin contenedores, volumenes ni redes gamestats. Quedan las imagenes `:local` en Docker.
