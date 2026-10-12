package scenario_test

import (
	"encoding/json"
	"math/rand"
	"reflect"
	"sort"
	"testing"
	"time"

	"gamestats/simulator/internal/event"
	"gamestats/simulator/internal/games"
	"gamestats/simulator/internal/scenario"
	"gamestats/simulator/internal/testsupport"
)

func sampleMatch(t *testing.T) []event.Event {
	t.Helper()
	generator := games.Combat()
	plan := games.MatchPlan{
		MatchId:   "match-scenario-1",
		PlayerIds: games.PlayerPool(generator)[:4],
		StartTime: time.Date(2026, 10, 12, 18, 0, 0, 0, time.UTC),
		Storyline: games.Natural,
	}
	return generator.Generate(plan, rand.New(rand.NewSource(3)))
}

func decode(t *testing.T, payloads [][]byte) []event.Event {
	t.Helper()
	decoded := make([]event.Event, len(payloads))
	for position, payload := range payloads {
		if err := json.Unmarshal(payload, &decoded[position]); err != nil {
			t.Fatalf("payload %d is not decodable: %v", position, err)
		}
	}
	return decoded
}

func eventIds(events []event.Event) []string {
	ids := make([]string, len(events))
	for position, candidate := range events {
		ids[position] = candidate.EventId
	}
	sort.Strings(ids)
	return ids
}

func TestOrderedDeliveryKeepsEveryEventInOrder(t *testing.T) {
	events := sampleMatch(t)
	delivered := decode(t, scenario.Apply(events, scenario.Variation{}, rand.New(rand.NewSource(1))))
	if len(delivered) != len(events) {
		t.Fatalf("expected %d events but got %d", len(events), len(delivered))
	}
	for position := range events {
		if delivered[position].EventId != events[position].EventId {
			t.Fatal("ordered delivery changed the order")
		}
	}
}

func TestShuffledDeliveryKeepsTheSameEventsInADifferentOrder(t *testing.T) {
	events := sampleMatch(t)
	delivered := decode(t, scenario.Apply(events, scenario.Variation{Delivery: scenario.Shuffled}, rand.New(rand.NewSource(1))))
	if !reflect.DeepEqual(eventIds(delivered), eventIds(events)) {
		t.Fatal("shuffling must not add or remove events")
	}
	if reflect.DeepEqual(delivered[0].EventId, events[0].EventId) && reflect.DeepEqual(decode(t, scenario.Apply(events, scenario.Variation{}, rand.New(rand.NewSource(1)))), delivered) {
		t.Fatal("shuffled delivery should differ from the ordered one")
	}
}

func TestShuffledMatchFinishedNeverMovesMoreThanOnePositionEarly(t *testing.T) {
	for seed := int64(1); seed <= 50; seed++ {
		delivered := decode(t, scenario.Apply(sampleMatch(t), scenario.Variation{Delivery: scenario.Shuffled}, rand.New(rand.NewSource(seed))))
		for position, candidate := range delivered {
			if candidate.Type == event.MatchFinished && position < len(delivered)-2 {
				t.Fatalf("MATCH_FINISHED moved to position %d of %d", position, len(delivered))
			}
		}
	}
}

func TestDuplicatedDeliveryRepeatsEventsWithTheSameId(t *testing.T) {
	events := sampleMatch(t)
	delivered := decode(t, scenario.Apply(events, scenario.Variation{Delivery: scenario.Duplicated}, rand.New(rand.NewSource(1))))
	if len(delivered) <= len(events) {
		t.Fatal("some events should have been duplicated")
	}
	uniqueIds := make(map[string]bool)
	for _, candidate := range delivered {
		uniqueIds[candidate.EventId] = true
	}
	if len(uniqueIds) != len(events) {
		t.Fatal("duplicates must reuse existing event ids")
	}
}

func TestInvalidEventsAreInjectedAndRejectedByTheContractChecker(t *testing.T) {
	events := sampleMatch(t)
	payloads := scenario.Apply(events, scenario.Variation{Delivery: scenario.WithInvalidEvents}, rand.New(rand.NewSource(1)))
	invalid := 0
	for _, payload := range payloads {
		if len(testsupport.EnvelopeProblems(payload)) > 0 {
			invalid++
		}
	}
	if invalid == 0 || len(payloads) != len(events)+len(events)/10 {
		t.Fatalf("expected injected invalid events but found %d among %d payloads", invalid, len(payloads))
	}
}

