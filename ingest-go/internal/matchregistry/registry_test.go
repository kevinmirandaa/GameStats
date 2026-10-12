package matchregistry_test

import (
	"context"
	"fmt"
	"sync"
	"testing"
	"time"

	"gamestats/ingest/internal/event"
	"gamestats/ingest/internal/matchregistry"
	"gamestats/ingest/internal/metrics"
)

type collectingDispatcher struct {
	mutex   sync.Mutex
	batches map[string]event.MatchBatch
	arrived chan string
	failure error
}

func newCollectingDispatcher() *collectingDispatcher {
	return &collectingDispatcher{batches: make(map[string]event.MatchBatch), arrived: make(chan string, 1024)}
}

func (dispatcher *collectingDispatcher) Dispatch(_ context.Context, batch event.MatchBatch) error {
	dispatcher.mutex.Lock()
	defer dispatcher.mutex.Unlock()
	if dispatcher.failure != nil {
		return dispatcher.failure
	}
	dispatcher.batches[batch.MatchId] = batch
	dispatcher.arrived <- batch.MatchId
	return nil
}

func (dispatcher *collectingDispatcher) batchOf(matchId string) (event.MatchBatch, bool) {
	dispatcher.mutex.Lock()
	defer dispatcher.mutex.Unlock()
	batch, found := dispatcher.batches[matchId]
	return batch, found
}

func (dispatcher *collectingDispatcher) waitForBatches(t *testing.T, expected int) {
	t.Helper()
	deadline := time.After(5 * time.Second)
	for received := 0; received < expected; received++ {
		select {
		case <-dispatcher.arrived:
		case <-deadline:
			t.Fatalf("only %d of %d batches arrived", received, expected)
		}
	}
}

func fastSettings() matchregistry.Settings {
	return matchregistry.Settings{
		IdleTimeout:     300 * time.Millisecond,
		FinishGrace:     80 * time.Millisecond,
		DispatchTimeout: time.Second,
		InboxCapacity:   4096,
	}
}

var baseTime = time.Date(2026, 10, 12, 18, 0, 0, 0, time.UTC)

func eventAt(matchId, eventId string, offsetSeconds int, eventType event.Type) event.Event {
	return event.Event{
		SchemaVersion: event.SupportedSchemaVersion,
		EventId:       eventId,
		Timestamp:     baseTime.Add(time.Duration(offsetSeconds) * time.Second),
		GameId:        "racing",
		GameVersion:   "1.0",
		MatchId:       matchId,
		Type:          eventType,
		Data:          []byte("{}"),
	}
}

func TestFinishedMatchIsSentCompletedAndSortedByTimestamp(t *testing.T) {
	dispatcher := newCollectingDispatcher()
	registry := matchregistry.New(dispatcher, &metrics.Counters{}, fastSettings())

	submitAll(t, registry,
		eventAt("m1", "e3", 3, event.GameAction),
		eventAt("m1", "e1", 1, event.MatchStarted),
		eventAt("m1", "e4", 4, event.MatchFinished),
		eventAt("m1", "e2", 2, event.GameAction),
	)
	dispatcher.waitForBatches(t, 1)

	batch, _ := dispatcher.batchOf("m1")
	if batch.Status != event.MatchCompleted {
		t.Fatalf("status should be COMPLETED but was %s", batch.Status)
	}
	assertEventOrder(t, batch, "e1", "e2", "e3", "e4")
}

func TestEventArrivingJustAfterMatchFinishedIsStillIncluded(t *testing.T) {
	dispatcher := newCollectingDispatcher()
	registry := matchregistry.New(dispatcher, &metrics.Counters{}, fastSettings())

	submitAll(t, registry, eventAt("m1", "e1", 1, event.MatchStarted), eventAt("m1", "e3", 3, event.MatchFinished))
	time.Sleep(20 * time.Millisecond)
	submitAll(t, registry, eventAt("m1", "e2", 2, event.GameAction))
	dispatcher.waitForBatches(t, 1)

	batch, _ := dispatcher.batchOf("m1")
	assertEventOrder(t, batch, "e1", "e2", "e3")
}

func TestMatchWithoutFinishIsSentIncompleteAfterInactivity(t *testing.T) {
	dispatcher := newCollectingDispatcher()
	registry := matchregistry.New(dispatcher, &metrics.Counters{}, fastSettings())

	submitAll(t, registry, eventAt("m1", "e1", 1, event.MatchStarted), eventAt("m1", "e2", 2, event.GameAction))
	dispatcher.waitForBatches(t, 1)

	batch, _ := dispatcher.batchOf("m1")
	if batch.Status != event.MatchIncomplete {
		t.Fatalf("status should be INCOMPLETE but was %s", batch.Status)
	}
}

func TestEventsOfAClosedMatchAreRefused(t *testing.T) {
	dispatcher := newCollectingDispatcher()
	registry := matchregistry.New(dispatcher, &metrics.Counters{}, fastSettings())

	submitAll(t, registry, eventAt("m1", "e1", 1, event.MatchFinished))
	dispatcher.waitForBatches(t, 1)

	err := registry.Submit(eventAt("m1", "late", 5, event.GameAction))
	if err != matchregistry.ErrMatchClosed {
		t.Fatalf("expected ErrMatchClosed but got %v", err)
	}
}

