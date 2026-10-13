#!/usr/bin/env bash
set -euo pipefail

source_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "$source_directory/../.." && pwd)"
delivery_directory="$repository_root/entregables"
staging_directory="$(mktemp -d)"
staged_project="$staging_directory/GameStats"

mkdir -p "$delivery_directory" "$staged_project/docs"

"$source_directory/build.sh" "$delivery_directory/GameStats_Documentacion.pdf"
"$source_directory/build-docx.sh" "$delivery_directory/GameStats_Documentacion.docx"

excluded_patterns=(--exclude target/ --exclude .bsp/ --exclude .bloop/ --exclude .metals/ --exclude .idea/ --exclude .DS_Store --exclude '*.log')
for item in ingest-go analytics-scala simulator contracts deploy qa .github Makefile; do
  rsync -a "${excluded_patterns[@]}" "$repository_root/$item" "$staged_project/"
done
for installer in LEEME_PRIMERO.html instalar.sh instalar.bat detener.sh detener.bat; do
  cp "$repository_root/$installer" "$staged_project/"
done
for document in DECISIONS.md STATS.md STATS_EXPECTED_RESULTS.md ADDING_A_GAME.md; do
  cp "$repository_root/docs/$document" "$staged_project/docs/"
done

grep -vE "^(\.claude/|\.antigravity/|entregables/|estudio/)" "$repository_root/.gitignore" > "$staged_project/.gitignore"
cp "$source_directory/leeme-entrega.md" "$staged_project/README.md"

rm -f "$delivery_directory/GameStats_Codigo.zip"
(cd "$staging_directory" && zip -qr "$delivery_directory/GameStats_Codigo.zip" GameStats)
rm -rf "$staging_directory"

echo "Carpeta de entrega: $delivery_directory"
ls -la "$delivery_directory"
