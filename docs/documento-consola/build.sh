#!/usr/bin/env bash
set -euo pipefail

source_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
tools_directory="$source_directory/herramientas"
output_directory="${1:-$source_directory}"
work_directory="$(mktemp -d)"

mkdir -p "$output_directory"

if [ ! -d "$tools_directory/node_modules/docx" ]; then
  (cd "$tools_directory" && npm install --no-audit --no-fund >/dev/null)
fi

python3 "$tools_directory/diagrama.py"
node "$tools_directory/construir.js" "$source_directory/.documento-temporal.html" "$output_directory/GameConsole_Documentacion.docx" 2>/dev/null
swift "$tools_directory/html-a-pdf.swift" "$source_directory/.documento-temporal.html" "$work_directory/sin-numerar.pdf" 2>/dev/null
swift "$tools_directory/numerar-paginas.swift" "$work_directory/sin-numerar.pdf" "$output_directory/GameConsole_Documentacion.pdf"
rm -f "$source_directory/.documento-temporal.html"
rm -rf "$work_directory"
echo "Documento generado en $output_directory"
