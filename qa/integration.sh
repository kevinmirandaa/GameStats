#!/usr/bin/env bash
set -uo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose=(docker compose -f "$repository_root/deploy/docker-compose.yml")
ingest_url="http://localhost:8080"
analytics_url="http://localhost:8081"
builder="$repository_root/qa/event_builder.py"
examples="$repository_root/contracts/examples"
run_id="qa7-$(date +%s)"
work_directory="$(mktemp -d)"
idle_timeout_seconds=5
analysis_wait_seconds=60
health_wait_seconds=90
parallel_matches_per_game=15
bulk_matches_per_game=50
mixed_expected_matches=24
status_accepted=202
status_invalid=400
status_conflict=409
failures=0

export MATCH_IDLE_TIMEOUT_SECONDS="$idle_timeout_seconds"

report() {
  printf '%-78s %s\n' "$1" "$2"
}

info() {
  printf 'INFO  %s\n' "$1"
}

check() {
  if [ "$2" = "true" ]; then
    report "$1" "OK"
  else
    report "$1" "FALLO"
    failures=$((failures + 1))
  fi
}

equals() {
  check "$1 (esperado $2, real $3)" "$([ "$2" = "$3" ] && echo true || echo false)"
}

json() {
  python3 -c "import json,sys; data=json.load(sys.stdin); print($1)"
}

