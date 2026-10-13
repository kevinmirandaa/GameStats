package sender

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"sync/atomic"
	"time"
)

const (
	healthPollInterval = 500 * time.Millisecond
	maximumAttempts    = 5
	initialBackoff     = 100 * time.Millisecond
	requestTimeout     = 15 * time.Second
)

type Tally struct {
	Sent     atomic.Int64
	Accepted atomic.Int64
	Rejected atomic.Int64
	Failed   atomic.Int64
}

type Pacing struct {
	EventsPerSecond float64
	BatchSize       int
}

type Sender struct {
	baseURL string
	client  *http.Client
}

func New(baseURL string) *Sender {
	return &Sender{baseURL: strings.TrimRight(baseURL, "/"), client: &http.Client{Timeout: requestTimeout}}
}

func (sender *Sender) WaitUntilHealthy(ctx context.Context, patience time.Duration) error {
	deadline := time.Now().Add(patience)
	for {
		if sender.isHealthy(ctx) {
			return nil
		}
		if time.Now().After(deadline) {
			return fmt.Errorf("%s did not become healthy within %s", sender.baseURL, patience)
		}
		if err := pause(ctx, healthPollInterval); err != nil {
			return err
		}
	}
}

func (sender *Sender) Deliver(ctx context.Context, _ string, payloads [][]byte, pacing Pacing, tally *Tally) {
	sender.Send(ctx, payloads, pacing, tally)
}

func (sender *Sender) Send(ctx context.Context, payloads [][]byte, pacing Pacing, tally *Tally) {
	if pacing.EventsPerSecond > 0 {
		sender.sendOneByOne(ctx, payloads, pacing.EventsPerSecond, tally)
		return
	}
	sender.sendInBatches(ctx, payloads, pacing.BatchSize, tally)
}

func (sender *Sender) isHealthy(ctx context.Context) bool {
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, sender.baseURL+"/health", nil)
	if err != nil {
		return false
	}
	response, err := sender.client.Do(request)
	if err != nil {
		return false
	}
	defer response.Body.Close()
	return response.StatusCode == http.StatusOK
}

func (sender *Sender) sendOneByOne(ctx context.Context, payloads [][]byte, eventsPerSecond float64, tally *Tally) {
	interval := time.Duration(float64(time.Second) / eventsPerSecond)
	for _, payload := range payloads {
		tally.Sent.Add(1)
		statusCode, _, err := sender.post(ctx, "/events", payload)
		switch {
		case err == nil && statusCode == http.StatusAccepted:
			tally.Accepted.Add(1)
		case err == nil && (statusCode == http.StatusBadRequest || statusCode == http.StatusConflict):
			tally.Rejected.Add(1)
		default:
			tally.Failed.Add(1)
		}
		if pause(ctx, interval) != nil {
			return
		}
	}
}

func (sender *Sender) sendInBatches(ctx context.Context, payloads [][]byte, batchSize int, tally *Tally) {
	for start := 0; start < len(payloads); start += batchSize {
		end := min(start+batchSize, len(payloads))
		chunk := payloads[start:end]
		tally.Sent.Add(int64(len(chunk)))

		statusCode, body, err := sender.post(ctx, "/events/batch", joinAsArray(chunk))
		var answer struct {
			Accepted int64 `json:"accepted"`
			Rejected int64 `json:"rejected"`
		}
		if err != nil || statusCode != http.StatusOK || json.Unmarshal(body, &answer) != nil {
			tally.Failed.Add(int64(len(chunk)))
			continue
		}
		tally.Accepted.Add(answer.Accepted)
		tally.Rejected.Add(answer.Rejected)
	}
}

func (sender *Sender) post(ctx context.Context, path string, body []byte) (int, []byte, error) {
	var lastError error
	for attempt := 1; attempt <= maximumAttempts; attempt++ {
		statusCode, responseBody, err := sender.postOnce(ctx, path, body)
		retryable := err != nil || statusCode >= http.StatusInternalServerError
		if !retryable {
			return statusCode, responseBody, nil
		}
		lastError = err
		if lastError == nil {
			lastError = fmt.Errorf("status %d", statusCode)
		}
		if pause(ctx, initialBackoff*time.Duration(1<<(attempt-1))) != nil {
			break
		}
	}
	return 0, nil, lastError
}

func (sender *Sender) postOnce(ctx context.Context, path string, body []byte) (int, []byte, error) {
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, sender.baseURL+path, bytes.NewReader(body))
	if err != nil {
		return 0, nil, err
	}
	request.Header.Set("Content-Type", "application/json")
	response, err := sender.client.Do(request)
	if err != nil {
		return 0, nil, err
	}
	defer response.Body.Close()
	responseBody, err := io.ReadAll(response.Body)
	return response.StatusCode, responseBody, err
}

func joinAsArray(payloads [][]byte) []byte {
	return append(append([]byte("["), bytes.Join(payloads, []byte(","))...), ']')
}

func pause(ctx context.Context, duration time.Duration) error {
	timer := time.NewTimer(duration)
	defer timer.Stop()
	select {
	case <-timer.C:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}
