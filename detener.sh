#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"

if [ "${1:-}" = "--borrar-datos" ]; then
  docker compose -f deploy/docker-compose.yml --profile simulation --profile demo down --volumes --remove-orphans
  echo "GameConsole detenido y datos borrados"
else
  docker compose -f deploy/docker-compose.yml --profile simulation --profile demo down --remove-orphans
  echo "GameConsole detenido. Los resultados guardados se conservan para la proxima vez. Para borrarlos: ./detener.sh --borrar-datos"
fi
