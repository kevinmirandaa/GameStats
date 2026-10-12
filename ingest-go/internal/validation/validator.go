package validation

import (
	"bytes"
	"encoding/json"
	"fmt"
	"sort"
	"time"

	"gamestats/ingest/internal/event"
)

const emptyDataObject = "{}"

var knownFields = map[string]struct{}{
	"schemaVersion": {}, "eventId": {}, "timestamp": {}, "gameId": {}, "gameVersion": {},
	"matchId": {}, "playerId": {}, "type": {}, "action": {}, "data": {},
}

var requiredTextFields = []string{"schemaVersion", "eventId", "timestamp", "gameId", "gameVersion", "matchId", "type"}

type Result struct {
	Event   event.Event
	Reasons []string
}

func (result Result) IsValid() bool {
	return len(result.Reasons) == 0
}

func Validate(rawEvent []byte) Result {
	fields, structureReason := decodeFields(rawEvent)
	if structureReason != "" {
		return Result{Reasons: []string{structureReason}}
	}

	reasons := unknownFieldReasons(fields)
	texts, textReasons := readTextFields(fields)
	reasons = append(reasons, textReasons...)

	candidate := event.Event{
		SchemaVersion: texts["schemaVersion"],
		EventId:       texts["eventId"],
		GameId:        texts["gameId"],
		GameVersion:   texts["gameVersion"],
		MatchId:       texts["matchId"],
		PlayerId:      texts["playerId"],
		Type:          event.Type(texts["type"]),
		Action:        texts["action"],
	}

	reasons = append(reasons, schemaVersionReasons(candidate)...)
	timestamp, timestampReasons := parseTimestamp(texts["timestamp"])
	reasons = append(reasons, timestampReasons...)
	candidate.Timestamp = timestamp
	reasons = append(reasons, typeDependentReasons(candidate)...)

	data, dataReasons := readData(fields)
	reasons = append(reasons, dataReasons...)
	candidate.Data = data

	return Result{Event: candidate, Reasons: reasons}
}

func decodeFields(rawEvent []byte) (map[string]json.RawMessage, string) {
	trimmed := bytes.TrimSpace(rawEvent)
	if len(trimmed) == 0 || trimmed[0] != '{' {
		return nil, "payload is not a JSON object"
	}
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(trimmed, &fields); err != nil {
		return nil, "payload is not valid JSON"
	}
	return fields, ""
}

func unknownFieldReasons(fields map[string]json.RawMessage) []string {
	var names []string
	for name := range fields {
		if _, known := knownFields[name]; !known {
			names = append(names, name)
		}
	}
	sort.Strings(names)
	reasons := make([]string, 0, len(names))
	for _, name := range names {
		reasons = append(reasons, fmt.Sprintf("unknown field %q, extra information belongs inside data", name))
	}
	return reasons
}

func readTextFields(fields map[string]json.RawMessage) (map[string]string, []string) {
	texts := make(map[string]string)
	var reasons []string
	for _, name := range requiredTextFields {
		text, reason := readText(fields, name, true)
		if reason != "" {
			reasons = append(reasons, reason)
			continue
		}
		texts[name] = text
	}
	for _, name := range []string{"playerId", "action"} {
		text, reason := readText(fields, name, false)
		if reason != "" {
			reasons = append(reasons, reason)
			continue
		}
		texts[name] = text
	}
	return texts, reasons
}

func readText(fields map[string]json.RawMessage, name string, required bool) (string, string) {
	raw, present := fields[name]
	if !present {
		if required {
			return "", fmt.Sprintf("%s is required", name)
		}
		return "", ""
	}
	var text string
	if err := json.Unmarshal(raw, &text); err != nil {
		return "", fmt.Sprintf("%s must be a string", name)
	}
	if text == "" {
		return "", fmt.Sprintf("%s must not be empty", name)
	}
	return text, ""
}

func schemaVersionReasons(candidate event.Event) []string {
	if candidate.SchemaVersion != "" && candidate.SchemaVersion != event.SupportedSchemaVersion {
		return []string{fmt.Sprintf("schemaVersion %q is not supported, expected %q", candidate.SchemaVersion, event.SupportedSchemaVersion)}
	}
	return nil
}

func parseTimestamp(text string) (time.Time, []string) {
	if text == "" {
		return time.Time{}, nil
	}
	parsed, err := time.Parse(time.RFC3339, text)
	if err != nil {
		return time.Time{}, []string{"timestamp must be an RFC 3339 date and time"}
	}
	return parsed.UTC(), nil
}

func typeDependentReasons(candidate event.Event) []string {
	if candidate.Type == "" {
		return nil
	}
	if !candidate.Type.IsKnown() {
		return []string{fmt.Sprintf("type %q is not one of %v", candidate.Type, event.AllTypes())}
	}
	var reasons []string
	if candidate.Type.RequiresPlayer() && candidate.PlayerId == "" {
		reasons = append(reasons, fmt.Sprintf("playerId is required when type is %s", candidate.Type))
	}
	if candidate.Type.RequiresAction() && candidate.Action == "" {
		reasons = append(reasons, fmt.Sprintf("action is required when type is %s", candidate.Type))
	}
	return reasons
}

func readData(fields map[string]json.RawMessage) (json.RawMessage, []string) {
	raw, present := fields["data"]
	if !present {
		return json.RawMessage(emptyDataObject), nil
	}
	trimmed := bytes.TrimSpace(raw)
	if len(trimmed) == 0 || trimmed[0] != '{' {
		return nil, []string{"data must be a JSON object"}
	}
	return trimmed, nil
}