func TestEveryKindOfCorruptionIsEventuallyProduced(t *testing.T) {
	seenProblems := make(map[string]bool)
	for seed := int64(1); seed <= 60; seed++ {
		for _, payload := range scenario.Apply(sampleMatch(t), scenario.Variation{Delivery: scenario.WithInvalidEvents}, rand.New(rand.NewSource(seed))) {
			for _, problem := range testsupport.EnvelopeProblems(payload) {
				seenProblems[problem] = true
			}
		}
	}
	if len(seenProblems) < 6 {
		t.Fatalf("expected at least six different kinds of invalid events but saw %v", seenProblems)
	}
}

func TestUnfinishedDeliveryDropsTheEndOfTheMatch(t *testing.T) {
	events := sampleMatch(t)
	delivered := decode(t, scenario.Apply(events, scenario.Variation{Delivery: scenario.Unfinished}, rand.New(rand.NewSource(1))))
	for _, candidate := range delivered {
		if candidate.Type == event.MatchFinished {
			t.Fatal("an unfinished match must not contain MATCH_FINISHED")
		}
	}
	if len(delivered) >= len(events) {
		t.Fatal("the tail of the match should be missing")
	}
}

func TestDisconnectAddsAPlayerLeftEventForAPlayerWhoJoined(t *testing.T) {
	delivered := decode(t, scenario.Apply(sampleMatch(t), scenario.Variation{Disconnect: true}, rand.New(rand.NewSource(1))))
	joined := make(map[string]bool)
	left := 0
	for _, candidate := range delivered {
		if candidate.Type == event.PlayerJoined {
			joined[candidate.PlayerId] = true
		}
		if candidate.Type == event.PlayerLeft {
			left++
			if !joined[candidate.PlayerId] {
				t.Fatal("the player who left never joined")
			}
		}
	}
	if left != 1 {
		t.Fatalf("expected exactly one PLAYER_LEFT but found %d", left)
	}
}

func TestEveryNamedScenarioCanBeFound(t *testing.T) {
	expected := []string{"normal", "comeback", "killstreak", "out-of-order", "duplicates", "invalid-events", "disconnect", "unfinished-match", "bulk", "mixed-parallel"}
	for _, name := range expected {
		if _, err := scenario.Find(name); err != nil {
			t.Errorf("scenario %s is missing: %v", name, err)
		}
	}
	if _, err := scenario.Find("nonexistent"); err == nil {
		t.Fatal("an unknown scenario must be reported")
	}
}

func TestMixedParallelCoversEveryStorylineAndEveryDeliveryKind(t *testing.T) {
	mixed, _ := scenario.Find("mixed-parallel")
	storylines := make(map[games.Storyline]bool)
	deliveries := make(map[scenario.Delivery]bool)
	disconnects := 0
	for index := 0; index < mixed.DefaultMatchesPerGame; index++ {
		variation := mixed.VariationFor(index)
		storylines[variation.Storyline] = true
		deliveries[variation.Delivery] = true
		if variation.Disconnect {
			disconnects++
		}
	}
	if len(storylines) != 4 || len(deliveries) < 4 || disconnects == 0 {
		t.Fatalf("mixed-parallel should cover everything but covered storylines=%d deliveries=%d disconnects=%d", len(storylines), len(deliveries), disconnects)
	}
}

func TestThePlayerWhoLeavesNeverActsAfterLeaving(t *testing.T) {
	for seed := int64(1); seed <= 40; seed++ {
		delivered := decode(t, scenario.Apply(sampleMatch(t), scenario.Variation{Disconnect: true}, rand.New(rand.NewSource(seed))))
		leftAt := make(map[string]time.Time)
		for _, candidate := range delivered {
			if candidate.Type == event.PlayerLeft {
				leftAt[candidate.PlayerId] = candidate.Timestamp
			}
		}
		for _, candidate := range delivered {
			if leftTime, left := leftAt[candidate.PlayerId]; left && candidate.Type == event.GameAction && candidate.Timestamp.After(leftTime) {
				t.Fatalf("seed %d: %s acted after leaving", seed, candidate.PlayerId)
			}
		}
	}
}
