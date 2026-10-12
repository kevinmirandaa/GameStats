package analyticsclient_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"gamestats/ingest/internal/analyticsclient"
	"gamestats/ingest/internal/event"
)

func clientFor(serverURL string, attempts int) *analyticsclient.Client {
	return analyticsclient.New(analyticsclient.Settings{
		BaseURL:         serverURL,
		MaximumAttempts: attempts,
		InitialBackoff:  5 * time.Millisecond,
		RequestTimeout:  time.Second,
	})
}

func sampleBatch() event.MatchBatch {
	return event.MatchBatch{
		SchemaVersion: event.SupportedSchemaVersion,
		GameId:        "racing",
		GameVersion:   "1.0",
		MatchId:       "match-1",
		Status:        event.MatchCompleted,
		Events:        []event.Event{{EventId: "e1", MatchId: "match-1", Type: event.MatchStarted, Data: []byte("{}")}},
	}
}

func TestBatchIsPostedAsJsonToTheAnalyzePath(t *testing.T) {
	var receivedPath, receivedContentType string
	var receivedBatch event.MatchBatch
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		receivedPath = request.URL.Path
		receivedContentType = request.Header.Get("Content-Type")
		if err := json.NewDecoder(request.Body).Decode(&receivedBatch); err != nil {
			t.Error(err)
		}
		response.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	if err := clientFor(server.URL, 3).Dispatch(context.Background(), sampleBatch()); err != nil {
		t.Fatal(err)
	}
	if receivedPath != "/analyze" || receivedContentType != "application/json" || receivedBatch.MatchId != "match-1" || receivedBatch.Status != event.MatchCompleted {
		t.Fatalf("unexpected request: %s %s %+v", receivedPath, receivedContentType, receivedBatch)
	}
}

func TestServerErrorsAreRetriedUntilSuccess(t *testing.T) {
	var calls atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		if calls.Add(1) < 3 {
			response.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		response.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	if err := clientFor(server.URL, 5).Dispatch(context.Background(), sampleBatch()); err != nil {
		t.Fatal(err)
	}
	if calls.Load() != 3 {
		t.Fatalf("expected 3 calls but got %d", calls.Load())
	}
}

func TestClientErrorsAreNotRetried(t *testing.T) {
	var calls atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		calls.Add(1)
		response.WriteHeader(http.StatusBadRequest)
	}))
	defer server.Close()

	if err := clientFor(server.URL, 5).Dispatch(context.Background(), sampleBatch()); err == nil {
		t.Fatal("a 400 answer should be reported as an error")
	}
	if calls.Load() != 1 {
		t.Fatalf("a 400 answer should not be retried but there were %d calls", calls.Load())
	}
}

func TestRetriesStopAfterTheMaximumAttempts(t *testing.T) {
	var calls atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		calls.Add(1)
		response.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	if err := clientFor(server.URL, 3).Dispatch(context.Background(), sampleBatch()); err == nil {
		t.Fatal("exhausted retries should be reported as an error")
	}
	if calls.Load() != 3 {
		t.Fatalf("expected exactly 3 attempts but got %d", calls.Load())
	}
}

func TestUnreachableServerIsRetriedAndReported(t *testing.T) {
	server := httptest.NewServer(http.NotFoundHandler())
	unreachableURL := server.URL
	server.Close()

	if err := clientFor(unreachableURL, 2).Dispatch(context.Background(), sampleBatch()); err == nil {
		t.Fatal("an unreachable server should be reported as an error")
	}
}

func TestCancelledContextStopsTheRetries(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if err := clientFor(server.URL, 10).Dispatch(ctx, sampleBatch()); err == nil {
		t.Fatal("a cancelled context should stop the delivery")
	}
}
