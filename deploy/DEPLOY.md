# Despliegue y pipeline

## Entorno definido

El entorno de GameStats es **Docker Compose con imagenes publicadas en GitHub Container Registry (GHCR)**. Los tres servicios (analytics-scala, ingest-go y simulator) se descargan como imagenes ya construidas y se levantan juntos con `deploy/docker-compose.published.yml`.

El pipeline despliega ese entorno de forma automatica en cada cambio unido a `main` y comprueba que funciona. El mismo archivo sirve para levantar el sistema en cualquier maquina con Docker.

## Pipelines

Estan en `.github/workflows/`.

### CI (`ci.yml`): en cada Pull Request y en cada cambio a main

| Trabajo | Que hace |
|---|---|
| Go ingest-go y simulator | Revisa el formato, ejecuta `go vet` y `go test -race` |
| Scala analytics-scala | Compila, ejecuta las pruebas de munit y empaqueta |
| Estandares de codigo | Ejecuta `qa/check-standards.sh` (comentarios, emojis, `var`, `null`, errores ignorados) |
| Contenedores y prueba de humo | Construye las tres imagenes con Docker, levanta el sistema con Compose y ejecuta `qa/smoke.sh` |

El ultimo trabajo solo corre si pasan los de Go y Scala.

### CD (`cd.yml`): al unir un cambio a main

1. **Publicar**: construye las tres imagenes y las sube a `ghcr.io/<usuario>/gamestats-ingest`, `gamestats-analytics` y `gamestats-simulator`, cada una con dos etiquetas: el identificador completo del commit (`<sha>`) y `latest`.
2. **Desplegar y verificar** (solo si el trabajo anterior termina bien): descarga las imagenes publicadas con la etiqueta del commit, levanta el sistema con `docker-compose.published.yml` y ejecuta `qa/smoke.sh` contra ese despliegue. Si la prueba falla, el trabajo falla y los registros de los contenedores quedan en la ejecucion.

Los trabajos usan el `GITHUB_TOKEN` que GitHub genera solo en cada ejecucion. **No hay secretos que configurar** y no se guarda ninguna credencial en el repositorio.

## Donde ver los resultados

En GitHub, pestana **Actions** del repositorio. Cada Pull Request muestra tambien el resultado de CI en su pagina. Las imagenes publicadas aparecen en la pagina principal del repositorio, seccion **Packages**.

## Levantar el sistema en una maquina cualquiera

Requisitos: Docker con Compose.

1. Clonar el repositorio (o descargar solo `deploy/docker-compose.published.yml` y usar esa ruta en los comandos siguientes).
2. Si las imagenes son privadas, iniciar sesion en el registro con un token personal de GitHub con permiso `read:packages`:

```bash
echo "$TOKEN" | docker login ghcr.io -u kevinmirandaa --password-stdin
```

Si las imagenes se hacen publicas (en la pagina de cada paquete, **Package settings**, **Change visibility**), no hace falta iniciar sesion.

3. Levantar:

```bash
export GAMESTATS_REGISTRY=ghcr.io/kevinmirandaa
export GAMESTATS_TAG=latest
docker compose -f deploy/docker-compose.published.yml up -d --wait
```

4. Generar datos y consultar:

```bash
docker compose -f deploy/docker-compose.published.yml --profile simulation run --rm simulator
sleep 10
curl localhost:8081/stats/matches
```

Go espera unos segundos tras el fin de cada partida antes de enviarla a Scala, por eso la consulta inmediata puede devolver una lista vacia.

5. Detener:

```bash
docker compose -f deploy/docker-compose.published.yml --profile simulation down --volumes
```

## Volver a una version anterior

Cada commit de `main` queda publicado con su identificador. Para desplegar una version anterior, usar su identificador como etiqueta:

```bash
export GAMESTATS_TAG=<identificador completo del commit>
```

## Probar el despliegue sin salir de tu maquina

Construye las imagenes con los nombres del registro y ejecuta la prueba de humo contra el Compose publicado:

```bash
docker build -t ghcr.io/kevinmirandaa/gamestats-ingest:local ingest-go
docker build -t ghcr.io/kevinmirandaa/gamestats-analytics:local analytics-scala
docker build -t ghcr.io/kevinmirandaa/gamestats-simulator:local simulator
GAMESTATS_REGISTRY=ghcr.io/kevinmirandaa GAMESTATS_TAG=local \
  SMOKE_COMPOSE_FILE=deploy/docker-compose.published.yml SMOKE_BUILD=0 ./qa/smoke.sh
```

## Limites

- El despliegue automatico se ejecuta en un entorno temporal de GitHub Actions: demuestra que las imagenes publicadas funcionan, pero no deja una direccion publica permanente.
- Las acciones de los pipelines estan fijadas por etiqueta de version, no por identificador de commit.
- Las imagenes se publican como privadas por defecto la primera vez. El pipeline puede descargarlas igual con su token.
- Los demas limites del sistema estan en `deploy/README.md`.
