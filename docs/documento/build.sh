#!/usr/bin/env bash
set -euo pipefail

source_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
output_pdf="${1:-$source_directory/GameStats_Documentacion.pdf}"
professor_name="${PROFESOR:-Oscar Víquez Acuña}"
work_directory="$(mktemp -d)"

python3 - "$source_directory/documentacion.html" "$source_directory/.documento-temporal.html" "$professor_name" <<'PYTHON'
import re
import sys

source_path, output_path, professor = sys.argv[1], sys.argv[2], sys.argv[3]
html = open(source_path, encoding="utf-8").read().replace("__PROFESOR__", professor)
figure_with_caption = r"<div class=\"(?:code|fig)\">.*?</div>(?:\s*<p class=\"cap\">.*?</p>)?"
heading_with_next = r"<h[123][^>]*>[^<]*</h[123]>\s*(?:<table.*?</table>|<ul>.*?</ul>|<ol>.*?</ol>|<p[^>]*>.*?</p>|" + figure_with_caption + ")"
standalone_table = r"<table.*?</table>"
keep_together = re.compile("(" + heading_with_next + ")|(" + figure_with_caption + ")|(" + standalone_table + ")", re.S)
html = keep_together.sub(lambda found: '<div class="keep">' + found.group(0) + "</div>", html)
open(output_path, "w", encoding="utf-8").write(html)
PYTHON
swift "$source_directory/tools/html-a-pdf.swift" "$source_directory/.documento-temporal.html" "$work_directory/sin-numerar.pdf" 2>/dev/null
swift "$source_directory/tools/numerar-paginas.swift" "$work_directory/sin-numerar.pdf" "$output_pdf"
rm -f "$source_directory/.documento-temporal.html"
rm -rf "$work_directory"
echo "Documento generado: $output_pdf"
