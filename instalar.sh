#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker no esta instalado. Instala Docker Desktop (https://www.docker.com/products/docker-desktop) y vuelve a ejecutar este script."
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker esta instalado pero no esta en ejecucion. Abre Docker Desktop, espera a que inicie y vuelve a ejecutar este script."
  exit 1
fi

echo "Construyendo y levantando GameConsole y GameStats (la primera vez tarda varios minutos)"
docker compose -f deploy/docker-compose.yml up -d --build --wait

if [ "${1:-}" != "--sin-demo" ]; then
  echo "Jugando partidas de demostracion a traves de la consola"
  docker compose -f deploy/docker-compose.yml --profile demo run --rm demo
fi

echo
echo "GameConsole esta listo."
echo
echo "  Consola de juegos:           http://localhost:8100"
echo "  Panel de estadisticas:       http://localhost:8100/#panel"
echo "  Panel propio de GameStats:   http://localhost:8081"
echo
echo "Para detener:  ./detener.sh"
echo

if [ -z "${GAMESTATS_NO_BROWSER:-}" ]; then
  if command -v open >/dev/null 2>&1; then
    open "http://localhost:8100"
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "http://localhost:8100" >/dev/null 2>&1 || true
  fi
fi
