#!/usr/bin/env bash
set -uo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose=(docker compose -f "$repository_root/deploy/docker-compose.yml")
ingest_url="http://localhost:8080"
analytics_url="http://localhost:8081"
run_id="load-$(date +%s)"
work_directory="$(mktemp -d)"
matches_per_game="${LOAD_MATCHES_PER_GAME:-200}"
parallel_rounds="${LOAD_PARALLEL_ROUNDS:-64 128}"
analysis_wait_seconds=180
sampling_interval_seconds=2
open_matches="${LOAD_OPEN_MATCHES:-2000}"
failures=0

export MATCH_IDLE_TIMEOUT_SECONDS=5

report() {
  printf '%-78s %s\n' "$1" "$2"
}

check() {
  if [ "$2" = "true" ]; then
    report "$1" "OK"
  else
    report "$1" "FALLO"
    failures=$((failures + 1))
  fi
}

json() {
  python3 -c "import json,sys; data=json.load(sys.stdin); print($1)"
}

now() {
  python3 -c "import time; print(time.time())"
}

elapsed_since() {
  python3 -c "import time; print(round(time.time() - $1, 2))"
}

cleanup() {
  if [ "${sampler_pid:-0}" -gt 0 ]; then
    kill "$sampler_pid" 2>/dev/null || true
  fi
  rm -rf "$work_directory"
  make -C "$repository_root" down >/dev/null 2>&1 || true
}
trap cleanup EXIT

sample_memory() {
  docker stats --no-stream --format '{{.Name}} {{.MemUsage}} {{.CPUPerc}}' $(docker ps --filter "label=com.docker.compose.project=gamestats" -q) 2>/dev/null
}

keep_sampling() {
  while true; do
    sample_memory >> "$work_directory/samples.txt"
    sleep "$sampling_interval_seconds"
  done
}

peak_memory_report() {
  python3 - "$work_directory/samples.txt" <<'PY'
import re
import sys

peaks = {}
for line in open(sys.argv[1]):
    parts = line.split()
    if len(parts) < 4:
        continue
    name, used, cpu = parts[0], parts[1], parts[-1]
    amount = float(re.match(r"[0-9.]+", used).group(0))
    unit = re.search(r"[A-Za-z]+", used).group(0)
    mebibytes = amount * {"KiB": 1 / 1024, "MiB": 1, "GiB": 1024, "B": 1 / 1048576}[unit]
    peak = peaks.setdefault(name, [0.0, 0.0])
    peak[0] = max(peak[0], mebibytes)
    peak[1] = max(peak[1], float(cpu.strip("%")))
for name in sorted(peaks):
    print("  pico %s memoria=%.1f MiB cpu=%.0f%%" % (name, peaks[name][0], peaks[name][1]))
PY
}

matches_of_run() {
  curl -fs "$analytics_url/stats/matches" | json "len([m for m in data if '$1' in m['matchId']])"
}

events_of_run() {
  curl -fs "$analytics_url/stats/matches" | json "sum(m['totalEvents'] for m in data if '$1' in m['matchId'])"
}

metric() {
  curl -fs "$ingest_url/metrics" | json "data['$1']"
}

timed_get() {
  python3 - "$1" <<'PY'
import sys
import time
import urllib.request

started = time.time()
body = urllib.request.urlopen(sys.argv[1]).read()
print("%.3f s %d bytes" % (time.time() - started, len(body)))
PY
}

summary_value() {
  echo "$1" | tr ' ' '\n' | grep "^$2=" | cut -d= -f2
}

make -C "$repository_root" down >/dev/null 2>&1
"${compose[@]}" up -d --build --wait >"$work_directory/up.log" 2>&1 || { tail -20 "$work_directory/up.log"; exit 1; }

echo "Memoria en reposo"
sample_memory | sed 's/^/  /'

