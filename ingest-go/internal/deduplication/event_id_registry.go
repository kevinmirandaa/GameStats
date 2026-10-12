package deduplication

import "sync"

type EventIdRegistry struct {
	mutex   sync.Mutex
	seenIds map[string]struct{}
}

func NewEventIdRegistry() *EventIdRegistry {
	return &EventIdRegistry{seenIds: make(map[string]struct{})}
}

func (registry *EventIdRegistry) RegisterIfNew(eventId string) bool {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()
	if _, alreadySeen := registry.seenIds[eventId]; alreadySeen {
		return false
	}
	registry.seenIds[eventId] = struct{}{}
	return true
}

func (registry *EventIdRegistry) Forget(eventId string) {
	registry.mutex.Lock()
	defer registry.mutex.Unlock()
	delete(registry.seenIds, eventId)
}
