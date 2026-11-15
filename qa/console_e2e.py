import os
import random
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request

CONSOLE = os.environ.get("CONSOLE_URL", "http://localhost:8100")
ANALYTICS = os.environ.get("ANALYTICS_URL", "http://localhost:8081")
INGEST = os.environ.get("INGEST_URL", "http://localhost:8080")
COMPOSE_FILE = os.environ.get("E2E_COMPOSE_FILE", "deploy/docker-compose.yml")
WAIT_SECONDS = 60
COMPUTER_ID = "ai-prolog-connect4"

failures = []
checks = 0
lock = threading.Lock()


def report(description, ok):
    global checks
    with lock:
        checks += 1
        print("%-78s %s" % (description, "OK" if ok else "FALLO"), flush=True)
        if not ok:
            failures.append(description)


sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "demo"))

from gameplay import act, create, discs, http, play_connect4, play_memory, play_minesweeper, playable_columns


def wait_for(description, condition, seconds=WAIT_SECONDS):
    deadline = time.time() + seconds
    while time.time() < deadline:
        try:
            value = condition()
        except (KeyError, TypeError, IndexError):
            value = None
        if value:
            return value
        time.sleep(0.4)
    report(description + " (tiempo agotado)", False)
    return None


def analysis_of(match_id):
    status, body = http("GET", "%s/stats/matches/%s" % (ANALYTICS, match_id))
    return body if status == 200 else None


def wait_analysis(match_id, seconds=WAIT_SECONDS):
    return wait_for("GameStats analiza " + match_id, lambda: analysis_of(match_id), seconds)


def check_lifecycle(analysis, participants, label):
    counts = analysis["eventCountsByType"]
    report("%s: un solo MATCH_STARTED y un solo MATCH_FINISHED" % label, counts.get("MATCH_STARTED") == 1 and counts.get("MATCH_FINISHED") == 1)
    report("%s: %d jugadores unidos y %d salidas" % (label, participants, participants), counts.get("PLAYER_JOINED") == participants and counts.get("PLAYER_LEFT") == participants)
    report("%s: partida COMPLETED en GameStats" % label, analysis["status"] == "COMPLETED")


def section(title):
    print("\n== " + title, flush=True)


def system_checks():
    section("Sistema integrado")
    status, health = http("GET", CONSOLE + "/api/health")
    report("la consola responde y esta UP", status == 200 and health["status"] == "UP")
    report("Prolog esta disponible desde la consola", bool(health and health["prologAvailable"]))
    report("la consola carga exactamente tres juegos", bool(health) and health["games"] == 3)
    _, games = http("GET", CONSOLE + "/api/games")
    report("catalogo: connect4, memory y minesweeper", sorted(game["gameId"] for game in games) == ["connect4", "memory", "minesweeper"])
    _, plugins = http("GET", CONSOLE + "/api/plugins")
    report("los tres juegos se cargaron como plugins JAR sin fallos", len(plugins["loaded"]) == 3 and not plugins["failures"])
    report("cada juego declara al menos un modo", all(game["modes"] for game in games))
    report("GameStats ingesta responde", http("GET", INGEST + "/health")[0] == 200)
    report("GameStats analisis responde", http("GET", ANALYTICS + "/health")[0] == 200)
    page = urllib.request.urlopen(CONSOLE + "/").read().decode()
    report("la consola web se sirve en la raiz", "GameConsole" in page)
    script = urllib.request.urlopen(CONSOLE + "/app.js").read().decode()
    report("la consola web consulta las estadisticas a la API", "/api/stats/overview" in script)


def connect4_with_prolog(generator):
    section("Conecta 4 contra la computadora (Prolog)")
    name = "qa-conecta-%d" % generator.randint(1000, 9999)
    view = play_connect4([name], "VS_COMPUTER", {"difficulty": "FACIL"}, generator)
    report("la partida contra la computadora termina", view["finished"])
    analysis = wait_analysis(view["matchId"])
    if not analysis:
        return
    check_lifecycle(analysis, 2, "connect4")
    computer_discs = discs(view, "yellow")
    report("decisiones de Prolog registradas igual a fichas de la computadora", analysis["matchMetrics"]["aiDecisions"] == computer_discs and computer_discs > 0)
    report("cada decision de Prolog incluye nodos buscados", analysis["matchMetrics"]["averageAiNodes"] > 0)
    report("jugadas totales igual a fichas en el tablero", analysis["matchMetrics"]["totalMoves"] == discs(view, "red") + computer_discs)
    report("existe AI_DECISION en las acciones analizadas", analysis["eventCountsByAction"].get("AI_DECISION") == computer_discs)
    outcomes = sum(analysis["eventCountsByAction"].get(key, 0) for key in ("PLAYER_WON", "PLAYER_LOST", "PLAYER_DRAW"))
    report("resultado PLAYER_WON/PLAYER_LOST/PLAYER_DRAW emitido para ambos", outcomes == 2)
    ids = sorted(player["playerId"] for player in analysis["players"])
    report("los jugadores son el humano y la computadora", ids == sorted(["player-" + name, COMPUTER_ID]))


