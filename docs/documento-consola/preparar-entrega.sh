#!/usr/bin/env bash
set -euo pipefail

source_directory="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "$source_directory/../.." && pwd)"
delivery_directory="$repository_root/entregables-proyecto2"
staging_directory="$(mktemp -d)"
staged_project="$staging_directory/GameStats"

mkdir -p "$delivery_directory" "$staged_project/docs"

"$source_directory/build.sh" "$delivery_directory"

excluded_patterns=(--exclude target/ --exclude node_modules/ --exclude .bsp/ --exclude .bloop/ --exclude .metals/ --exclude .idea/ --exclude .DS_Store --exclude '*.log')
for item in console prolog-ai demo ingest-go analytics-scala simulator contracts deploy qa .github Makefile README.md instalar.sh instalar.bat detener.sh detener.bat LEEME_PRIMERO.html; do
  rsync -a "${excluded_patterns[@]}" "$repository_root/$item" "$staged_project/"
done
for document in DECISIONS.md STATS.md STATS_EXPECTED_RESULTS.md ADDING_A_GAME.md; do
  cp "$repository_root/docs/$document" "$staged_project/docs/"
done
cp "$repository_root/.gitignore" "$staged_project/.gitignore" 2>/dev/null || true

rm -f "$delivery_directory/GameStats_Codigo.zip"
(cd "$staging_directory" && zip -qr "$delivery_directory/GameStats_Codigo.zip" GameStats)
rm -rf "$staging_directory"

echo "Carpeta de entrega: $delivery_directory"
ls -la "$delivery_directory"
