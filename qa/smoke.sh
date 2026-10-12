#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="${SMOKE_COMPOSE_FILE:-$repository_root/deploy/docker-compose.yml}"
build_images="${SMOKE_BUILD:-1}"
compose=(docker compose -f "$compose_file")
ingest_url="http://localhost:8080"
analytics_url="http://localhost:8081"
run_id="smoke-$(date +%s)"
expected_matches_per_game=12
idle_timeout_seconds=5
wait_limit_seconds=90

export MATCH_IDLE_TIMEOUT_SECONDS="$idle_timeout_seconds"

failures=0

report() {
  printf '%-62s %s\n' "$1" "$2"
}

check() {
  local description="$1"
  local condition="$2"
  if [ "$condition" = "true" ]; then
    report "$description" "OK"
  else
    report "$description" "FALLO"
    failures=$((failures + 1))
  fi
}

contains_word() {
  if echo " $1 " | grep -q " $2 "; then
    echo true
  else
    echo false
  fi
}

json() {
  python3 -c "import json,sys; data=json.load(sys.stdin); print($1)"
}

cleanup() {
  if [ "${KEEP_UP:-0}" != "1" ]; then
    "${compose[@]}" --profile simulation down --volumes --remove-orphans >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

wait_until() {
  local description="$1"
  local command="$2"
  local waited=0
  until eval "$command" >/dev/null 2>&1; do
    waited=$((waited + 2))
    if [ "$waited" -ge "$wait_limit_seconds" ]; then
      report "$description" "TIEMPO AGOTADO"
      failures=$((failures + 1))
      return 1
    fi
    sleep 2
  done
  return 0
}

echo "Levantando el sistema con $compose_file"
if [ "$build_images" = "1" ]; then
  "${compose[@]}" up -d --build --wait
else
  "${compose[@]}" up -d --wait
fi

echo "Ejecutando el escenario mixed-parallel"
SIMULATOR_SCENARIO=mixed-parallel SIMULATOR_RUN_ID="$run_id" "${compose[@]}" --profile simulation run --rm simulator

echo "Esperando los analisis"
matches_of_this_run() {
  curl -fs "$analytics_url/stats/matches" | json "len([m for m in data if '$run_id' in m['matchId']])"
}
wait_until "analisis de las 24 partidas" '[ "$(matches_of_this_run)" -ge $((expected_matches_per_game * 2)) ]' || true

echo "Verificando resultados"
racing_matches="$(curl -fs "$analytics_url/stats/matches?gameId=racing" | json "len([m for m in data if '$run_id' in m['matchId']])")"
combat_matches="$(curl -fs "$analytics_url/stats/matches?gameId=combat" | json "len([m for m in data if '$run_id' in m['matchId']])")"
completed_matches="$(curl -fs "$analytics_url/stats/matches" | json "len([m for m in data if '$run_id' in m['matchId'] and m['status']=='COMPLETED'])")"
check "partidas de racing analizadas ($racing_matches de $expected_matches_per_game)" "$([ "$racing_matches" -eq "$expected_matches_per_game" ] && echo true || echo false)"
check "partidas de combat analizadas ($combat_matches de $expected_matches_per_game)" "$([ "$combat_matches" -eq "$expected_matches_per_game" ] && echo true || echo false)"
check "todas las partidas COMPLETED ($completed_matches de 24)" "$([ "$completed_matches" -eq 24 ] && echo true || echo false)"

pattern_names="$(curl -fs "$analytics_url/patterns" | json "' '.join(sorted(set(p['pattern']['name'] for p in data if '$run_id' in p['matchId'])))")"
pattern_total="$(curl -fs "$analytics_url/patterns" | json "len([p for p in data if '$run_id' in p['matchId']])")"
check "al menos un patron detectado ($pattern_total)" "$([ "$pattern_total" -ge 1 ] && echo true || echo false)"
for expected in RACE_COMEBACK IMPROVING_LAP_STREAK KILL_STREAK SCORE_COMEBACK; do
  check "patron $expected presente" "$(contains_word "$pattern_names" "$expected")"
done

racing_aggregate="$(curl -fs "$analytics_url/stats/games/racing" | json "int(data['aggregates']['matchesAnalyzed'])")"
check "agregado de juego racing disponible ($racing_aggregate partidas)" "$([ "$racing_aggregate" -ge "$expected_matches_per_game" ] && echo true || echo false)"
sample_player="$(curl -fs "$analytics_url/stats/matches?gameId=combat" | json "data[0]['matchId']" | xargs -I{} curl -fs "$analytics_url/stats/matches/{}" | json "data['players'][0]['playerId']")"
player_matches="$(curl -fs "$analytics_url/stats/players/$sample_player" | json "int(data['aggregates']['matchesPlayed'])")"
check "agregado de jugador disponible ($sample_player en $player_matches partidas)" "$([ "$player_matches" -ge 1 ] && echo true || echo false)"

ingest_metrics="$(curl -fs "$ingest_url/metrics")"
rejected="$(echo "$ingest_metrics" | json "data['eventsRejected']")"
failed_deliveries="$(echo "$ingest_metrics" | json "data['matchesFailed']")"
active="$(echo "$ingest_metrics" | json "data['activeMatches']")"
check "eventos invalidos y duplicados rechazados ($rejected)" "$([ "$rejected" -ge 1 ] && echo true || echo false)"
check "ninguna partida fallo al enviarse a Scala ($failed_deliveries)" "$([ "$failed_deliveries" -eq 0 ] && echo true || echo false)"
check "ninguna partida quedo activa en Go ($active)" "$([ "$active" -eq 0 ] && echo true || echo false)"

echo "Ejecutando el escenario unfinished-match"
unfinished_run_id="${run_id}-unfinished"
SIMULATOR_SCENARIO=unfinished-match SIMULATOR_MATCHES_PER_GAME=2 SIMULATOR_RUN_ID="$unfinished_run_id" "${compose[@]}" --profile simulation run --rm simulator
incomplete_matches() {
  curl -fs "$analytics_url/stats/matches" | json "len([m for m in data if '$unfinished_run_id' in m['matchId'] and m['status']=='INCOMPLETE'])"
}
wait_until "partidas INCOMPLETE por inactividad" '[ "$(incomplete_matches)" -ge 4 ]' || true
check "partidas sin cierre llegan como INCOMPLETE ($(incomplete_matches) de 4)" "$([ "$(incomplete_matches)" -ge 4 ] && echo true || echo false)"

echo
if [ "$failures" -eq 0 ]; then
  echo "PRUEBA DE HUMO APROBADA"
else
  echo "PRUEBA DE HUMO FALLIDA: $failures verificaciones"
  exit 1
fi
