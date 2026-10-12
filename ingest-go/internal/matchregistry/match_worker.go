package matchregistry

import (
	"context"
	"log/slog"
	"sort"
	"time"

	"gamestats/ingest/internal/event"
)

type matchWorker struct {
	registry    *Registry
	matchId     string
	gameId      string
	gameVersion string
	inbox       chan event.Event
}

func newMatchWorker(registry *Registry, firstEvent event.Event) *matchWorker {
	return &matchWorker{
		registry:    registry,
		matchId:     firstEvent.MatchId,
		gameId:      firstEvent.GameId,
		gameVersion: firstEvent.GameVersion,
		inbox:       make(chan event.Event, registry.settings.InboxCapacity),
	}
}

func (worker *matchWorker) run() {
	defer worker.registry.runningWorkers.Done()

	var collectedEvents []event.Event
	matchFinished := false
	closingTimer := time.NewTimer(worker.registry.settings.IdleTimeout)
	defer closingTimer.Stop()

	for {
		select {
		case incoming := <-worker.inbox:
			collectedEvents = append(collectedEvents, incoming)
			if incoming.Type == event.MatchFinished {
				matchFinished = true
			}
			restart(closingTimer, worker.waitBeforeClosing(matchFinished))
		case <-closingTimer.C:
			worker.closeMatch(collectedEvents, matchFinished)
			return
		case <-worker.registry.stopSignal:
			worker.closeMatch(collectedEvents, matchFinished)
			return
		}
	}
}

func (worker *matchWorker) waitBeforeClosing(matchFinished bool) time.Duration {
	if matchFinished {
		return worker.registry.settings.FinishGrace
	}
	return worker.registry.settings.IdleTimeout
}

func (worker *matchWorker) closeMatch(collectedEvents []event.Event, matchFinished bool) {
	worker.registry.retire(worker.matchId)
	collectedEvents = append(collectedEvents, drain(worker.inbox)...)

	batch := event.MatchBatch{
		SchemaVersion: event.SupportedSchemaVersion,
		GameId:        worker.gameId,
		GameVersion:   worker.gameVersion,
		MatchId:       worker.matchId,
		Status:        statusFor(matchFinished),
		Events:        sortedByTimestamp(collectedEvents),
	}
	worker.deliver(batch)
}

func (worker *matchWorker) deliver(batch event.MatchBatch) {
	ctx, cancel := context.WithTimeout(context.Background(), worker.registry.settings.DispatchTimeout)
	defer cancel()

	if err := worker.registry.dispatcher.Dispatch(ctx, batch); err != nil {
		worker.registry.counters.MatchFailed()
		slog.Error("match could not be delivered to analytics", "matchId", batch.MatchId, "error", err)
		return
	}
	worker.registry.counters.MatchSent()
	slog.Info("match delivered to analytics", "matchId", batch.MatchId, "status", batch.Status, "events", len(batch.Events))
}

func statusFor(matchFinished bool) event.MatchStatus {
	if matchFinished {
		return event.MatchCompleted
	}
	return event.MatchIncomplete
}

func sortedByTimestamp(events []event.Event) []event.Event {
	sorted := append([]event.Event(nil), events...)
	sort.SliceStable(sorted, func(first, second int) bool {
		return sorted[first].Timestamp.Before(sorted[second].Timestamp)
	})
	return sorted
}

func drain(inbox chan event.Event) []event.Event {
	var remaining []event.Event
	for {
		select {
		case pending := <-inbox:
			remaining = append(remaining, pending)
		default:
			return remaining
		}
	}
}

func restart(timer *time.Timer, duration time.Duration) {
	if !timer.Stop() {
		select {
		case <-timer.C:
		default:
		}
	}
	timer.Reset(duration)
}
