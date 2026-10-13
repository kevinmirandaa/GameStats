package runner_test

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"gamestats/simulator/internal/event"
	"gamestats/simulator/internal/filedrop"
	"gamestats/simulator/internal/games"
	"gamestats/simulator/internal/runner"
	"gamestats/simulator/internal/scenario"
	"gamestats/simulator/internal/sender"
)

type recordingTarget struct {
	server       *httptest.Server
	mutex        sync.Mutex
	events       []event.Event
	payloads     atomic.Int64
	inFlight     atomic.Int32
	peakInFlight atomic.Int32
}

func startRecordingTarget(t *testing.T) *recordingTarget {
	t.Helper()
	target := &recordingTarget{}
	target.server = httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		current := target.inFlight.Add(1)
		defer target.inFlight.Add(-1)
		for {
			peak := target.peakInFlight.Load()
			if current <= peak || target.peakInFlight.CompareAndSwap(peak, current) {
				break
			}
		}
		time.Sleep(15 * time.Millisecond)
		body, _ := io.ReadAll(request.Body)
		var rawEvents []json.RawMessage
		if err := json.Unmarshal(body, &rawEvents); err != nil {
			response.WriteHeader(http.StatusBadRequest)
			return
		}
		target.payloads.Add(int64(len(rawEvents)))
		for _, raw := range rawEvents {
			var received event.Event
			if json.Unmarshal(raw, &received) == nil {
				target.mutex.Lock()
				target.events = append(target.events, received)
				target.mutex.Unlock()
			}
		}
		_, _ = response.Write([]byte(`{"accepted":` + itoa(len(rawEvents)) + `,"rejected":0,"errors":[]}`))
	}))
	t.Cleanup(target.server.Close)
	return target
}

func itoa(number int) string {
	encoded, _ := json.Marshal(number)
	return string(encoded)
}

func settings(parallel int) runner.Settings {
	return runner.Settings{
		Seed:            42,
		RunId:           "test",
		StartTime:       time.Date(2026, 10, 12, 18, 0, 0, 0, time.UTC),
		PlayersPerMatch: 4,
		Pacing:          sender.Pacing{BatchSize: 10},
		Parallel:        parallel,
	}
}

func TestMixedParallelSendsEveryMatchOfBothGamesWithUniqueIds(t *testing.T) {
	target := startRecordingTarget(t)
	mixed, _ := scenario.Find("mixed-parallel")

	summary := runner.Run(context.Background(), settings(8), games.All(), mixed, sender.New(target.server.URL))

	if summary.Matches != 24 || summary.Failed != 0 || summary.Sent != target.payloads.Load() {
		t.Fatalf("unexpected summary %+v with %d payloads received", summary, target.payloads.Load())
	}
	matchesByGame := make(map[string]map[string]bool)
	for _, received := range target.events {
		if received.MatchId == "" {
			continue
		}
		if matchesByGame[received.GameId] == nil {
			matchesByGame[received.GameId] = make(map[string]bool)
		}
		matchesByGame[received.GameId][received.MatchId] = true
	}
	if len(matchesByGame["racing"]) != 12 || len(matchesByGame["combat"]) != 12 {
		t.Fatalf("expected 12 matches per game but got racing=%d combat=%d", len(matchesByGame["racing"]), len(matchesByGame["combat"]))
	}
}

func TestMatchesAreSentConcurrently(t *testing.T) {
	target := startRecordingTarget(t)
	normal, _ := scenario.Find("normal")

	runner.Run(context.Background(), settings(8), games.All(), normal, sender.New(target.server.URL))

	if target.peakInFlight.Load() < 2 {
		t.Fatalf("matches were not sent concurrently, peak in flight was %d", target.peakInFlight.Load())
	}
}

func TestTheSameSeedSendsExactlyTheSameEventIdsAndTimestamps(t *testing.T) {
	collect := func() map[string]time.Time {
		target := startRecordingTarget(t)
		normal, _ := scenario.Find("normal")
		runner.Run(context.Background(), settings(4), games.All(), normal, sender.New(target.server.URL))
		collected := make(map[string]time.Time)
		for _, received := range target.events {
			collected[received.EventId] = received.Timestamp
		}
		return collected
	}
	first, second := collect(), collect()
	if len(first) == 0 || len(first) != len(second) {
		t.Fatalf("runs differ in size: %d against %d", len(first), len(second))
	}
	for eventId, timestamp := range first {
		if !second[eventId].Equal(timestamp) {
			t.Fatalf("event %s differs between runs", eventId)
		}
	}
}

func TestExplicitMatchCountOverridesTheScenarioDefault(t *testing.T) {
	target := startRecordingTarget(t)
	normal, _ := scenario.Find("normal")
	custom := settings(4)
	custom.MatchesPerGame = 2

	summary := runner.Run(context.Background(), custom, games.All(), normal, sender.New(target.server.URL))

	if summary.Matches != 4 {
		t.Fatalf("expected 4 matches but got %d", summary.Matches)
	}
}

func TestEveryMatchUsesPlayersFromTheGamePoolOnly(t *testing.T) {
	target := startRecordingTarget(t)
	mixed, _ := scenario.Find("mixed-parallel")
	runner.Run(context.Background(), settings(8), games.All(), mixed, sender.New(target.server.URL))

	pools := map[string]map[string]bool{}
	for _, generator := range games.All() {
		pools[generator.GameId()] = make(map[string]bool)
		for _, playerId := range games.PlayerPool(generator) {
			pools[generator.GameId()][playerId] = true
		}
	}
	for _, received := range target.events {
		if received.PlayerId != "" && !pools[received.GameId][received.PlayerId] {
			t.Fatalf("player %s does not belong to the %s pool", received.PlayerId, received.GameId)
		}
	}
}

func TestTheWholeScenarioCanBeWrittenAsFilesInsteadOfSentOverHttp(t *testing.T) {
	directory := t.TempDir()
	writer, err := filedrop.New(directory)
	if err != nil {
		t.Fatal(err)
	}
	mixed, _ := scenario.Find("mixed-parallel")

	summary := runner.Run(context.Background(), settings(8), games.All(), mixed, writer)

	files, err := os.ReadDir(directory)
	if err != nil || len(files) != 24 || summary.Failed != 0 {
		t.Fatalf("expected 24 files and no failures but got %d files, summary %+v (%v)", len(files), summary, err)
	}
	totalLines := 0
	for _, file := range files {
		content, err := os.ReadFile(filepath.Join(directory, file.Name()))
		if err != nil {
			t.Fatal(err)
		}
		for _, line := range strings.Split(strings.TrimSpace(string(content)), "\n") {
			if !json.Valid([]byte(line)) {
				t.Fatalf("%s contains a line that is not JSON: %s", file.Name(), line)
			}
			totalLines++
		}
	}
	if int64(totalLines) != summary.Sent {
		t.Fatalf("%d lines were written but %d were reported as sent", totalLines, summary.Sent)
	}
}
