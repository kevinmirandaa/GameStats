package httpapi_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"gamestats/ingest/internal/analyticsclient"
	"gamestats/ingest/internal/deduplication"
	"gamestats/ingest/internal/event"
	"gamestats/ingest/internal/httpapi"
	"gamestats/ingest/internal/ingestion"
	"gamestats/ingest/internal/matchregistry"
	"gamestats/ingest/internal/metrics"
	"gamestats/ingest/internal/rejectedlog"
)

type fakeAnalytics struct {
	server  *httptest.Server
	mutex   sync.Mutex
	batches []event.MatchBatch
	arrived chan struct{}
}

func startFakeAnalytics(t *testing.T) *fakeAnalytics {
	t.Helper()
	analytics := &fakeAnalytics{arrived: make(chan struct{}, 64)}
	analytics.server = httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		var batch event.MatchBatch
		if request.URL.Path != "/analyze" || json.NewDecoder(request.Body).Decode(&batch) != nil {
			response.WriteHeader(http.StatusBadRequest)
			return
		}
		analytics.mutex.Lock()
		analytics.batches = append(analytics.batches, batch)
		analytics.mutex.Unlock()
		analytics.arrived <- struct{}{}
		response.WriteHeader(http.StatusOK)
	}))
	t.Cleanup(analytics.server.Close)
	return analytics
}

func (analytics *fakeAnalytics) waitForBatch(t *testing.T) event.MatchBatch {
	t.Helper()
	select {
	case <-analytics.arrived:
	case <-time.After(5 * time.Second):
		t.Fatal("analytics never received a batch")
	}
	analytics.mutex.Lock()
	defer analytics.mutex.Unlock()
	return analytics.batches[len(analytics.batches)-1]
}

type system struct {
	api       *httptest.Server
	analytics *fakeAnalytics
	registry  *matchregistry.Registry
}

func startSystem(t *testing.T) *system {
	t.Helper()
	analytics := startFakeAnalytics(t)
	counters := &metrics.Counters{}
	client := analyticsclient.New(analyticsclient.Settings{
		BaseURL: analytics.server.URL, MaximumAttempts: 3, InitialBackoff: 5 * time.Millisecond, RequestTimeout: time.Second,
	})
	registry := matchregistry.New(client, counters, matchregistry.Settings{
		IdleTimeout: 400 * time.Millisecond, FinishGrace: 60 * time.Millisecond, DispatchTimeout: time.Second, InboxCapacity: 256,
	})
	rejections, err := rejectedlog.Open("", 50)
	if err != nil {
		t.Fatal(err)
	}
	service := ingestion.NewService(registry, deduplication.NewEventIdRegistry(), rejections, counters)
	api := httptest.NewServer(httpapi.NewHandler(service))
	t.Cleanup(api.Close)
	t.Cleanup(func() { _ = registry.Shutdown(context.Background()) })
	return &system{api: api, analytics: analytics, registry: registry}
}

