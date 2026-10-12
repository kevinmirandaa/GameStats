package ingestion_test

import (
	"context"
	"testing"
	"time"

	"gamestats/ingest/internal/deduplication"
	"gamestats/ingest/internal/event"
	"gamestats/ingest/internal/ingestion"
	"gamestats/ingest/internal/matchregistry"
	"gamestats/ingest/internal/metrics"
	"gamestats/ingest/internal/rejectedlog"
)

type silentDispatcher struct{}

func (silentDispatcher) Dispatch(context.Context, event.MatchBatch) error { return nil }

func newService(t *testing.T, inboxCapacity int) (*ingestion.Service, *matchregistry.Registry) {
	t.Helper()
	counters := &metrics.Counters{}
	registry := matchregistry.New(silentDispatcher{}, counters, matchregistry.Settings{
		IdleTimeout: time.Hour, FinishGrace: time.Hour, DispatchTimeout: time.Second, InboxCapacity: inboxCapacity,
	})
	rejections, err := rejectedlog.Open("", 10)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = registry.Shutdown(context.Background()) })
	return ingestion.NewService(registry, deduplication.NewEventIdRegistry(), rejections, counters), registry
}

func rawEvent(eventId string) []byte {
	return []byte(`{"schemaVersion":"1.0","eventId":"` + eventId + `","timestamp":"2026-10-12T18:00:00Z","gameId":"g","gameVersion":"1","matchId":"m1","type":"MATCH_STARTED"}`)
}

func TestEventRefusedBecauseOfShutdownCanBeSentAgain(t *testing.T) {
	service, registry := newService(t, 8)
	if err := registry.Shutdown(context.Background()); err != nil {
		t.Fatal(err)
	}
	first := service.Ingest(rawEvent("late"))
	second := service.Ingest(rawEvent("late"))
	if first.Outcome != ingestion.ShuttingDown || second.Outcome != ingestion.ShuttingDown {
		t.Fatalf("both attempts should report shutdown but got %v and %v", first.Outcome, second.Outcome)
	}
}

func TestEveryRejectionIsCountedAndLogged(t *testing.T) {
	service, _ := newService(t, 8)
	service.Ingest([]byte(`{"broken":true}`))
	service.Ingest(rawEvent("same"))
	service.Ingest(rawEvent("same"))

	snapshot := service.Counters().Snapshot(service.ActiveMatches())
	if snapshot.EventsReceived != 3 || snapshot.EventsRejected != 2 || snapshot.DuplicatesRejected != 1 || len(service.RecentRejections()) != 2 {
		t.Fatalf("unexpected counters %+v", snapshot)
	}
}
