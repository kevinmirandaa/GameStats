package ingestion

import (
	"errors"

	"gamestats/ingest/internal/deduplication"
	"gamestats/ingest/internal/matchregistry"
	"gamestats/ingest/internal/metrics"
	"gamestats/ingest/internal/rejectedlog"
	"gamestats/ingest/internal/validation"
)

type Outcome int

const (
	Accepted Outcome = iota
	Invalid
	Duplicate
	MatchClosed
	Overloaded
	ShuttingDown
)

type Result struct {
	Outcome Outcome
	EventId string
	Reasons []string
}

type Service struct {
	registry   *matchregistry.Registry
	eventIds   *deduplication.EventIdRegistry
	rejections *rejectedlog.Log
	counters   *metrics.Counters
}

func NewService(registry *matchregistry.Registry, eventIds *deduplication.EventIdRegistry, rejections *rejectedlog.Log, counters *metrics.Counters) *Service {
	return &Service{registry: registry, eventIds: eventIds, rejections: rejections, counters: counters}
}

func (service *Service) Ingest(rawEvent []byte) Result {
	service.counters.EventReceived()

	checked := validation.Validate(rawEvent)
	if !checked.IsValid() {
		return service.reject(Invalid, checked.Event.EventId, checked.Reasons, rawEvent)
	}
	eventId := checked.Event.EventId
	if !service.eventIds.RegisterIfNew(eventId) {
		service.counters.DuplicateRejected()
		return service.reject(Duplicate, eventId, []string{"eventId was already received"}, rawEvent)
	}

	if err := service.registry.Submit(checked.Event); err != nil {
		return service.rejectSubmission(err, eventId, rawEvent)
	}
	service.counters.EventAccepted()
	service.counters.GameEventAccepted(checked.Event.GameId)
	return Result{Outcome: Accepted, EventId: eventId}
}

func (service *Service) ActiveMatches() int {
	return service.registry.ActiveMatches()
}

func (service *Service) RecentRejections() []rejectedlog.Entry {
	return service.rejections.Recent()
}

func (service *Service) Counters() *metrics.Counters {
	return service.counters
}

func (service *Service) rejectSubmission(err error, eventId string, rawEvent []byte) Result {
	switch {
	case errors.Is(err, matchregistry.ErrMatchClosed):
		return service.reject(MatchClosed, eventId, []string{err.Error()}, rawEvent)
	case errors.Is(err, matchregistry.ErrMatchInboxFull):
		service.eventIds.Forget(eventId)
		return service.reject(Overloaded, eventId, []string{err.Error()}, rawEvent)
	case errors.Is(err, matchregistry.ErrRegistryClosed):
		service.eventIds.Forget(eventId)
		return service.reject(ShuttingDown, eventId, []string{err.Error()}, rawEvent)
	default:
		return service.reject(Invalid, eventId, []string{err.Error()}, rawEvent)
	}
}

func (service *Service) reject(outcome Outcome, eventId string, reasons []string, rawEvent []byte) Result {
	service.counters.EventRejected()
	service.rejections.Record(eventId, reasons, rawEvent)
	return Result{Outcome: outcome, EventId: eventId, Reasons: reasons}
}