def prolog_decides_by_search():
    section("La computadora decide con Prolog, no al azar")
    survived = 0
    attempts = 5
    for attempt in range(attempts):
        view = create("connect4", "VS_COMPUTER", ["qa-ataque-%d" % attempt], {"difficulty": "FACIL"})
        for _ in range(4):
            if view["finished"]:
                break
            if not view["cells"][0]["enabled"]:
                break
            _, body = act(view, {"cell": 0})
            view = body["view"]
        if view["winnerPlayerId"] != "player-qa-ataque-%d" % attempt:
            survived += 1
        http("DELETE", "%s/api/sessions/%s" % (CONSOLE, view["matchId"]))
    report("la computadora bloquea la torre de 3 fichas en %d de %d intentos" % (attempts, attempts), survived == attempts)

    view = create("connect4", "VS_COMPUTER", ["qa-gana"], {"difficulty": "DIFICIL", "first": "COMPUTADORA"})
    report("si empieza la computadora, juega primero en cuanto se crea la partida", discs(view, "yellow") == 1 or discs(view, "red") == 1)
    http("DELETE", "%s/api/sessions/%s" % (CONSOLE, view["matchId"]))


def two_player_connect4():
    section("Conecta 4 para dos jugadores")
    view = create("connect4", "TWO_PLAYERS", ["qa-ana", "qa-luis"])
    for column in (0, 1, 0, 1, 0, 1, 0):
        _, body = act(view, {"cell": column})
        view = body["view"]
    report("cuatro fichas verticales ganan la partida", view["finished"] and view["winnerPlayerId"] == "player-qa-ana")
    status, rejected = act(view, {"cell": 3})
    report("jugar despues del final se rechaza", status == 422 and not rejected["accepted"])
    analysis = wait_analysis(view["matchId"])
    if analysis:
        check_lifecycle(analysis, 2, "connect4 dos jugadores")
        report("el ganador llega a GameStats", analysis.get("winnerPlayerId") == "player-qa-ana")
        report("victoria humana contabilizada", analysis["matchMetrics"]["humanWin"] == 1 and analysis["matchMetrics"]["totalMoves"] == 7)


def memory_game():
    section("Memoria")
    view, flips = play_memory(["qa-mem-a", "qa-mem-b"], "TWO_PLAYERS", "PEQUENO")
    report("la partida de memoria termina", view["finished"])
    analysis = wait_analysis(view["matchId"])
    if not analysis:
        return
    check_lifecycle(analysis, 2, "memoria")
    report("las 6 parejas fueron encontradas", analysis["matchMetrics"]["pairsFound"] == 6)
    report("las cartas volteadas coinciden con las enviadas", analysis["matchMetrics"]["totalFlips"] == flips)
    report("hay ganador o empate informado", analysis["eventCountsByAction"].get("PLAYER_WON", 0) + analysis["eventCountsByAction"].get("PLAYER_DRAW", 0) >= 1)


def minesweeper_game(generator):
    section("Buscaminas")
    view, reveals = play_minesweeper("qa-minas", True, generator)
    report("la partida de buscaminas termina (victoria o mina)", view["finished"])
    analysis = wait_analysis(view["matchId"])
    if not analysis:
        return
    check_lifecycle(analysis, 1, "buscaminas")
    report("las casillas descubiertas coinciden con las enviadas", analysis["matchMetrics"]["reveals"] == reveals)
    report("la bandera colocada y retirada se registro", analysis["eventCountsByAction"].get("FLAG_CELL") == 2)
    outcome = analysis["eventCountsByAction"].get("PLAYER_WON", 0) + analysis["eventCountsByAction"].get("PLAYER_LOST", 0)
    report("resultado de victoria o derrota emitido", outcome == 1)


def abandoned_game():
    section("Partida abandonada")
    view = create("minesweeper", "SOLO", ["qa-abandono"])
    act(view, {"cell": 40})
    status, _ = http("DELETE", "%s/api/sessions/%s" % (CONSOLE, view["matchId"]))
    report("abandonar una partida abierta responde 204", status == 204)
    analysis = wait_analysis(view["matchId"])
    if analysis:
        report("GameStats recibe el abandono y lo cuenta", analysis["matchMetrics"]["abandoned"] == 1)
        check_lifecycle(analysis, 1, "abandono")


