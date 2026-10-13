#!/usr/bin/env bash
set -euo pipefail

source_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
output_docx="${1:-$source_directory/GameStats_Documentacion.docx}"
professor_name="${PROFESOR:-(nombre del profesor)}"
work_directory="$(mktemp -d)"

if [ ! -d "$source_directory/tools/docx/node_modules/docx" ]; then
  (cd "$source_directory/tools/docx" && npm install --no-audit --no-fund >/dev/null)
fi

python3 "$source_directory/tools/docx/html-a-bloques.py" "$source_directory/documentacion.html" "$work_directory/bloques.json" >/dev/null
node "$source_directory/tools/docx/build-docx.js" "$work_directory/bloques.json" "$source_directory" "$output_docx" "$professor_name" 2>/dev/null
rm -rf "$work_directory"
