package metrics

import "sync/atomic"

type Counters struct {
	eventsReceived     atomic.Int64
	eventsAccepted     atomic.Int64
	eventsRejected     atomic.Int64
	duplicatesRejected atomic.Int64
	matchesSent        atomic.Int64
	matchesFailed      atomic.Int64
}

type Snapshot struct {
	EventsReceived     int64 `json:"eventsReceived"`
	EventsAccepted     int64 `json:"eventsAccepted"`
	EventsRejected     int64 `json:"eventsRejected"`
	DuplicatesRejected int64 `json:"duplicatesRejected"`
	ActiveMatches      int   `json:"activeMatches"`
	MatchesSent        int64 `json:"matchesSent"`
	MatchesFailed      int64 `json:"matchesFailed"`
}

func (counters *Counters) EventReceived()     { counters.eventsReceived.Add(1) }
func (counters *Counters) EventAccepted()     { counters.eventsAccepted.Add(1) }
func (counters *Counters) EventRejected()     { counters.eventsRejected.Add(1) }
func (counters *Counters) DuplicateRejected() { counters.duplicatesRejected.Add(1) }
func (counters *Counters) MatchSent()         { counters.matchesSent.Add(1) }
func (counters *Counters) MatchFailed()       { counters.matchesFailed.Add(1) }

func (counters *Counters) Snapshot(activeMatches int) Snapshot {
	return Snapshot{
		EventsReceived:     counters.eventsReceived.Load(),
		EventsAccepted:     counters.eventsAccepted.Load(),
		EventsRejected:     counters.eventsRejected.Load(),
		DuplicatesRejected: counters.duplicatesRejected.Load(),
		ActiveMatches:      activeMatches,
		MatchesSent:        counters.matchesSent.Load(),
		MatchesFailed:      counters.matchesFailed.Load(),
	}
}
