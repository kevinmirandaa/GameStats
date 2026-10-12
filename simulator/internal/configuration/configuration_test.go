package configuration_test

import (
	"errors"
	"flag"
	"testing"
	"time"

	"gamestats/simulator/internal/configuration"
)

var now = time.Date(2026, 10, 12, 18, 30, 45, 123, time.UTC)

func lookupFrom(values map[string]string) func(string) (string, bool) {
	return func(name string) (string, bool) {
		value, present := values[name]
		return value, present
	}
}

func TestDefaultsDescribeTheMixedParallelScenarioAgainstLocalhost(t *testing.T) {
	loaded, err := configuration.Load(nil, lookupFrom(nil), now)
	if err != nil {
		t.Fatal(err)
	}
	if loaded.TargetURL != "http://localhost:8080" || loaded.ScenarioName != "mixed-parallel" || loaded.Seed != 42 || loaded.PlayersPerMatch != 4 {
		t.Fatalf("unexpected defaults %+v", loaded)
	}
	if len(loaded.GameIds) != 2 || loaded.RunId != "1791829845" || !loaded.StartTime.Equal(time.Date(2026, 10, 12, 18, 30, 45, 0, time.UTC)) {
		t.Fatalf("unexpected defaults %+v", loaded)
	}
}

func TestEnvironmentValuesAreUsedAndFlagsOverrideThem(t *testing.T) {
	environment := lookupFrom(map[string]string{"SIMULATOR_TARGET_URL": "http://ingest-go:8080", "SIMULATOR_SEED": "7", "SIMULATOR_SCENARIO": "bulk"})
	loaded, err := configuration.Load([]string{"-seed", "9", "-games", "racing"}, environment, now)
	if err != nil {
		t.Fatal(err)
	}
	if loaded.TargetURL != "http://ingest-go:8080" || loaded.ScenarioName != "bulk" || loaded.Seed != 9 || len(loaded.GameIds) != 1 {
		t.Fatalf("unexpected settings %+v", loaded)
	}
}

func TestInvalidSettingsAreReported(t *testing.T) {
	invalidArguments := [][]string{
		{"-scenario", "nonexistent"},
		{"-games", "chess"},
		{"-players", "3"},
		{"-players", "9"},
		{"-batch-size", "0"},
		{"-parallel", "0"},
		{"-matches", "-1"},
		{"-games", " , "},
		{"-games", "racing,racing"},
		{"-run-id", "a b/c"},
		{"-events-per-second", "NaN"},
		{"-events-per-second", "0.000001"},
		{"-start-time", "yesterday"},
	}
	for _, arguments := range invalidArguments {
		if _, err := configuration.Load(arguments, lookupFrom(nil), now); err == nil {
			t.Errorf("arguments %v should be rejected", arguments)
		}
	}
}

func TestNonNumericEnvironmentValuesAreReported(t *testing.T) {
	if _, err := configuration.Load(nil, lookupFrom(map[string]string{"SIMULATOR_SEED": "abc"}), now); err == nil {
		t.Fatal("a non numeric seed must be reported")
	}
}

func TestAFixedStartTimeIsUsedExactlyAndConvertedToUniversalTime(t *testing.T) {
	loaded, err := configuration.Load([]string{"-start-time", "2026-10-12T12:00:00-06:00"}, lookupFrom(nil), now)
	if err != nil {
		t.Fatal(err)
	}
	if !loaded.StartTime.Equal(time.Date(2026, 10, 12, 18, 0, 0, 0, time.UTC)) || loaded.StartTime.Location() != time.UTC {
		t.Fatalf("unexpected start time %v", loaded.StartTime)
	}
}

func TestStartTimeCanComeFromTheEnvironment(t *testing.T) {
	loaded, err := configuration.Load(nil, lookupFrom(map[string]string{"SIMULATOR_START_TIME": "2026-01-01T00:00:00Z"}), now)
	if err != nil {
		t.Fatal(err)
	}
	if loaded.StartTime.Year() != 2026 || loaded.StartTime.Month() != time.January {
		t.Fatalf("unexpected start time %v", loaded.StartTime)
	}
}

func TestEnvironmentNumbersMustBeCompleteIntegers(t *testing.T) {
	if _, err := configuration.Load(nil, lookupFrom(map[string]string{"SIMULATOR_SEED": "12abc"}), now); err == nil {
		t.Fatal("a seed with trailing text must be reported")
	}
}

func TestHelpIsReportedAsHelpAndNotAsAFailure(t *testing.T) {
	if _, err := configuration.Load([]string{"-h"}, lookupFrom(nil), now); !errors.Is(err, flag.ErrHelp) {
		t.Fatalf("expected flag.ErrHelp but got %v", err)
	}
}
