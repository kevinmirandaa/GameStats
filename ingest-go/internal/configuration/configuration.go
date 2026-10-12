package configuration

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

type Configuration struct {
	Port                 string
	AnalyticsURL         string
	MatchIdleTimeout     time.Duration
	MatchFinishGrace     time.Duration
	RejectedEventsPath   string
	AnalyticsMaxAttempts int
	AnalyticsBackoff     time.Duration
	AnalyticsTimeout     time.Duration
	MatchInboxCapacity   int
}

const (
	defaultPort                    = "8080"
	defaultAnalyticsURL            = "http://localhost:8081"
	defaultIdleTimeoutSeconds      = 30
	defaultFinishGraceMilliseconds = 500
	defaultRejectedEventsPath      = "rejected-events.log"
	defaultAnalyticsMaxAttempts    = 5
	defaultAnalyticsBackoffMillis  = 200
	defaultAnalyticsTimeoutSeconds = 10
	defaultMatchInboxCapacity      = 1024
)

func Load(lookup func(string) (string, bool)) (Configuration, error) {
	reader := environmentReader{lookup: lookup}

	idleSeconds := reader.integer("MATCH_IDLE_TIMEOUT_SECONDS", defaultIdleTimeoutSeconds)
	graceMillis := reader.integer("MATCH_FINISH_GRACE_MILLISECONDS", defaultFinishGraceMilliseconds)
	maxAttempts := reader.integer("ANALYTICS_MAX_ATTEMPTS", defaultAnalyticsMaxAttempts)
	backoffMillis := reader.integer("ANALYTICS_INITIAL_BACKOFF_MILLISECONDS", defaultAnalyticsBackoffMillis)
	timeoutSeconds := reader.integer("ANALYTICS_REQUEST_TIMEOUT_SECONDS", defaultAnalyticsTimeoutSeconds)
	inboxCapacity := reader.integer("MATCH_INBOX_CAPACITY", defaultMatchInboxCapacity)

	if reader.err != nil {
		return Configuration{}, reader.err
	}
	return Configuration{
		Port:                 reader.text("PORT", defaultPort),
		AnalyticsURL:         reader.text("ANALYTICS_URL", defaultAnalyticsURL),
		MatchIdleTimeout:     time.Duration(idleSeconds) * time.Second,
		MatchFinishGrace:     time.Duration(graceMillis) * time.Millisecond,
		RejectedEventsPath:   reader.text("REJECTED_EVENTS_PATH", defaultRejectedEventsPath),
		AnalyticsMaxAttempts: maxAttempts,
		AnalyticsBackoff:     time.Duration(backoffMillis) * time.Millisecond,
		AnalyticsTimeout:     time.Duration(timeoutSeconds) * time.Second,
		MatchInboxCapacity:   inboxCapacity,
	}, nil
}

type environmentReader struct {
	lookup func(string) (string, bool)
	err    error
}

func (reader *environmentReader) text(name, fallback string) string {
	if value, present := reader.lookup(name); present && value != "" {
		return value
	}
	return fallback
}

func (reader *environmentReader) integer(name string, fallback int) int {
	value, present := reader.lookup(name)
	if !present || value == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil || parsed <= 0 {
		if reader.err == nil {
			reader.err = fmt.Errorf("%s must be a positive integer, got %q", name, value)
		}
		return fallback
	}
	return parsed
}

func FromEnvironment() (Configuration, error) {
	return Load(os.LookupEnv)
}
