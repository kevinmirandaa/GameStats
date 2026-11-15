@echo off
cd /d "%~dp0"

where docker >nul 2>nul
if errorlevel 1 (
  echo Docker no esta instalado. Instala Docker Desktop y vuelve a ejecutar este archivo.
  exit /b 1
)

docker info >nul 2>nul
if errorlevel 1 (
  echo Docker esta instalado pero no esta en ejecucion. Abre Docker Desktop, espera a que inicie y vuelve a ejecutar este archivo.
  exit /b 1
)

echo Construyendo y levantando GameConsole y GameStats (la primera vez tarda varios minutos)
docker compose -f deploy\docker-compose.yml up -d --build --wait
if errorlevel 1 exit /b 1

set "skipdemo="
if "%1"=="sindemo" set "skipdemo=1"
if "%1"=="--sin-demo" set "skipdemo=1"
if not defined skipdemo (
  echo Jugando partidas de demostracion a traves de la consola
  docker compose -f deploy\docker-compose.yml --profile demo run --rm demo
)

echo.
echo GameConsole esta listo.
echo.
echo   Consola de juegos:           http://localhost:8100
echo   Panel de estadisticas:       http://localhost:8100/#panel
echo   Panel propio de GameStats:   http://localhost:8081
echo.
echo Para detener: detener.bat
echo.

start http://localhost:8100
