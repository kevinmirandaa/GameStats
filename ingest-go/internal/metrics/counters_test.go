package metrics_test

import (
	"sync"
	"testing"

	"gamestats/ingest/internal/metrics"
)

func TestAcceptedEventsAreCountedPerGame(t *testing.T) {
	counters := &metrics.Counters{}
	counters.GameEventAccepted("racing")
	counters.GameEventAccepted("racing")
	counters.GameEventAccepted("combat")

	byGame := counters.Snapshot(0).EventsAcceptedByGame
	if byGame["racing"] != 2 || byGame["combat"] != 1 || len(byGame) != 2 {
		t.Fatalf("unexpected counts %v", byGame)
	}
}

func TestTheSnapshotIsACopyThatLaterEventsDoNotChange(t *testing.T) {
	counters := &metrics.Counters{}
	counters.GameEventAccepted("racing")
	snapshot := counters.Snapshot(0)
	counters.GameEventAccepted("racing")
	if snapshot.EventsAcceptedByGame["racing"] != 1 {
		t.Fatal("a snapshot must not change after it was taken")
	}
}

func TestAnEmptyCounterReportsAnEmptyMapAndNoFiles(t *testing.T) {
	snapshot := (&metrics.Counters{}).Snapshot(3)
	if snapshot.EventsAcceptedByGame == nil || len(snapshot.EventsAcceptedByGame) != 0 || snapshot.ActiveMatches != 3 || snapshot.FilesProcessed != 0 {
		t.Fatalf("unexpected snapshot %+v", snapshot)
	}
}

func TestConcurrentCountingDoesNotLoseEvents(t *testing.T) {
	counters := &metrics.Counters{}
	var group sync.WaitGroup
	for worker := 0; worker < 50; worker++ {
		group.Add(1)
		go func() {
			defer group.Done()
			for step := 0; step < 100; step++ {
				counters.GameEventAccepted("racing")
				counters.FileProcessed()
			}
		}()
	}
	group.Wait()
	snapshot := counters.Snapshot(0)
	if snapshot.EventsAcceptedByGame["racing"] != 5000 || snapshot.FilesProcessed != 5000 {
		t.Fatalf("unexpected totals %+v", snapshot)
	}
}
