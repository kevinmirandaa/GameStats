@echo off
cd /d "%~dp0"

if "%1"=="borrar-datos" (
  docker compose -f deploy\docker-compose.yml --profile simulation --profile demo down --volumes --remove-orphans
  echo GameStats detenido y datos borrados
) else (
  docker compose -f deploy\docker-compose.yml --profile simulation --profile demo down --remove-orphans
  echo GameStats detenido. Los resultados guardados se conservan. Para borrarlos: detener.bat borrar-datos
)
