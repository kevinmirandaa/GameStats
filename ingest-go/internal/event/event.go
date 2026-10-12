package event

import (
	"encoding/json"
	"time"
)

const SupportedSchemaVersion = "1.0"

type Type string

const (
	MatchStarted  Type = "MATCH_STARTED"
	MatchFinished Type = "MATCH_FINISHED"
	PlayerJoined  Type = "PLAYER_JOINED"
	PlayerLeft    Type = "PLAYER_LEFT"
	GameAction    Type = "GAME_ACTION"
)

var allTypes = []Type{MatchStarted, MatchFinished, PlayerJoined, PlayerLeft, GameAction}

func AllTypes() []Type {
	return append([]Type(nil), allTypes...)
}

func (eventType Type) IsKnown() bool {
	for _, knownType := range allTypes {
		if knownType == eventType {
			return true
		}
	}
	return false
}

func (eventType Type) RequiresPlayer() bool {
	return eventType == PlayerJoined || eventType == PlayerLeft || eventType == GameAction
}

func (eventType Type) RequiresAction() bool {
	return eventType == GameAction
}

type Event struct {
	SchemaVersion string          `json:"schemaVersion"`
	EventId       string          `json:"eventId"`
	Timestamp     time.Time       `json:"timestamp"`
	GameId        string          `json:"gameId"`
	GameVersion   string          `json:"gameVersion"`
	MatchId       string          `json:"matchId"`
	PlayerId      string          `json:"playerId,omitempty"`
	Type          Type            `json:"type"`
	Action        string          `json:"action,omitempty"`
	Data          json.RawMessage `json:"data"`
}

type MatchStatus string

const (
	MatchCompleted  MatchStatus = "COMPLETED"
	MatchIncomplete MatchStatus = "INCOMPLETE"
)

type MatchBatch struct {
	SchemaVersion string      `json:"schemaVersion"`
	GameId        string      `json:"gameId"`
	GameVersion   string      `json:"gameVersion"`
	MatchId       string      `json:"matchId"`
	Status        MatchStatus `json:"status"`
	Events        []Event     `json:"events"`
}