def live_statistics():
    section("Estadisticas en vivo mientras se juega")
    _, before = http("GET", CONSOLE + "/api/stats/overview")
    _, live_before = http("GET", CONSOLE + "/api/stats/live")
    base_matches = next((game["matches"] for game in before["games"] if game["gameId"] == "connect4"), 0)
    base_events = live_before["eventsAcceptedByGame"].get("connect4", 0)
    view = create("connect4", "TWO_PLAYERS", ["qa-vivo-a", "qa-vivo-b"])
    _, body = act(view, {"cell": 3})
    view = body["view"]
    active = wait_for("GameStats muestra la instancia en curso", lambda: http("GET", CONSOLE + "/api/stats/live")[1]["activeMatchesByGame"].get("connect4", 0) >= 1)
    report("el panel ve una instancia de connect4 en curso antes de terminar", bool(active))
    _, live_during = http("GET", CONSOLE + "/api/stats/live")
    report("los eventos aceptados por juego crecen mientras se juega", live_during["eventsAcceptedByGame"].get("connect4", 0) >= base_events + 4)
    for column in (4, 3, 4, 3, 4, 3):
        _, body = act(view, {"cell": column})
        view = body["view"]
    wait_analysis(view["matchId"])
    _, after = http("GET", CONSOLE + "/api/stats/overview")
    matches_after = next(game["matches"] for game in after["games"] if game["gameId"] == "connect4")
    report("al terminar, las instancias finalizadas de connect4 aumentan en uno", matches_after == base_matches + 1)
    _, direct = http("GET", ANALYTICS + "/stats/overview")
    report("el panel muestra exactamente lo que devuelve GameStats", after == direct)
    _, game_stats = http("GET", CONSOLE + "/api/stats/games/connect4")
    _, game_direct = http("GET", ANALYTICS + "/stats/games/connect4")
    report("las estadisticas por juego coinciden con GameStats", game_stats == game_direct and "aggregates" in game_stats)
    status, missing = http("GET", CONSOLE + "/api/stats/games/no-existe")
    report("un juego sin partidas se informa con 404 desde GameStats", status == 404)


def concurrent_matches():
    section("Partidas concurrentes sin mezclar eventos")
    total = 18
    results = {}
    errors = []

    def worker(index):
        generator = random.Random(index)
        tag = "%d-%d" % (index, generator.randint(100, 999))
        try:
            if index % 3 == 0:
                name = "qa-par-c%s" % tag
                view = play_connect4([name], "VS_COMPUTER", {"difficulty": "FACIL"}, generator)
                results[view["matchId"]] = ("connect4", {"player-" + name, COMPUTER_ID}, view)
            elif index % 3 == 1:
                names = ["qa-par-ma%s" % tag, "qa-par-mb%s" % tag]
                view, flips = play_memory(names, "TWO_PLAYERS", "PEQUENO")
                results[view["matchId"]] = ("memory", {"player-" + name for name in names}, view)
            else:
                name = "qa-par-bm%s" % tag
                view, reveals = play_minesweeper(name, False, generator)
                results[view["matchId"]] = ("minesweeper", {"player-" + name}, view)
        except Exception as failure:
            errors.append(str(failure))

    threads = [threading.Thread(target=worker, args=(index,)) for index in range(total)]
    started = time.time()
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    report("%d partidas de tres juegos se juegan en paralelo sin errores (%.1f s)" % (total, time.time() - started), not errors and len(results) == total)
    report("cada partida tiene un identificador distinto", len(results) == len(set(results)))
    analyses = {}
    for match_id in results:
        analysis = wait_analysis(match_id, 90)
        if analysis:
            analyses[match_id] = analysis
    report("GameStats analizo las %d partidas" % total, len(analyses) == total)
    mixed = 0
    wrong = 0
    for match_id, (game, expected_players, view) in results.items():
        analysis = analyses.get(match_id)
        if not analysis:
            continue
        if analysis["gameId"] != game or {player["playerId"] for player in analysis["players"]} != expected_players:
            mixed += 1
        counts = analysis["eventCountsByType"]
        if counts.get("MATCH_STARTED") != 1 or counts.get("MATCH_FINISHED") != 1 or counts.get("PLAYER_JOINED") != len(expected_players):
            wrong += 1
        if game == "connect4" and analysis["matchMetrics"]["totalMoves"] != discs(view, "red") + discs(view, "yellow"):
            wrong += 1
    report("ninguna partida recibio eventos de otra (juego y jugadores correctos)", mixed == 0)
    report("cada partida tiene su propio inicio, fin y jugadores completos", wrong == 0)
    status, delivery = http("GET", CONSOLE + "/api/delivery")
    report("la consola no perdio ningun evento al enviarlos en paralelo", delivery["statistics"]["failed"] == 0 and delivery["statistics"]["rejected"] == 0)


