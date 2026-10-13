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

echo Construyendo y levantando GameStats (la primera vez tarda unos minutos)
docker compose -f deploy\docker-compose.yml up -d --build --wait
if errorlevel 1 exit /b 1

if not "%1"=="sindemo" (
  echo Generando datos de demostracion
  docker compose -f deploy\docker-compose.yml --profile simulation run --rm simulator
  timeout /t 6 /nobreak >nul
)

echo.
echo    GameStats esta listo. Abre el panel de resultados aqui:
echo.
echo                  http://localhost:8081
echo.
echo Para detener: detener.bat
echo.

start http://localhost:8081
