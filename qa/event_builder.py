import argparse
import datetime
import json
import random
import sys

SCHEMA_VERSION = "1.0"
GAME_VERSION = "1.0"
TIMESTAMP_FORMAT = "%Y-%m-%dT%H:%M:%S.000Z"


def parse_arguments():
    parser = argparse.ArgumentParser()
    parser.add_argument("--game", required=True)
    parser.add_argument("--match", required=True)
    parser.add_argument("--players", required=True)
    parser.add_argument("--actions", required=True)
    parser.add_argument("--count", type=int, required=True)
    parser.add_argument("--start", default="2026-10-12T20:00:00")
    parser.add_argument("--no-finish", action="store_true")
    parser.add_argument("--order", choices=["natural", "reverse", "shuffle"], default="natural")
    parser.add_argument("--duplicate", type=int, default=0)
    parser.add_argument("--invalid", type=int, default=0)
    parser.add_argument("--seed", type=int, default=1)
    return parser.parse_args()


def timestamp_of(start, offset_seconds):
    moment = datetime.datetime.strptime(start, "%Y-%m-%dT%H:%M:%S") + datetime.timedelta(seconds=offset_seconds)
    return moment.strftime(TIMESTAMP_FORMAT)


def envelope(arguments, serial, event_type, player_id=None, action=None, data=None):
    event = {
        "schemaVersion": SCHEMA_VERSION,
        "eventId": "%s-evt-%04d" % (arguments.match, serial),
        "timestamp": timestamp_of(arguments.start, serial),
        "gameId": arguments.game,
        "gameVersion": GAME_VERSION,
        "matchId": arguments.match,
        "type": event_type,
        "data": data if data is not None else {},
    }
    if player_id is not None:
        event["playerId"] = player_id
    if action is not None:
        event["action"] = action
    return event


def natural_events(arguments):
    players = arguments.players.split(",")
    actions = arguments.actions.split(",")
    serial = 0
    events = [envelope(arguments, serial, "MATCH_STARTED", data={"source": "qa"})]
    for player in players:
        serial += 1
        events.append(envelope(arguments, serial, "PLAYER_JOINED", player_id=player))
    for index in range(arguments.count):
        serial += 1
        player = players[index % len(players)]
        action = actions[index % len(actions)]
        events.append(envelope(arguments, serial, "GAME_ACTION", player, action, {"step": index, "free": {"nested": [index, "x"]}}))
    if not arguments.no_finish:
        serial += 1
        events.append(envelope(arguments, serial, "MATCH_FINISHED", data={"winnerPlayerId": players[0], "ranking": players}))
    return events


def invalid_events(arguments, first_serial):
    return [
        {
            "schemaVersion": SCHEMA_VERSION,
            "eventId": "%s-bad-%04d" % (arguments.match, first_serial + index),
            "timestamp": timestamp_of(arguments.start, first_serial + index),
            "gameId": arguments.game,
            "gameVersion": GAME_VERSION,
            "matchId": arguments.match,
            "type": "NOT_A_TYPE",
        }
        for index in range(arguments.invalid)
    ]


def arranged(events, arguments):
    if arguments.order == "reverse":
        return list(reversed(events))
    if arguments.order == "shuffle":
        shuffled = list(events)
        random.Random(arguments.seed).shuffle(shuffled)
        return shuffled
    return events


def main():
    arguments = parse_arguments()
    events = natural_events(arguments)
    extra = events[: arguments.duplicate] + invalid_events(arguments, len(events))
    json.dump(arranged(events, arguments) + extra, sys.stdout)


main()
