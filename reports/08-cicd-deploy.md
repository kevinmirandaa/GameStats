# Reporte fase 08 CI/CD y despliegue

## Resultado

Dos pipelines de GitHub Actions y un entorno de despliegue definido con imagenes publicadas.

## Archivos

- `.github/workflows/ci.yml`: Go (formato, vet, pruebas con carreras) para ingest-go y simulator, Scala (pruebas y empaquetado), estandares de codigo, e imagenes mas prueba de humo.
- `.github/workflows/cd.yml`: publica las tres imagenes en GHCR con etiquetas `<sha>` y `latest`, y despliega y verifica las imagenes publicadas.
- `deploy/docker-compose.published.yml`: el entorno definido, sin construir nada, con imagenes del registro.
- `qa/smoke.sh`: acepta `SMOKE_COMPOSE_FILE` y `SMOKE_BUILD` para correr contra el despliegue.
- `deploy/DEPLOY.md`: pipelines, como ver resultados, como levantar el sistema en cualquier maquina, volver a una version anterior y probar localmente.

## Verificado ejecutando

- Los dos workflows se interpretan correctamente como YAML (Ruby): disparadores, trabajos, pasos y dependencias (`needs`) son los esperados. Los archivos no tienen tabuladores.
- `docker compose config` valida el Compose publicado.
- Simulacion local del trabajo de despliegue: imagenes etiquetadas con el nombre del registro, `qa/smoke.sh` contra `docker-compose.published.yml` sin construir: 15 verificaciones en verde en 23 segundos.
- Los pasos de CI se ejecutaron por separado en fases anteriores: formato, vet y pruebas de Go, `sbt test` y `sbt stage`, `qa/check-standards.sh` y `make smoke`.

## No verificado

- **Los workflows no se han ejecutado en GitHub.** No se pueden ejecutar localmente. La primera ejecucion real ocurre al subir esta rama y abrir el Pull Request. Si falla algo propio de GitHub (versiones de acciones, permisos, nombres de imagen), se corrige con el registro de la ejecucion.
- La publicacion en GHCR y la descarga con `GITHUB_TOKEN` ocurriran la primera vez que se una algo a `main`.

## Pasos manuales para el usuario

Ninguno es obligatorio. Opcionales:

1. Comprobar en la pestana Actions que el CI del Pull Request termina en verde.
2. Despues del merge a `main`, comprobar que CD publica y despliega, y que aparecen los paquetes en la seccion Packages del repositorio.
3. Si se quieren descargar las imagenes sin iniciar sesion, cambiar la visibilidad de cada paquete a publica.

## Decisiones nuevas

D31 y D32 en `docs/DECISIONS.md`.

## Despues del QA

El QA independiente (`reports/qa-08-cicd-deploy.md`) aprobo la fase sin bloqueantes. Reviso con rigor las acciones, los permisos y los contextos de expresion de GitHub, y confirmo por ejecucion la simulacion local del despliegue.

Se corrigio el hallazgo importante: la prueba de humo apagaba el sistema antes de que los pasos de registros pudieran leer los contenedores, asi que habrian salido vacios. Ahora los pasos de humo usan `KEEP_UP=1`. Tambien se agregaron limites de tiempo a todos los trabajos, se evita publicar desde una rama distinta de main si se lanza el CD a mano, y se aclararon dos puntos de `deploy/DEPLOY.md`.

Quedan aceptados como menores: la ausencia de una direccion publica permanente (D31), el CI sin cache de capas de Docker y las acciones fijadas por etiqueta.
