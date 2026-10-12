# Memoria del Agente DevOps

## Hechos

- Kevin tiene Docker Desktop. Primero se prueba local.
- Kevin prefiere procesos pesados en la nube. Despliegue objetivo: el mismo compose en una maquina virtual gratuita o servicio equivalente, elegido en la fase 08.
- Puertos: Go 8080, Scala 8081.
- Kevin esta aprendiendo Docker: explicar cada concepto la primera vez.

## Aprendizajes

- Fase 06 cerrada. Compose con perfil `simulation`, Makefile y `qa/smoke.sh`. Ver D27 y D28.
- Imagenes: analytics 421 MB, ingest y simulator 21 MB. Arranque en unos 12 segundos.
- El bash de macOS es 3.2: evitar `case` dentro de `$(...)` y otras sintaxis modernas en scripts.
- Docker Desktop debe estar abierto para construir; comprobar con `docker info`.
- El stop_grace_period de ingest-go es 50 s porque entrega las partidas abiertas al apagarse.
- Fase 08 cerrada. CI en PR, CD en main: publica a GHCR y despliega las imagenes publicadas con `docker-compose.published.yml`. Ver D31 y D32.
- No hay secretos: se usa el GITHUB_TOKEN automatico. El nombre del registro se pone en minusculas dentro del workflow.
- Los workflows no se pueden ejecutar localmente; validar el YAML con Ruby (`ruby -ryaml`) y simular el despliegue con imagenes etiquetadas `ghcr.io/<usuario>/gamestats-*:local`.
