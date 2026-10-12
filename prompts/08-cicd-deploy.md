# Fase 08 CI/CD y despliegue

Agente: devops

## Leer primero

`agents/devops/AGENT.md`, `agents/devops/MEMORY.md`, `agents/shared/CODE_STANDARDS.md`, `docs/BLUEPRINT.md`, `deploy/`, `Makefile`, `reports/06-integration-docker.md`.

## Objetivo

Automatizar la verificacion y publicacion, y dejar una version funcional desplegada.

## Entregables

1. `.github/workflows/ci.yml`: en cada push y Pull Request, compilar y probar Go (con `-race`), compilar y probar Scala, construir las tres imagenes y ejecutar `qa/smoke.sh`.
2. `.github/workflows/publish.yml`: al fusionar en main, publicar las tres imagenes en GitHub Container Registry con etiquetas de version y de commit.
3. `.github/workflows/deploy.yml` y `deploy/DEPLOY.md`: comparar brevemente tres opciones de despliegue gratuitas o baratas (VM gratuita en una nube, servicio de contenedores, servidor propio), recomendar una segun la preferencia de Kevin de usar la nube, e implementar la recomendada con pasos manuales claros donde se requieran credenciales. Nunca guardar secretos en el repositorio.
4. Archivo de ejemplo de variables de entorno de produccion sin valores secretos.
5. Instrucciones para que Kevin configure los secretos en GitHub paso a paso.
6. Verificacion de que el sistema desplegado responde en `/health` y procesa el escenario de demostracion.

## Criterios de aceptacion

- El pipeline de CI pasa en verde en una rama de prueba.
- Las imagenes se publican y pueden descargarse.
- Hay una URL o direccion donde se puede consultar el sistema funcionando.
- Todo paso que requiera credenciales de Kevin queda listado y no se intenta realizar por el agente.

## Fuera de alcance

Nuevas funcionalidades.

## Reporte

Escribir `reports/08-cicd-deploy.md` con los pipelines, la opcion elegida, la direccion del despliegue y los pasos manuales pendientes. Mostrar el resumen en pantalla.
