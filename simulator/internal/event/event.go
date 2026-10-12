package event

import "time"

const SchemaVersion = "1.0"

type Type string

const (
	MatchStarted  Type = "MATCH_STARTED"
	MatchFinished Type = "MATCH_FINISHED"
	PlayerJoined  Type = "PLAYER_JOINED"
	PlayerLeft    Type = "PLAYER_LEFT"
	GameAction    Type = "GAME_ACTION"
)

type Event struct {
	SchemaVersion string         `json:"schemaVersion"`
	EventId       string         `json:"eventId"`
	Timestamp     time.Time      `json:"timestamp"`
	GameId        string         `json:"gameId"`
	GameVersion   string         `json:"gameVersion"`
	MatchId       string         `json:"matchId"`
	PlayerId      string         `json:"playerId,omitempty"`
	Type          Type           `json:"type"`
	Action        string         `json:"action,omitempty"`
	Data          map[string]any `json:"data"`
}
