package event_test

import (
	"testing"

	"gamestats/ingest/internal/event"
)

func TestOnlyTheFiveGenericTypesAreKnown(t *testing.T) {
	for _, knownType := range event.AllTypes() {
		if !knownType.IsKnown() {
			t.Errorf("%s should be known", knownType)
		}
	}
	if len(event.AllTypes()) != 5 || event.Type("PLAYER_ELIMINATED").IsKnown() {
		t.Fatal("only the five generic types should exist")
	}
}

func TestPlayerAndActionRequirementsFollowTheContract(t *testing.T) {
	expectations := map[event.Type]struct{ player, action bool }{
		event.MatchStarted:  {false, false},
		event.MatchFinished: {false, false},
		event.PlayerJoined:  {true, false},
		event.PlayerLeft:    {true, false},
		event.GameAction:    {true, true},
	}
	for eventType, expected := range expectations {
		if eventType.RequiresPlayer() != expected.player || eventType.RequiresAction() != expected.action {
			t.Errorf("unexpected requirements for %s", eventType)
		}
	}
}