total_expected_events=0
total_expected_matches=0
for parallel in $parallel_rounds; do
  round_id="${run_id}p${parallel}"
  echo
  echo "Ronda: bulk con $matches_per_game partidas por juego y parallel=$parallel"
  : > "$work_directory/samples.txt"
  keep_sampling &
  sampler_pid=$!
  send_started="$(now)"
  summary="$("${compose[@]}" --profile simulation run --rm -e SIMULATOR_SCENARIO=bulk -e SIMULATOR_MATCHES_PER_GAME="$matches_per_game" -e SIMULATOR_PARALLEL="$parallel" -e SIMULATOR_RUN_ID="$round_id" simulator 2>&1 | grep '^matches=' | tail -1)"
  send_seconds="$(elapsed_since "$send_started")"
  echo "  simulador: $summary"
  echo "  tiempo de envio (incluye arrancar el contenedor): $send_seconds s"
  accepted="$(summary_value "$summary" accepted)"
  expected_matches=$(( $(summary_value "$summary" matches) ))
  waited=0
  until [ "$(matches_of_run "$round_id")" -ge "$expected_matches" ] || [ "$waited" -ge "$analysis_wait_seconds" ]; do
    waited=$((waited + 1))
    sleep 1
  done
  all_analyses_seconds="$(elapsed_since "$send_started")"
  echo "  tiempo hasta ver los $expected_matches analisis en Scala (desde el inicio del envio): $all_analyses_seconds s"
  kill "$sampler_pid" 2>/dev/null
  wait "$sampler_pid" 2>/dev/null
  sampler_pid=0
  peak_memory_report
  echo "  memoria justo despues de la ronda:"
  sample_memory | sed 's/^/    /'
  total_expected_events=$((total_expected_events + accepted))
  total_expected_matches=$((total_expected_matches + expected_matches))
  check "ronda $parallel: partidas analizadas ($(matches_of_run "$round_id") de $expected_matches)" "$([ "$(matches_of_run "$round_id")" -eq "$expected_matches" ] && echo true || echo false)"
  check "ronda $parallel: eventos en Scala ($(events_of_run "$round_id") de $accepted)" "$([ "$(events_of_run "$round_id")" -eq "$accepted" ] && echo true || echo false)"
  check "ronda $parallel: sin rechazos ni fallos de envio" "$([ "$(summary_value "$summary" rejected)" -eq 0 ] && [ "$(summary_value "$summary" failed)" -eq 0 ] && echo true || echo false)"
done

echo
echo "Totales"
check "eventos aceptados por Go ($(metric eventsAccepted)) igual a la suma de los enviados ($total_expected_events)" "$([ "$(metric eventsAccepted)" -eq "$total_expected_events" ] && echo true || echo false)"
check "partidas entregadas por Go ($(metric matchesSent)) igual a las esperadas ($total_expected_matches)" "$([ "$(metric matchesSent)" -eq "$total_expected_matches" ] && echo true || echo false)"
check "partidas fallidas en Go ($(metric matchesFailed))" "$([ "$(metric matchesFailed)" -eq 0 ] && echo true || echo false)"
check "partidas activas al final en Go ($(metric activeMatches))" "$([ "$(metric activeMatches)" -eq 0 ] && echo true || echo false)"

echo
echo "Partidas abiertas al mismo tiempo: $open_matches partidas con un solo evento cada una"
python3 - "$open_matches" "$run_id" "$work_directory/open-matches.json" <<'PY'
import json
import sys

events = [
    {
        "schemaVersion": "1.0",
        "eventId": "open-%s-%05d" % (sys.argv[2], index),
        "timestamp": "2026-10-12T20:00:00.000Z",
        "gameId": "chess",
        "gameVersion": "1.0",
        "matchId": "match-open-%s-%05d" % (sys.argv[2], index),
        "type": "MATCH_STARTED",
        "data": {},
    }
    for index in range(int(sys.argv[1]))
]
json.dump(events, open(sys.argv[3], "w"))
PY
open_started="$(now)"
curl -s -o "$work_directory/open-answer.json" -X POST --data-binary "@$work_directory/open-matches.json" "$ingest_url/events/batch"
echo "  respuesta de Go: $(cat "$work_directory/open-answer.json" | cut -c1-80) en $(elapsed_since "$open_started") s"
echo "  partidas activas en Go: $(metric activeMatches)"
echo "  memoria con las partidas abiertas:"
sample_memory | sed 's/^/    /'
waited=0
until [ "$(matches_of_run "open-$run_id")" -ge "$open_matches" ] || [ "$waited" -ge "$analysis_wait_seconds" ]; do
  waited=$((waited + 1))
  sleep 1
done
check "partidas abiertas cerradas por inactividad y analizadas ($(matches_of_run "open-$run_id") de $open_matches)" "$([ "$(matches_of_run "open-$run_id")" -eq "$open_matches" ] && echo true || echo false)"
echo "  memoria tras cerrarse todas:"
sample_memory | sed 's/^/    /'

echo
echo "Latencia de consultas con $(curl -fs "$analytics_url/stats/matches" | json "len(data)") partidas almacenadas en Scala"
echo "  GET /stats/matches: $(timed_get "$analytics_url/stats/matches")"
echo "  GET /stats/games/racing: $(timed_get "$analytics_url/stats/games/racing")"
echo "  GET /patterns: $(timed_get "$analytics_url/patterns")"
echo "  GET /stats/players/<jugador>: $(timed_get "$analytics_url/stats/players/$(curl -fs "$analytics_url/stats/matches?gameId=combat" | json "data[0]['matchId']" | xargs -I{} curl -fs "$analytics_url/stats/matches/{}" | json "data['players'][0]['playerId']")")"

echo
echo "Memoria final (docker stats --no-stream)"
sample_memory | sed 's/^/  /'

if [ "$failures" -eq 0 ]; then
  echo "CARGA APROBADA"
else
  echo "CARGA FALLIDA: $failures verificaciones"
  exit 1
fi
