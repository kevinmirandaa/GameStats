package configuration_test

import (
	"testing"
	"time"

	"gamestats/ingest/internal/configuration"
)

func lookupFrom(values map[string]string) func(string) (string, bool) {
	return func(name string) (string, bool) {
		value, present := values[name]
		return value, present
	}
}

func TestDefaultsAreUsedWhenNothingIsSet(t *testing.T) {
	loaded, err := configuration.Load(lookupFrom(nil))
	if err != nil {
		t.Fatal(err)
	}
	if loaded.Port != "8080" || loaded.AnalyticsURL != "http://localhost:8081" || loaded.MatchIdleTimeout != 30*time.Second {
		t.Fatalf("unexpected defaults: %+v", loaded)
	}
}

func TestEnvironmentValuesOverrideDefaults(t *testing.T) {
	loaded, err := configuration.Load(lookupFrom(map[string]string{
		"PORT":                       "9090",
		"ANALYTICS_URL":              "http://analytics:8081",
		"MATCH_IDLE_TIMEOUT_SECONDS": "5",
		"REJECTED_EVENTS_PATH":       "/data/rejected.log",
	}))
	if err != nil {
		t.Fatal(err)
	}
	if loaded.Port != "9090" || loaded.AnalyticsURL != "http://analytics:8081" || loaded.MatchIdleTimeout != 5*time.Second || loaded.RejectedEventsPath != "/data/rejected.log" {
		t.Fatalf("unexpected configuration: %+v", loaded)
	}
}

func TestInvalidNumbersAreReported(t *testing.T) {
	for _, badValue := range []string{"abc", "0", "-3"} {
		_, err := configuration.Load(lookupFrom(map[string]string{"MATCH_IDLE_TIMEOUT_SECONDS": badValue}))
		if err == nil {
			t.Errorf("value %q should be reported as an error", badValue)
		}
	}
}
