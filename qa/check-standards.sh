#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "Revisando estandares de codigo en $repository_root"
if python3 -I "$repository_root/qa/standards_scanner.py" "$repository_root"; then
  echo "ESTANDARES APROBADOS"
else
  echo "ESTANDARES CON HALLAZGOS"
  exit 1
fi
