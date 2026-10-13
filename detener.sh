#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

if [ "${1:-}" = "--borrar-datos" ]; then
  docker compose -f deploy/docker-compose.yml --profile simulation down --volumes --remove-orphans
  echo "GameStats detenido y datos borrados"
else
  docker compose -f deploy/docker-compose.yml --profile simulation down --remove-orphans
  echo "GameStats detenido. Los resultados guardados se conservan para la proxima vez. Para borrarlos: ./detener.sh --borrar-datos"
fi
