package configuration

import (
	"flag"
	"fmt"
	"math"
	"regexp"
	"strconv"
	"strings"
	"time"

	"gamestats/simulator/internal/games"
	"gamestats/simulator/internal/scenario"
)

type Settings struct {
	TargetURL       string
	ScenarioName    string
	MatchesPerGame  int
	PlayersPerMatch int
	Seed            int64
	EventsPerSecond float64
	RunId           string
	GameIds         []string
	BatchSize       int
	Parallel        int
	WaitForTarget   time.Duration
	StartTime       time.Time
	OutputDirectory string
}

const (
	minimumPlayers         = 4
	maximumPlayers         = 8
	minimumEventsPerSecond = 0.01
)

var runIdPattern = regexp.MustCompile(`^[A-Za-z0-9_-]+$`)

func Load(arguments []string, lookup func(string) (string, bool), now time.Time) (Settings, error) {
	environment := environmentReader{lookup: lookup}
	flags := flag.NewFlagSet("simulator", flag.ContinueOnError)

	targetURL := flags.String("target", environment.text("SIMULATOR_TARGET_URL", "http://localhost:8080"), "base URL of ingest-go")
	scenarioName := flags.String("scenario", environment.text("SIMULATOR_SCENARIO", "mixed-parallel"), "scenario: "+strings.Join(scenario.Names(), ", "))
	matches := flags.Int("matches", environment.integer("SIMULATOR_MATCHES_PER_GAME", 0), "matches per game, 0 uses the scenario default")
	players := flags.Int("players", environment.integer("SIMULATOR_PLAYERS_PER_MATCH", minimumPlayers), "players per match, 4 to 8")
	seed := flags.Int64("seed", int64(environment.integer("SIMULATOR_SEED", 42)), "random seed, the same seed generates the same matches")
	rate := flags.Float64("events-per-second", float64(environment.integer("SIMULATOR_EVENTS_PER_SECOND", 0)), "events per second for each match, 0 sends as fast as possible")
	runId := flags.String("run-id", environment.text("SIMULATOR_RUN_ID", fmt.Sprintf("%d", now.Unix())), "identifier included in match ids so repeated runs do not collide")
	gameList := flags.String("games", environment.text("SIMULATOR_GAMES", "racing,combat"), "comma separated game ids")
	batchSize := flags.Int("batch-size", environment.integer("SIMULATOR_BATCH_SIZE", 25), "events per request when not pacing")
	parallel := flags.Int("parallel", environment.integer("SIMULATOR_PARALLEL", 16), "matches sent at the same time")
	outputDirectory := flags.String("output-directory", environment.text("SIMULATOR_OUTPUT_DIRECTORY", ""), "write .jsonl files to this folder instead of sending HTTP requests")
	startText := flags.String("start-time", environment.text("SIMULATOR_START_TIME", ""), "RFC 3339 time of the first match, defaults to now, fix it to repeat a run exactly")
	waitSeconds := flags.Int("wait-seconds", environment.integer("SIMULATOR_WAIT_SECONDS", 30), "seconds to wait for the target to be healthy")

	if err := flags.Parse(arguments); err != nil {
		return Settings{}, err
	}
	startTime, err := parseStartTime(*startText, now)
	if err != nil {
		return Settings{}, err
	}

	settings := Settings{
		TargetURL:       *targetURL,
		ScenarioName:    *scenarioName,
		MatchesPerGame:  *matches,
		PlayersPerMatch: *players,
		Seed:            *seed,
		EventsPerSecond: *rate,
		RunId:           *runId,
		GameIds:         splitGameIds(*gameList),
		BatchSize:       *batchSize,
		Parallel:        *parallel,
		WaitForTarget:   time.Duration(*waitSeconds) * time.Second,
		StartTime:       startTime,
		OutputDirectory: *outputDirectory,
	}
	if environment.err != nil {
		return Settings{}, environment.err
	}
	return settings, settings.validate()
}

func parseStartTime(text string, now time.Time) (time.Time, error) {
	if text == "" {
		return now.UTC().Truncate(time.Second), nil
	}
	parsed, err := time.Parse(time.RFC3339, text)
	if err != nil {
		return time.Time{}, fmt.Errorf("start time must be RFC 3339, got %q", text)
	}
	return parsed.UTC(), nil
}

func (settings Settings) validate() error {
	if _, err := scenario.Find(settings.ScenarioName); err != nil {
		return err
	}
	for _, gameId := range settings.GameIds {
		if _, known := games.Find(gameId); !known {
			return fmt.Errorf("unknown game %q", gameId)
		}
	}
	switch {
	case !runIdPattern.MatchString(settings.RunId):
		return fmt.Errorf("run id may only contain letters, digits, hyphen and underscore, got %q", settings.RunId)
	case hasDuplicates(settings.GameIds):
		return fmt.Errorf("games must not repeat")
	case math.IsNaN(settings.EventsPerSecond) || math.IsInf(settings.EventsPerSecond, 0) ||
		(settings.EventsPerSecond != 0 && settings.EventsPerSecond < minimumEventsPerSecond):
		return fmt.Errorf("events per second must be 0 or at least %v", minimumEventsPerSecond)
	case len(settings.GameIds) == 0:
		return fmt.Errorf("at least one game is required")
	case settings.PlayersPerMatch < minimumPlayers || settings.PlayersPerMatch > maximumPlayers:
		return fmt.Errorf("players must be between %d and %d", minimumPlayers, maximumPlayers)
	case settings.MatchesPerGame < 0 || settings.BatchSize <= 0 || settings.Parallel <= 0 || settings.EventsPerSecond < 0:
		return fmt.Errorf("matches, batch size, parallel and events per second must not be negative or zero where a positive value is required")
	}
	return nil
}

func hasDuplicates(values []string) bool {
	seen := make(map[string]bool, len(values))
	for _, value := range values {
		if seen[value] {
			return true
		}
		seen[value] = true
	}
	return false
}

func splitGameIds(list string) []string {
	var gameIds []string
	for _, part := range strings.Split(list, ",") {
		if trimmed := strings.TrimSpace(part); trimmed != "" {
			gameIds = append(gameIds, trimmed)
		}
	}
	return gameIds
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
	if err != nil {
		if reader.err == nil {
			reader.err = fmt.Errorf("%s must be an integer, got %q", name, value)
		}
		return fallback
	}
	return parsed
}