func (running *system) post(t *testing.T, path, body string) (int, map[string]any) {
	t.Helper()
	response, err := http.Post(running.api.URL+path, "application/json", strings.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	var decoded map[string]any
	_ = json.NewDecoder(response.Body).Decode(&decoded)
	return response.StatusCode, decoded
}

func (running *system) get(t *testing.T, path string) (int, map[string]any) {
	t.Helper()
	response, err := http.Get(running.api.URL + path)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	var decoded map[string]any
	_ = json.NewDecoder(response.Body).Decode(&decoded)
	return response.StatusCode, decoded
}

func exampleBody(t *testing.T, fileName string) string {
	t.Helper()
	raw, err := os.ReadFile(filepath.Join("../../../contracts/examples/valid", fileName))
	if err != nil {
		t.Fatal(err)
	}
	return string(raw)
}

func eventJSON(eventId, matchId, eventType, timestamp string) string {
	return `{"schemaVersion":"1.0","eventId":"` + eventId + `","timestamp":"` + timestamp + `","gameId":"racing","gameVersion":"1.0","matchId":"` + matchId + `","type":"` + eventType + `"}`
}

func TestValidEventIsAcceptedWithStatus202(t *testing.T) {
	running := startSystem(t)
	status, answer := running.post(t, "/events", exampleBody(t, "match-started-racing.json"))
	if status != http.StatusAccepted || answer["status"] != "ACCEPTED" {
		t.Fatalf("unexpected answer %d %v", status, answer)
	}
}

func TestInvalidEventIsRejectedWithStatus400AndReasons(t *testing.T) {
	running := startSystem(t)
	status, answer := running.post(t, "/events", `{"eventId":"e1"}`)
	errorsList, _ := answer["errors"].([]any)
	if status != http.StatusBadRequest || len(errorsList) != 1 {
		t.Fatalf("unexpected answer %d %v", status, answer)
	}
	reasons := errorsList[0].(map[string]any)["reasons"].([]any)
	if len(reasons) < 3 {
		t.Fatalf("all reasons should be listed but got %v", reasons)
	}
}

func TestDuplicateEventIsRejectedWithStatus409(t *testing.T) {
	running := startSystem(t)
	body := exampleBody(t, "lap-completed-racing.json")
	running.post(t, "/events", body)
	status, _ := running.post(t, "/events", body)
	if status != http.StatusConflict {
		t.Fatalf("duplicate should answer 409 but answered %d", status)
	}
}

func TestBatchReportsAcceptedAndRejectedCounts(t *testing.T) {
	running := startSystem(t)
	body := "[" + eventJSON("b1", "m-batch", "MATCH_STARTED", "2026-10-12T18:00:00Z") + `,{"broken":true},` + eventJSON("b1", "m-batch", "MATCH_STARTED", "2026-10-12T18:00:00Z") + "]"
	status, answer := running.post(t, "/events/batch", body)
	if status != http.StatusOK || answer["accepted"] != float64(1) || answer["rejected"] != float64(2) {
		t.Fatalf("unexpected batch answer %d %v", status, answer)
	}
}

func TestBatchThatIsNotAnArrayIsRejected(t *testing.T) {
	running := startSystem(t)
	status, _ := running.post(t, "/events/batch", `{"not":"an array"}`)
	if status != http.StatusBadRequest {
		t.Fatalf("expected 400 but got %d", status)
	}
}

func TestWholeMatchTravelsFromHttpToAnalyticsOrderedAndComplete(t *testing.T) {
	running := startSystem(t)
	body := "[" +
		eventJSON("e3", "m-flow", "MATCH_FINISHED", "2026-10-12T18:00:30Z") + "," +
		eventJSON("e1", "m-flow", "MATCH_STARTED", "2026-10-12T18:00:00Z") + "," +
		`{"schemaVersion":"1.0","eventId":"e2","timestamp":"2026-10-12T18:00:10Z","gameId":"racing","gameVersion":"1.0","matchId":"m-flow","playerId":"p1","type":"GAME_ACTION","action":"LAP_COMPLETED","data":{"lap":1}}` +
		"]"
	running.post(t, "/events/batch", body)

	batch := running.analytics.waitForBatch(t)
	if batch.MatchId != "m-flow" || batch.Status != event.MatchCompleted || len(batch.Events) != 3 {
		t.Fatalf("unexpected batch %+v", batch)
	}
	for position, expectedId := range []string{"e1", "e2", "e3"} {
		if batch.Events[position].EventId != expectedId {
			t.Fatalf("position %d should be %s but was %s", position, expectedId, batch.Events[position].EventId)
		}
	}
}

func TestUnknownGameFlowsThroughWithoutAnyConfiguration(t *testing.T) {
	running := startSystem(t)
	running.post(t, "/events", exampleBody(t, "third-game-match-started.json"))
	status, _ := running.post(t, "/events", exampleBody(t, "third-game-item-collected.json"))
	if status != http.StatusAccepted {
		t.Fatalf("events of an unknown game must be accepted but status was %d", status)
	}
	batch := running.analytics.waitForBatch(t)
	if batch.GameId != "treasure-hunt" || len(batch.Events) != 2 {
		t.Fatalf("unexpected batch %+v", batch)
	}
}

func TestHealthMetricsAndRejectedEndpoints(t *testing.T) {
	running := startSystem(t)
	running.post(t, "/events", `{"bad":1}`)
	running.post(t, "/events", exampleBody(t, "match-started-racing.json"))

	status, health := running.get(t, "/health")
	if status != http.StatusOK || health["status"] != "UP" {
		t.Fatalf("unexpected health %d %v", status, health)
	}
	_, metricsAnswer := running.get(t, "/metrics")
	if metricsAnswer["eventsReceived"] != float64(2) || metricsAnswer["eventsAccepted"] != float64(1) || metricsAnswer["eventsRejected"] != float64(1) || metricsAnswer["activeMatches"] != float64(1) {
		t.Fatalf("unexpected metrics %v", metricsAnswer)
	}
	_, rejected := running.get(t, "/rejected")
	if entries, _ := rejected["rejected"].([]any); len(entries) != 1 {
		t.Fatalf("unexpected rejected list %v", rejected)
	}
}

func TestEventForAClosedMatchIsRefusedWithStatus409(t *testing.T) {
	running := startSystem(t)
	running.post(t, "/events", eventJSON("c1", "m-closed", "MATCH_FINISHED", "2026-10-12T18:00:00Z"))
	running.analytics.waitForBatch(t)

	status, _ := running.post(t, "/events", eventJSON("c2", "m-closed", "MATCH_STARTED", "2026-10-12T18:00:01Z"))
	if status != http.StatusConflict {
		t.Fatalf("expected 409 but got %d", status)
	}
}
