import json
import os
import urllib.error
import urllib.request

CONSOLE = os.environ.get("CONSOLE_URL", "http://localhost:8100")


def http(method, url, body=None, timeout=30):
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            text = response.read().decode()
            return response.status, (json.loads(text) if text else None)
    except urllib.error.HTTPError as failure:
        text = failure.read().decode()
        try:
            return failure.code, json.loads(text) if text else None
        except ValueError:
            return failure.code, text
    except (urllib.error.URLError, ConnectionError, TimeoutError):
        return 0, None


def create(game, mode, players, options=None):
    status, view = http("POST", CONSOLE + "/api/sessions", {"gameId": game, "mode": mode, "players": players, "options": options or {}})
    if status != 201:
        raise RuntimeError("no se pudo crear la partida %s: %s %s" % (game, status, view))
    return view


def act(view, action, player_id=None):
    status, body = http("POST", "%s/api/sessions/%s/actions" % (CONSOLE, view["matchId"]), {"playerId": player_id or view["currentPlayerId"], "action": action})
    return status, body


def playable_columns(view):
    columns = view["columns"]
    return [index for index in range(columns) if view["cells"][index]["enabled"]]


def discs(view, style):
    return sum(1 for cell in view["cells"] if cell["style"].split(" ")[0] == style)


def play_connect4(names, mode, options, generator):
    view = create("connect4", mode, names, options)
    moves = 0
    while not view["finished"] and moves < 45:
        columns = playable_columns(view)
        if not columns:
            break
        status, body = act(view, {"cell": generator.choice(columns)})
        if status != 200:
            raise RuntimeError("jugada rechazada: %s" % body)
        view = body["view"]
        moves += 1
    return view


def play_memory(names, mode, size):
    view = create("memory", mode, names, {"size": size})
    known = {}
    flips = 0
    safety = 0
    while not view["finished"] and safety < 400:
        safety += 1
        hidden = [index for index, cell in enumerate(view["cells"]) if cell["enabled"]]
        if "continue" in view["extraActions"]:
            _, body = act(view, {"continue": True})
            view = body["view"]
            continue
        pair = next(((a, b) for a in hidden for b in hidden if a < b and a in known and b in known and known[a] == known[b]), None)
        if pair:
            first, second = pair
        else:
            unseen = [index for index in hidden if index not in known]
            first = unseen[0]
            _, body = act(view, {"cell": first})
            flips += 1
            view = body["view"]
            known[first] = view["cells"][first]["text"]
            partner = next((index for index in hidden if index != first and known.get(index) == known[first]), None)
            if partner is not None:
                second = partner
            else:
                remaining = [index for index in unseen if index != first]
                second = remaining[0]
        if pair:
            _, body = act(view, {"cell": first})
            flips += 1
            view = body["view"]
        _, body = act(view, {"cell": second})
        flips += 1
        view = body["view"]
        known[second] = view["cells"][second]["text"]
    return view, flips


def play_minesweeper(name, flag_first, generator):
    view = create("minesweeper", "SOLO", [name], {"difficulty": "PRINCIPIANTE"})
    reveals = 0
    _, body = act(view, {"cell": 40})
    view = body["view"]
    reveals += 1
    if flag_first:
        candidates = [index for index, cell in enumerate(view["cells"]) if cell["enabled"]]
        target = candidates[0]
        act(view, {"cell": target, "flag": True})
        _, body = act(view, {"cell": target, "flag": True})
        view = body["view"]
    order = list(range(len(view["cells"])))
    generator.shuffle(order)
    for index in order:
        if view["finished"]:
            break
        if view["cells"][index]["enabled"] and view["cells"][index]["style"] == "hidden":
            status, body = act(view, {"cell": index})
            if status == 200:
                reveals += 1
            view = body["view"]
    return view, reveals