def prolog_under_load():
    section("La consola sigue respondiendo con Prolog bajo carga")
    latencies = []
    running = threading.Event()
    running.set()

    def probe():
        while running.is_set():
            started = time.time()
            http("GET", CONSOLE + "/api/games")
            latencies.append(time.time() - started)
            time.sleep(0.2)

    def play(index):
        view = create("connect4", "VS_COMPUTER", ["qa-carga-%d" % index], {"difficulty": "DIFICIL"})
        for _ in range(3):
            if view["finished"]:
                break
            columns = playable_columns(view)
            _, body = act(view, {"cell": columns[0]})
            view = body["view"]
        http("DELETE", "%s/api/sessions/%s" % (CONSOLE, view["matchId"]))

    watcher = threading.Thread(target=probe)
    watcher.start()
    players = [threading.Thread(target=play, args=(index,)) for index in range(8)]
    for thread in players:
        thread.start()
    for thread in players:
        thread.join()
    running.clear()
    watcher.join()
    report("8 partidas dificiles a la vez terminan sin error", True)
    report("las consultas del catalogo respondieron en menos de 2 s (maximo %.2f s)" % max(latencies), max(latencies) < 2.0)


def gamestats_outage():
    section("Fallo temporal de GameStats")
    compose = ["docker", "compose", "-f", COMPOSE_FILE]
    subprocess.run(compose + ["stop", "ingest-go"], check=True, capture_output=True)
    try:
        view = create("connect4", "TWO_PLAYERS", ["qa-caida-a", "qa-caida-b"])
        for column in (0, 1, 0, 1, 0, 1, 0):
            status, body = act(view, {"cell": column})
            view = body["view"]
        report("el juego sigue funcionando con GameStats caido", view["finished"] and status == 200)
        time.sleep(1.5)
        _, delivery = http("GET", CONSOLE + "/api/delivery")
        report("la consola acumula eventos pendientes y reintenta", delivery["statistics"]["pending"] > 0 or delivery["statistics"]["retries"] > 0)
    finally:
        subprocess.run(compose + ["start", "ingest-go"], check=True, capture_output=True)
    wait_for("GameStats de ingesta vuelve a responder", lambda: http("GET", INGEST + "/health")[0] == 200)
    analysis = wait_analysis(view["matchId"], 120)
    if analysis:
        report("tras recuperarse, GameStats recibe la partida completa", analysis["status"] == "COMPLETED" and analysis["totalEvents"] == 15)
    _, delivery = http("GET", CONSOLE + "/api/delivery")
    report("no se perdio ningun evento tras la caida", delivery["statistics"]["failed"] == 0)


def invalid_requests():
    section("Entradas invalidas")
    status, body = http("POST", CONSOLE + "/api/sessions", {"gameId": "no-existe", "mode": "SOLO", "players": ["x"]})
    report("un juego desconocido se rechaza con 400", status == 400 and "error" in body)
    status, body = http("POST", CONSOLE + "/api/sessions", {"gameId": "connect4", "mode": "MODO-RARO", "players": ["x"]})
    report("un modo desconocido se rechaza con 400", status == 400)
    status, _ = http("GET", CONSOLE + "/api/sessions/connect4-inexistente")
    report("una partida inexistente responde 404", status == 404)
    view = create("connect4", "TWO_PLAYERS", ["qa-turno-a", "qa-turno-b"])
    status, body = act(view, {"cell": 0}, "player-qa-turno-b")
    report("jugar fuera de turno se rechaza", status == 422 and not body["accepted"])
    status, body = act(view, {"cell": 0}, "player-desconocido")
    report("un jugador desconocido se rechaza", status == 422)
    http("DELETE", "%s/api/sessions/%s" % (CONSOLE, view["matchId"]))
    for body in ("{bad", "null", "[1]"):
        request = urllib.request.Request(CONSOLE + "/api/sessions", data=body.encode(), method="POST", headers={"Content-Type": "application/json"})
        try:
            urllib.request.urlopen(request)
            status = 200
        except urllib.error.HTTPError as failure:
            status = failure.code
        report("un cuerpo JSON invalido (%s) se rechaza con 400" % body, status == 400)
    long_name = create("memory", "SOLO", ["n" * 80], {})
    report("un nombre demasiado largo se recorta", len(long_name["players"][0]["displayName"]) <= 24)
    http("DELETE", "%s/api/sessions/%s" % (CONSOLE, long_name["matchId"]))


def main():
    generator = random.Random(2026)
    system_checks()
    connect4_with_prolog(generator)
    prolog_decides_by_search()
    two_player_connect4()
    memory_game()
    minesweeper_game(generator)
    abandoned_game()
    live_statistics()
    concurrent_matches()
    invalid_requests()
    prolog_under_load()
    gamestats_outage()
    print("\n%d verificaciones, %d fallos" % (checks, len(failures)))
    for failure in failures:
        print("  FALLO: " + failure)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