func TestEventOfAnotherGameInTheSameMatchIsRefused(t *testing.T) {
	registry := matchregistry.New(newCollectingDispatcher(), &metrics.Counters{}, fastSettings())
	submitAll(t, registry, eventAt("m1", "e1", 1, event.MatchStarted))

	foreign := eventAt("m1", "e2", 2, event.GameAction)
	foreign.GameId = "combat"
	if err := registry.Submit(foreign); err != matchregistry.ErrGameMismatch {
		t.Fatalf("expected ErrGameMismatch but got %v", err)
	}
}

func TestManyMatchesAreProcessedConcurrentlyWithoutMixingEvents(t *testing.T) {
	const matches = 60
	const playersEventsPerMatch = 150
	dispatcher := newCollectingDispatcher()
	registry := matchregistry.New(dispatcher, &metrics.Counters{}, fastSettings())

	var senders sync.WaitGroup
	for matchNumber := 0; matchNumber < matches; matchNumber++ {
		senders.Add(1)
		go func(number int) {
			defer senders.Done()
			matchId := fmt.Sprintf("match-%03d", number)
			for position := playersEventsPerMatch; position >= 1; position-- {
				incoming := eventAt(matchId, fmt.Sprintf("%s-e%03d", matchId, position), position, event.GameAction)
				if err := registry.Submit(incoming); err != nil {
					t.Errorf("submit failed: %v", err)
					return
				}
			}
			finish := eventAt(matchId, matchId+"-finish", playersEventsPerMatch+1, event.MatchFinished)
			if err := registry.Submit(finish); err != nil {
				t.Errorf("submit failed: %v", err)
			}
		}(matchNumber)
	}
	senders.Wait()
	dispatcher.waitForBatches(t, matches)

	for matchNumber := 0; matchNumber < matches; matchNumber++ {
		matchId := fmt.Sprintf("match-%03d", matchNumber)
		batch, found := dispatcher.batchOf(matchId)
		if !found || len(batch.Events) != playersEventsPerMatch+1 {
			t.Fatalf("%s should have %d events but had %d", matchId, playersEventsPerMatch+1, len(batch.Events))
		}
		assertSortedAndOwnedBy(t, batch)
	}
}

func TestShutdownSendsActiveMatchesAsIncomplete(t *testing.T) {
	dispatcher := newCollectingDispatcher()
	settings := fastSettings()
	settings.IdleTimeout = time.Hour
	registry := matchregistry.New(dispatcher, &metrics.Counters{}, settings)

	submitAll(t, registry, eventAt("m1", "e1", 1, event.MatchStarted), eventAt("m2", "e2", 1, event.MatchStarted))
	if err := registry.Shutdown(context.Background()); err != nil {
		t.Fatal(err)
	}

	for _, matchId := range []string{"m1", "m2"} {
		batch, found := dispatcher.batchOf(matchId)
		if !found || batch.Status != event.MatchIncomplete {
			t.Fatalf("%s should have been flushed as INCOMPLETE", matchId)
		}
	}
	if err := registry.Submit(eventAt("m3", "e3", 1, event.MatchStarted)); err != matchregistry.ErrRegistryClosed {
		t.Fatalf("expected ErrRegistryClosed but got %v", err)
	}
}

func TestFailedDeliveryIsCounted(t *testing.T) {
	dispatcher := newCollectingDispatcher()
	dispatcher.failure = fmt.Errorf("analytics unavailable")
	counters := &metrics.Counters{}
	registry := matchregistry.New(dispatcher, counters, fastSettings())

	submitAll(t, registry, eventAt("m1", "e1", 1, event.MatchFinished))
	if err := registry.Shutdown(context.Background()); err != nil {
		t.Fatal(err)
	}
	if counters.Snapshot(0).MatchesFailed != 1 {
		t.Fatal("one failed delivery should have been counted")
	}
}

func submitAll(t *testing.T, registry *matchregistry.Registry, events ...event.Event) {
	t.Helper()
	for _, incoming := range events {
		if err := registry.Submit(incoming); err != nil {
			t.Fatalf("submit of %s failed: %v", incoming.EventId, err)
		}
	}
}

func assertEventOrder(t *testing.T, batch event.MatchBatch, expectedIds ...string) {
	t.Helper()
	if len(batch.Events) != len(expectedIds) {
		t.Fatalf("expected %d events but got %d", len(expectedIds), len(batch.Events))
	}
	for position, expectedId := range expectedIds {
		if batch.Events[position].EventId != expectedId {
			t.Fatalf("position %d should be %s but was %s", position, expectedId, batch.Events[position].EventId)
		}
	}
}

func assertSortedAndOwnedBy(t *testing.T, batch event.MatchBatch) {
	t.Helper()
	for position, incoming := range batch.Events {
		if incoming.MatchId != batch.MatchId {
			t.Fatalf("event %s belongs to %s but was sent in %s", incoming.EventId, incoming.MatchId, batch.MatchId)
		}
		if position > 0 && incoming.Timestamp.Before(batch.Events[position-1].Timestamp) {
			t.Fatalf("events of %s are not sorted by timestamp", batch.MatchId)
		}
	}
}
