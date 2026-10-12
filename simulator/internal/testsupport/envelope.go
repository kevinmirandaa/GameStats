package testsupport

import (
	"bytes"
	"encoding/json"
	"fmt"
	"time"
)

var knownFields = map[string]bool{
	"schemaVersion": true, "eventId": true, "timestamp": true, "gameId": true, "gameVersion": true,
	"matchId": true, "playerId": true, "type": true, "action": true, "data": true,
}

var knownTypes = map[string]bool{
	"MATCH_STARTED": true, "MATCH_FINISHED": true, "PLAYER_JOINED": true, "PLAYER_LEFT": true, "GAME_ACTION": true,
}

func EnvelopeProblems(payload []byte) []string {
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(bytes.TrimSpace(payload), &fields); err != nil {
		return []string{"not a JSON object"}
	}
	var problems []string
	for name := range fields {
		if !knownFields[name] {
			problems = append(problems, fmt.Sprintf("unknown field %s", name))
		}
	}
	text := func(name string) string {
		var value string
		if err := json.Unmarshal(fields[name], &value); err != nil {
			return ""
		}
		return value
	}
	for _, name := range []string{"schemaVersion", "eventId", "timestamp", "gameId", "gameVersion", "matchId", "type"} {
		if text(name) == "" {
			problems = append(problems, name+" missing or not text")
		}
	}
	if text("schemaVersion") != "" && text("schemaVersion") != "1.0" {
		problems = append(problems, "schemaVersion not supported")
	}
	if _, err := time.Parse(time.RFC3339, text("timestamp")); text("timestamp") != "" && err != nil {
		problems = append(problems, "timestamp not RFC 3339")
	}
	eventType := text("type")
	if eventType != "" && !knownTypes[eventType] {
		problems = append(problems, "type unknown")
	}
	needsPlayer := eventType == "PLAYER_JOINED" || eventType == "PLAYER_LEFT" || eventType == "GAME_ACTION"
	if needsPlayer && text("playerId") == "" {
		problems = append(problems, "playerId required")
	}
	if eventType == "GAME_ACTION" && text("action") == "" {
		problems = append(problems, "action required")
	}
	if rawData, present := fields["data"]; present && !bytes.HasPrefix(bytes.TrimSpace(rawData), []byte("{")) {
		problems = append(problems, "data must be an object")
	}
	return problems
}
