package sender_test

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"gamestats/simulator/internal/sender"
)

func payloads(count int) [][]byte {
	result := make([][]byte, count)
	for position := range result {
		result[position] = []byte(`{"n":` + string(rune('0'+position%10)) + `}`)
	}
	return result
}

func TestBatchesAreSentInChunksAndCountedFromTheAnswer(t *testing.T) {
	var requests atomic.Int32
	var largestChunk atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		requests.Add(1)
		body, _ := io.ReadAll(request.Body)
		var events []json.RawMessage
		if err := json.Unmarshal(body, &events); err != nil || request.URL.Path != "/events/batch" {
			response.WriteHeader(http.StatusBadRequest)
			return
		}
		if int32(len(events)) > largestChunk.Load() {
			largestChunk.Store(int32(len(events)))
		}
		_, _ = response.Write([]byte(`{"accepted":` + string(rune('0'+len(events)-1)) + `,"rejected":1,"errors":[]}`))
	}))
	defer server.Close()

	tally := &sender.Tally{}
	sender.New(server.URL).Send(context.Background(), payloads(7), sender.Pacing{BatchSize: 3}, tally)

	if requests.Load() != 3 || largestChunk.Load() != 3 {
		t.Fatalf("expected 3 requests of at most 3 events but got %d requests and chunk %d", requests.Load(), largestChunk.Load())
	}
	if tally.Sent.Load() != 7 || tally.Accepted.Load() != 4 || tally.Rejected.Load() != 3 || tally.Failed.Load() != 0 {
		t.Fatalf("unexpected tally %d %d %d %d", tally.Sent.Load(), tally.Accepted.Load(), tally.Rejected.Load(), tally.Failed.Load())
	}
}

func TestPacedSendingPostsEachEventAndClassifiesTheStatus(t *testing.T) {
	var calls atomic.Int32
	statuses := []int{http.StatusAccepted, http.StatusBadRequest, http.StatusConflict, http.StatusAccepted}
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		position := int(calls.Add(1)) - 1
		if request.URL.Path != "/events" {
			response.WriteHeader(http.StatusNotFound)
			return
		}
		response.WriteHeader(statuses[position])
	}))
	defer server.Close()

	tally := &sender.Tally{}
	sender.New(server.URL).Send(context.Background(), payloads(4), sender.Pacing{EventsPerSecond: 1000}, tally)

	if tally.Accepted.Load() != 2 || tally.Rejected.Load() != 2 || tally.Failed.Load() != 0 || calls.Load() != 4 {
		t.Fatalf("unexpected tally accepted=%d rejected=%d failed=%d calls=%d", tally.Accepted.Load(), tally.Rejected.Load(), tally.Failed.Load(), calls.Load())
	}
}

func TestServiceUnavailableIsRetriedUntilItRecovers(t *testing.T) {
	var calls atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		if calls.Add(1) < 3 {
			response.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		response.WriteHeader(http.StatusAccepted)
	}))
	defer server.Close()

	tally := &sender.Tally{}
	sender.New(server.URL).Send(context.Background(), payloads(1), sender.Pacing{EventsPerSecond: 1000}, tally)

	if tally.Accepted.Load() != 1 || tally.Failed.Load() != 0 || calls.Load() != 3 {
		t.Fatalf("expected recovery after 3 calls but got accepted=%d failed=%d calls=%d", tally.Accepted.Load(), tally.Failed.Load(), calls.Load())
	}
}

func TestAnUnreachableTargetCountsEveryEventAsFailed(t *testing.T) {
	server := httptest.NewServer(http.NotFoundHandler())
	url := server.URL
	server.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	tally := &sender.Tally{}
	sender.New(url).Send(ctx, payloads(5), sender.Pacing{BatchSize: 5}, tally)

	if tally.Failed.Load() != 5 || tally.Accepted.Load() != 0 {
		t.Fatalf("expected 5 failures but got %d failed and %d accepted", tally.Failed.Load(), tally.Accepted.Load())
	}
}

func TestWaitUntilHealthyReturnsOnceTheTargetAnswers(t *testing.T) {
	var calls atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		if calls.Add(1) < 2 {
			response.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		response.WriteHeader(http.StatusOK)
	}))
	defer server.Close()

	if err := sender.New(server.URL).WaitUntilHealthy(context.Background(), 5*time.Second); err != nil {
		t.Fatal(err)
	}
}

func TestWaitUntilHealthyGivesUpAfterThePatienceRunsOut(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.WriteHeader(http.StatusServiceUnavailable)
	}))
	defer server.Close()

	if err := sender.New(server.URL).WaitUntilHealthy(context.Background(), 700*time.Millisecond); err == nil {
		t.Fatal("an unhealthy target must be reported")
	}
}