cleanup() {
  rm -rf "$work_directory"
  if [ "${KEEP_UP:-0}" != "1" ]; then
    make -C "$repository_root" down >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

build_events() {
  python3 -I "$builder" "$@"
}

post_file() {
  curl -s -o "$work_directory/last-body.json" -w '%{http_code}' -X POST -H 'Content-Type: application/json' --data-binary "@$2" "$ingest_url$1"
}

post_batch_file() {
  post_file /events/batch "$1"
}

batch_field() {
  json "data['$1']" < "$work_directory/last-body.json"
}

wait_until() {
  local limit="$1"
  local command="$2"
  local waited=0
  until eval "$command" >/dev/null 2>&1; do
    waited=$((waited + 1))
    if [ "$waited" -ge "$limit" ]; then
      return 1
    fi
    sleep 1
  done
  return 0
}

analysis_exists() {
  [ "$(curl -s -o /dev/null -w '%{http_code}' "$analytics_url/stats/matches/$1")" = "200" ]
}

analysis_field() {
  curl -fs "$analytics_url/stats/matches/$1" | json "$2"
}

wait_for_analysis() {
  wait_until "$analysis_wait_seconds" "analysis_exists $1"
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

simulator_summary() {
  "${compose[@]}" --profile simulation run --rm "$@" simulator 2>&1 | grep '^matches=' | tail -1
}

summary_value() {
  echo "$1" | tr ' ' '\n' | grep "^$2=" | cut -d= -f2
}

echo "Limpieza previa y arranque del sistema"
make -C "$repository_root" down >/dev/null 2>&1
if ! "${compose[@]}" up -d --build --wait >"$work_directory/up.log" 2>&1; then
  tail -20 "$work_directory/up.log"
  exit 1
fi
ingest_image_before="$(docker images -q gamestats-ingest:local)"
analytics_image_before="$(docker images -q gamestats-analytics:local)"

echo
echo "1. Eventos validos de contracts/examples/valid"
valid_total=0
valid_accepted=0
for file in "$examples"/valid/*.json; do
  valid_total=$((valid_total + 1))
  if [ "$(post_file /events "$file")" = "$status_accepted" ]; then
    valid_accepted=$((valid_accepted + 1))
  else
    info "no aceptado: $(basename "$file") -> $(cat "$work_directory/last-body.json")"
  fi
done
equals "ejemplos validos aceptados con 202" "$valid_total" "$valid_accepted"

echo
echo "2. Eventos invalidos de contracts/examples/invalid"
invalid_total=0
invalid_rejected=0
for file in "$examples"/invalid/*.json; do
  invalid_total=$((invalid_total + 1))
  if [ "$(post_file /events "$file")" = "$status_invalid" ]; then
    invalid_rejected=$((invalid_rejected + 1))
  else
    info "no rechazado con 400: $(basename "$file") -> $(cat "$work_directory/last-body.json")"
  fi
done
equals "ejemplos invalidos rechazados con 400" "$invalid_total" "$invalid_rejected"
printf '{"not":' > "$work_directory/broken.json"
equals "JSON roto en POST /events devuelve 400" "$status_invalid" "$(post_file /events "$work_directory/broken.json")"
printf '{"not":"an array"}' > "$work_directory/object.json"
equals "objeto en POST /events/batch devuelve 400" "$status_invalid" "$(post_batch_file "$work_directory/object.json")"
rejected_listed="$(curl -fs "$ingest_url/rejected" | json "len(data['rejected'])")"
check "GET /rejected lista los rechazos ($rejected_listed)" "$([ "$rejected_listed" -ge "$invalid_total" ] && echo true || echo false)"

echo
echo "3. Duplicados"
equals "reenviar un evento ya aceptado devuelve 409" "$status_conflict" "$(post_file /events "$examples/valid/player-joined-racing.json")"

echo
echo "4. Lote mezclado: validos, duplicados e invalidos en una sola llamada"
mixed_match="match-chess-$run_id-mixed"
build_events --game chess --match "$mixed_match" --players ana,beto --actions MOVE,CAPTURE --count 20 --duplicate 4 --invalid 3 > "$work_directory/mixed.json"
post_batch_file "$work_directory/mixed.json" >/dev/null
equals "lote mezclado: aceptados" 24 "$(batch_field accepted)"
equals "lote mezclado: rechazados (4 duplicados y 3 invalidos)" 7 "$(batch_field rejected)"
wait_for_analysis "$mixed_match"
equals "Scala recibe solo los eventos aceptados" 24 "$(analysis_field "$mixed_match" "data['totalEvents']")"

echo
echo "5. Eventos desordenados"
reverse_match="match-chess-$run_id-reverse"
build_events --game chess --match "$reverse_match" --players ana,beto --actions MOVE,CAPTURE --count 30 --order reverse > "$work_directory/reverse.json"
post_batch_file "$work_directory/reverse.json" >/dev/null
equals "orden inverso: aceptados" 34 "$(batch_field accepted)"
wait_for_analysis "$reverse_match"
equals "orden inverso: estado COMPLETED" COMPLETED "$(analysis_field "$reverse_match" "data['status']")"
equals "orden inverso: eventos en Scala" 34 "$(analysis_field "$reverse_match" "data['totalEvents']")"
equals "orden inverso: duracion igual a la del orden natural" 33 "$(analysis_field "$reverse_match" "data['durationSeconds']")"
shuffle_match="match-chess-$run_id-shuffle"
build_events --game chess --match "$shuffle_match" --players ana,beto,carla --actions MOVE,CAPTURE --count 60 --order shuffle > "$work_directory/shuffle.json"
post_batch_file "$work_directory/shuffle.json" >/dev/null
wait_for_analysis "$shuffle_match"
equals "orden aleatorio: duracion" 64 "$(analysis_field "$shuffle_match" "data['durationSeconds']")"
equals "orden aleatorio: ganador" ana "$(analysis_field "$shuffle_match" "data['winnerPlayerId']")"

echo
echo "6. Partidas concurrentes enviadas por clientes HTTP paralelos"
parallel_total=0
for index in $(seq 1 "$parallel_matches_per_game"); do
  for game in racing combat chess; do
    parallel_total=$((parallel_total + 1))
    match="match-$game-$run_id-par-$index"
    build_events --game "$game" --match "$match" --players p1,p2,p3 --actions A,B,C --count 40 --order shuffle --seed "$index" > "$work_directory/par-$game-$index.json"
    (post_file /events/batch "$work_directory/par-$game-$index.json" >/dev/null; true) &
  done
done
wait
wait_until "$analysis_wait_seconds" "[ \"\$(matches_of_run $run_id-par)\" -ge $parallel_total ]"
equals "partidas concurrentes analizadas" "$parallel_total" "$(matches_of_run "$run_id-par")"
equals "eventos de las partidas concurrentes (45 por partida)" $((parallel_total * 45)) "$(events_of_run "$run_id-par")"
wrong_totals="$(curl -fs "$analytics_url/stats/matches" | json "len([m for m in data if '$run_id-par' in m['matchId'] and m['totalEvents'] != 45])")"
equals "ninguna partida concurrente con eventos mezclados o perdidos" 0 "$wrong_totals"

echo
echo "7. Escenario bulk del simulador con $bulk_matches_per_game partidas por juego"
accepted_before="$(metric eventsAccepted)"
sent_before="$(metric matchesSent)"
bulk_summary="$(simulator_summary -e SIMULATOR_SCENARIO=bulk -e SIMULATOR_MATCHES_PER_GAME=$bulk_matches_per_game -e SIMULATOR_RUN_ID=${run_id}b)"
info "simulador: $bulk_summary"
bulk_expected_matches=$((bulk_matches_per_game * 2))
wait_until "$analysis_wait_seconds" "[ \"\$(matches_of_run ${run_id}b)\" -ge $bulk_expected_matches ]"
equals "bulk: partidas analizadas en Scala" "$bulk_expected_matches" "$(matches_of_run "${run_id}b")"
bulk_accepted="$(summary_value "$bulk_summary" accepted)"
equals "bulk: rechazos esperados" 0 "$(summary_value "$bulk_summary" rejected)"
equals "bulk: fallos de envio" 0 "$(summary_value "$bulk_summary" failed)"
equals "bulk: eventos en Scala igual a eventos aceptados por el simulador" "$bulk_accepted" "$(events_of_run "${run_id}b")"
equals "bulk: eventos aceptados por Go (metrica) igual a los enviados" "$bulk_accepted" "$(( $(metric eventsAccepted) - accepted_before ))"
equals "bulk: partidas entregadas por Go a Scala (metrica)" "$bulk_expected_matches" "$(( $(metric matchesSent) - sent_before ))"
equals "bulk: todas COMPLETED" "$bulk_expected_matches" "$(curl -fs "$analytics_url/stats/matches" | json "len([m for m in data if '${run_id}b' in m['matchId'] and m['status']=='COMPLETED'])")"

echo
echo "8. Escenario mixed-parallel: desorden, duplicados, invalidos y desconexiones"
mixed_summary="$(simulator_summary -e SIMULATOR_SCENARIO=mixed-parallel -e SIMULATOR_RUN_ID=${run_id}m)"
info "simulador: $mixed_summary"
wait_until "$analysis_wait_seconds" "[ \"\$(matches_of_run ${run_id}m)\" -ge $mixed_expected_matches ]"
equals "mixed: partidas analizadas" "$mixed_expected_matches" "$(matches_of_run "${run_id}m")"
mixed_accepted="$(summary_value "$mixed_summary" accepted)"
mixed_rejected="$(summary_value "$mixed_summary" rejected)"
check "mixed: hubo rechazos esperados ($mixed_rejected)" "$([ "$mixed_rejected" -ge 1 ] && echo true || echo false)"
equals "mixed: enviados igual a aceptados mas rechazados" "$(summary_value "$mixed_summary" sent)" "$((mixed_accepted + mixed_rejected))"
equals "mixed: eventos en Scala igual a aceptados por Go" "$mixed_accepted" "$(events_of_run "${run_id}m")"
equals "mixed: ninguna partida fallo al entregarse" 0 "$(metric matchesFailed)"

echo
echo "8b. Una partida muy grande (30000 eventos en una sola llamada)"
large_match="match-chess-$run_id-large"
build_events --game chess --match "$large_match" --players ana,beto --actions MOVE,CAPTURE --count 30000 > "$work_directory/large.json"
post_batch_file "$work_directory/large.json" >/dev/null
equals "partida grande: aceptados" 30004 "$(batch_field accepted)"
equals "partida grande: rechazados por buzon lleno" 0 "$(batch_field rejected)"
wait_for_analysis "$large_match"
equals "partida grande: eventos en Scala" 30004 "$(analysis_field "$large_match" "data['totalEvents']")"

echo
echo "9. Partida sin MATCH_FINISHED"
unfinished_match="match-chess-$run_id-unfinished"
build_events --game chess --match "$unfinished_match" --players ana,beto --actions MOVE,CAPTURE --count 12 --no-finish > "$work_directory/unfinished.json"
post_batch_file "$work_directory/unfinished.json" >/dev/null
sleep 1
equals "partida abierta antes del cierre por inactividad: no analizada aun" 404 "$(curl -s -o /dev/null -w '%{http_code}' "$analytics_url/stats/matches/$unfinished_match")"
wait_for_analysis "$unfinished_match"
equals "sin MATCH_FINISHED llega como INCOMPLETE" INCOMPLETE "$(analysis_field "$unfinished_match" "data['status']")"
equals "INCOMPLETE conserva los 15 eventos" 15 "$(analysis_field "$unfinished_match" "data['totalEvents']")"
check "INCOMPLETE no declara ganador" "$([ "$(analysis_field "$unfinished_match" "'winnerPlayerId' in data")" = "False" ] && echo true || echo false)"
python3 - "$unfinished_match" "$run_id" "$work_directory/late.json" <<'PY'
import json
import sys

late_event = {
    "schemaVersion": "1.0",
    "eventId": "late-" + sys.argv[2],
    "timestamp": "2026-10-12T21:00:00Z",
    "gameId": "chess",
    "gameVersion": "1.0",
    "matchId": sys.argv[1],
    "playerId": "ana",
    "type": "GAME_ACTION",
    "action": "MOVE",
}
json.dump(late_event, open(sys.argv[3], "w"))
PY
equals "evento tardio de una partida cerrada devuelve 409" "$status_conflict" "$(post_file /events "$work_directory/late.json")"

echo
echo "10. Genericidad: juegos que el sistema nunca vio"
python3 - "$examples" "$work_directory/third-game.json" <<'PY'
import glob
import json
import sys

events = [json.load(open(path)) for path in sorted(glob.glob(sys.argv[1] + "/valid/third-game-*.json"))]
for event in events:
    event["eventId"] = event["eventId"] + "-generic"
    event["matchId"] = event["matchId"] + "-generic"
json.dump(events, open(sys.argv[2], "w"))
PY
chess_match="match-chess-$run_id-generic"
build_events --game chess --match "$chess_match" --players kasparov,karpov --actions MOVE,CAPTURE --count 30 > "$work_directory/chess.json"
python3 - "$work_directory/third-game.json" "$work_directory/chess.json" "$work_directory/generic-batch.json" <<'PY'
import json
import random
import sys

events = json.load(open(sys.argv[1])) + json.load(open(sys.argv[2]))
random.Random(7).shuffle(events)
json.dump(events, open(sys.argv[3], "w"))
PY
post_batch_file "$work_directory/generic-batch.json" >/dev/null
equals "genericidad: eventos aceptados (2 de treasure-hunt y 34 de chess)" 36 "$(batch_field accepted)"
equals "genericidad: eventos rechazados" 0 "$(batch_field rejected)"
wait_for_analysis "$chess_match"
equals "chess: agrupado en una sola partida con todos sus eventos" 34 "$(analysis_field "$chess_match" "data['totalEvents']")"
equals "chess: conteo por action MOVE" 15 "$(analysis_field "$chess_match" "data['eventCountsByAction']['MOVE']")"
equals "chess: conteo por action CAPTURE" 15 "$(analysis_field "$chess_match" "data['eventCountsByAction']['CAPTURE']")"
equals "chess: jugadores detectados" "karpov,kasparov" "$(analysis_field "$chess_match" "','.join(sorted(p['playerId'] for p in data['players']))")"
equals "chess: eventos por jugador kasparov" 16 "$(analysis_field "$chess_match" "[p['eventCount'] for p in data['players'] if p['playerId']=='kasparov'][0]")"
equals "chess: duracion en segundos" 33 "$(analysis_field "$chess_match" "data['durationSeconds']")"
equals "chess: ganador tomado de MATCH_FINISHED" kasparov "$(analysis_field "$chess_match" "data['winnerPlayerId']")"
equals "chess: sin metricas especificas (analisis generico)" "{}" "$(analysis_field "$chess_match" "data['matchMetrics']")"
equals "GET /stats/games/chess: partidas analizadas" "$(curl -fs "$analytics_url/stats/matches?gameId=chess" | json "len(data)")" "$(curl -fs "$analytics_url/stats/games/chess" | json "int(data['aggregates']['matchesAnalyzed'])")"
check "GET /stats/players/kasparov responde con partidas jugadas" "$([ "$(curl -s -o /dev/null -w '%{http_code}' "$analytics_url/stats/players/kasparov")" = "200" ] && echo true || echo false)"
equals "GET /stats/players/kasparov: partidas jugadas" 1 "$(curl -fs "$analytics_url/stats/players/kasparov" | json "int(data['aggregates']['matchesPlayed'])")"
equals "GET /stats/players/kasparov: victorias" 1 "$(curl -fs "$analytics_url/stats/players/kasparov" | json "int(data['aggregates']['wins'])")"
treasure_match="match-treasure-hunt-001-generic"
wait_for_analysis "$treasure_match"
equals "treasure-hunt (ejemplos del repositorio): estado" INCOMPLETE "$(analysis_field "$treasure_match" "data['status']")"
equals "treasure-hunt: conteo por action ITEM_COLLECTED" 1 "$(analysis_field "$treasure_match" "data['eventCountsByAction']['ITEM_COLLECTED']")"
equals "treasure-hunt: jugador identificado" player-31 "$(analysis_field "$treasure_match" "data['players'][0]['playerId']")"
equals "treasure-hunt: GET /stats/games/treasure-hunt cuenta sus partidas (la del paso 1 y la de este paso)" 2 "$(curl -fs "$analytics_url/stats/games/treasure-hunt" | json "int(data['aggregates']['matchesAnalyzed'])")"
equals "genericidad: imagen de ingest-go sin reconstruir" "$ingest_image_before" "$(docker images -q gamestats-ingest:local)"
equals "genericidad: imagen de analytics-scala sin reconstruir" "$analytics_image_before" "$(docker images -q gamestats-analytics:local)"

echo
echo "11. Scala caido durante un envio: caida corta (se reinicia a los 3 s)"
short_match="match-chess-$run_id-outage-short"
build_events --game chess --match "$short_match" --players ana,beto --actions MOVE,CAPTURE --count 10 > "$work_directory/outage-short.json"
"${compose[@]}" stop analytics-scala >/dev/null 2>&1
failed_before="$(metric matchesFailed)"
post_batch_file "$work_directory/outage-short.json" >/dev/null
equals "con Scala caido Go sigue aceptando eventos" 14 "$(batch_field accepted)"
sleep 3
outage_started="$(date +%s)"
"${compose[@]}" start analytics-scala >/dev/null 2>&1
wait_until "$health_wait_seconds" "curl -fs $analytics_url/health"
health_back="$(date +%s)"
info "Scala volvio a responder /health $((health_back - outage_started)) s despues de start"
wait_until "$analysis_wait_seconds" "analysis_exists $short_match"
check "caida corta: la partida se entrega tras los reintentos" "$(analysis_exists "$short_match" && echo true || echo false)"
equals "caida corta: eventos completos" 14 "$(analysis_field "$short_match" "data['totalEvents']")"
equals "caida corta: sin fallos de entrega registrados" "$failed_before" "$(metric matchesFailed)"

echo
echo "12. Scala caido durante un envio: caida larga (mas que la ventana de reintentos)"
long_match="match-chess-$run_id-outage-long"
build_events --game chess --match "$long_match" --players ana,beto --actions MOVE,CAPTURE --count 10 > "$work_directory/outage-long.json"
"${compose[@]}" stop analytics-scala >/dev/null 2>&1
post_batch_file "$work_directory/outage-long.json" >/dev/null
long_started="$(date +%s)"
wait_until "$analysis_wait_seconds" "[ \"\$(metric matchesFailed)\" -gt $failed_before ]"
info "Go declaro la entrega fallida $(( $(date +%s) - long_started )) s despues del envio"
equals "caida larga: matchesFailed aumenta en 1" $((failed_before + 1)) "$(metric matchesFailed)"
"${compose[@]}" start analytics-scala >/dev/null 2>&1
wait_until "$health_wait_seconds" "curl -fs $analytics_url/health"
equals "caida larga: la partida no esta en Scala (se perdio)" 404 "$(curl -s -o /dev/null -w '%{http_code}' "$analytics_url/stats/matches/$long_match")"
python3 -I "$builder" --game chess --match "$long_match" --players ana,beto --actions MOVE,CAPTURE --count 10 > "$work_directory/outage-long-retry.json"
post_batch_file "$work_directory/outage-long-retry.json" >/dev/null
equals "caida larga: reenviar la misma partida es rechazado (sin recuperacion)" 14 "$(( $(batch_field rejected) + $(batch_field accepted) ))"
equals "caida larga: el reenvio no acepta ningun evento" 0 "$(batch_field accepted)"
equals "reinicio de Scala: se pierde el historial en memoria (partidas guardadas antes)" 0 "$(matches_of_run "$run_id")"

echo
ingest_image_after="$(docker images -q gamestats-ingest:local)"
check "imagen de ingest-go identica al inicio y al final" "$([ "$ingest_image_before" = "$ingest_image_after" ] && echo true || echo false)"

if [ "$failures" -eq 0 ]; then
  echo "INTEGRACION APROBADA"
else
  echo "INTEGRACION FALLIDA: $failures verificaciones"
  exit 1
fi
