import random
import sys
import threading
import time

from gameplay import CONSOLE, http, play_connect4, play_memory, play_minesweeper

WAIT_FOR_CONSOLE_SECONDS = 180
WAIT_FOR_STATISTICS_SECONDS = 90


def wait_for_console():
    deadline = time.time() + WAIT_FOR_CONSOLE_SECONDS
    while time.time() < deadline:
        status, health = http("GET", CONSOLE + "/api/health")
        if status == 200 and health and health["prologAvailable"]:
            return True
        time.sleep(1)
    return False


def plan():
    return [
        ("Conecta 4 contra Prolog (facil)", lambda g: play_connect4(["Ana"], "VS_COMPUTER", {"difficulty": "FACIL"}, g)),
        ("Conecta 4 contra Prolog (medio)", lambda g: play_connect4(["Luis"], "VS_COMPUTER", {"difficulty": "MEDIO"}, g)),
        ("Conecta 4 contra Prolog (medio)", lambda g: play_connect4(["Maria"], "VS_COMPUTER", {"difficulty": "MEDIO"}, g)),
        ("Conecta 4 dos jugadores", lambda g: play_connect4(["Carlos", "Sofia"], "TWO_PLAYERS", {}, g)),
        ("Memoria dos jugadores", lambda g: play_memory(["Ana", "Luis"], "TWO_PLAYERS", "PEQUENO")[0]),
        ("Memoria dos jugadores", lambda g: play_memory(["Maria", "Pedro"], "TWO_PLAYERS", "MEDIANO")[0]),
        ("Memoria un jugador", lambda g: play_memory(["Sofia"], "SOLO", "PEQUENO")[0]),
        ("Buscaminas", lambda g: play_minesweeper("Carlos", True, g)[0]),
        ("Buscaminas", lambda g: play_minesweeper("Sofia", False, g)[0]),
        ("Buscaminas", lambda g: play_minesweeper("Pedro", True, g)[0]),
    ]


def statistics_ready(expected):
    status, overview = http("GET", CONSOLE + "/api/stats/overview")
    return status == 200 and overview["totalMatches"] >= expected


def main():
    if not wait_for_console():
        print("La consola no respondio a tiempo")
        return 1
    status, baseline = http("GET", CONSOLE + "/api/stats/overview")
    already = baseline["totalMatches"] if status == 200 else 0
    games = plan()
    results = []

    def play(index, label, runner):
        generator = random.Random(index)
        try:
            view = runner(generator)
            results.append((label, view["status"]))
        except RuntimeError as failure:
            results.append((label, "error: %s" % failure))

    threads = [threading.Thread(target=play, args=(index, label, runner)) for index, (label, runner) in enumerate(games)]
    print("Jugando %d partidas en paralelo a traves de la consola" % len(games), flush=True)
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    for label, outcome in sorted(results):
        print("  %-36s %s" % (label, outcome), flush=True)
    deadline = time.time() + WAIT_FOR_STATISTICS_SECONDS
    while time.time() < deadline and not statistics_ready(already + len(games)):
        time.sleep(1)
    ready = statistics_ready(already + len(games))
    print("Estadisticas de GameStats %s" % ("disponibles" if ready else "todavia procesandose"), flush=True)
    return 0 if ready and all(not outcome.startswith("error") for _, outcome in results) else 1


if __name__ == "__main__":
    sys.exit(main())
