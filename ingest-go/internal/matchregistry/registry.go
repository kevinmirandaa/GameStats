package matchregistry

import (
	"context"
	"errors"
	"sync"
	"time"

	"gamestats/ingest/internal/event"
	"gamestats/ingest/internal/metrics"
)

var (
	ErrMatchClosed    = errors.New("match is already closed")
	ErrMatchInboxFull = errors.New("match inbox is full")
	ErrGameMismatch   = errors.New("game of the event differs from the game of the match")
	ErrRegistryClosed = errors.New("registry is shutting down")
)

type Dispatcher interface {
	Dispatch(ctx context.Context, batch event.MatchBatch) error
}

type Settings struct {
	IdleTimeout     time.Duration
	FinishGrace     time.Duration
	DispatchTimeout time.Duration
	InboxCapacity   int
}

type Registry struct {
	mutex          sync.Mutex
	activeWorkers  map[string]*matchWorker
	closedMatches  map[string]struct{}
	shuttingDown   bool
	stopSignal     chan struct{}
	runningWorkers sync.WaitGroup
	dispatcher     Dispatcher
	counters       *metrics.Counters
	settings       Settings
}

func New(dispatcher Dispatcher, counters *metrics.Counters, settings Settings) *Registry {
	return &Registry{
		activeWorkers: make(map[string]*matchWorker),
		closedMatches: make(map[string]struct{}),
		stopSignal:    make(chan struct{}),
		dispatcher:    dispatcher,
		counters:      counters,
		settings:      settings,
	}
}

func (registry *Registry) Submit(incoming event.Event) error {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()

	if registry.shuttingDown {
		return ErrRegistryClosed
	}
	if _, closed := registry.closedMatches[incoming.MatchId]; closed {
		return ErrMatchClosed
	}
	worker, exists := registry.activeWorkers[incoming.MatchId]
	if !exists {
		worker = registry.startWorker(incoming)
	}
	if worker.gameId != incoming.GameId || worker.gameVersion != incoming.GameVersion {
		return ErrGameMismatch
	}
	select {
	case worker.inbox <- incoming:
		return nil
	default:
		return ErrMatchInboxFull
	}
}

func (registry *Registry) ActiveMatches() int {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()
	return len(registry.activeWorkers)
}

func (registry *Registry) Shutdown(ctx context.Context) error {
	registry.mutex.Lock()
	if !registry.shuttingDown {
		registry.shuttingDown = true
		close(registry.stopSignal)
	}
	registry.mutex.Unlock()

	allWorkersDone := make(chan struct{})
	go func() {
		registry.runningWorkers.Wait()
		close(allWorkersDone)
	}()

	select {
	case <-allWorkersDone:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

func (registry *Registry) startWorker(firstEvent event.Event) *matchWorker {
	worker := newMatchWorker(registry, firstEvent)
	registry.activeWorkers[firstEvent.MatchId] = worker
	registry.runningWorkers.Add(1)
	go worker.run()
	return worker
}

func (registry *Registry) retire(matchId string) {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()
	delete(registry.activeWorkers, matchId)
	registry.closedMatches[matchId] = struct{}{}
}
