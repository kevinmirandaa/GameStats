package games

import (
	"fmt"
	"sort"
	"time"

	"gamestats/simulator/internal/event"
)

type timeline struct {
	gameId      string
	gameVersion string
	matchId     string
	events      []event.Event
}

func newTimeline(gameId, gameVersion, matchId string) *timeline {
	return &timeline{gameId: gameId, gameVersion: gameVersion, matchId: matchId}
}

func (recorded *timeline) record(at time.Time, eventType event.Type, playerId, action string, data map[string]any) {
	recorded.events = append(recorded.events, event.Event{
		SchemaVersion: event.SchemaVersion,
		Timestamp:     at.UTC(),
		GameId:        recorded.gameId,
		GameVersion:   recorded.gameVersion,
		MatchId:       recorded.matchId,
		PlayerId:      playerId,
		Type:          eventType,
		Action:        action,
		Data:          data,
	})
}

func (recorded *timeline) lastTimestamp() time.Time {
	latest := recorded.events[0].Timestamp
	for _, candidate := range recorded.events {
		if candidate.Timestamp.After(latest) {
			latest = candidate.Timestamp
		}
	}
	return latest
}

func (recorded *timeline) ordered() []event.Event {
	sort.SliceStable(recorded.events, func(first, second int) bool {
		return recorded.events[first].Timestamp.Before(recorded.events[second].Timestamp)
	})
	for position := range recorded.events {
		recorded.events[position].EventId = fmt.Sprintf("evt-%s-%04d", recorded.matchId, position+1)
	}
	return recorded.events
}
