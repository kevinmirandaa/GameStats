package analyticsclient

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"gamestats/ingest/internal/event"
)

const analyzePath = "/analyze"

type Settings struct {
	BaseURL         string
	MaximumAttempts int
	InitialBackoff  time.Duration
	RequestTimeout  time.Duration
}

type Client struct {
	analyzeURL string
	httpClient *http.Client
	settings   Settings
}

type unsuccessfulResponse struct {
	statusCode int
	body       string
}

func (response unsuccessfulResponse) Error() string {
	return fmt.Sprintf("analytics answered with status %d: %s", response.statusCode, response.body)
}

func (response unsuccessfulResponse) isRetryable() bool {
	return response.statusCode >= http.StatusInternalServerError || response.statusCode == http.StatusTooManyRequests
}

func New(settings Settings) *Client {
	return &Client{
		analyzeURL: strings.TrimRight(settings.BaseURL, "/") + analyzePath,
		httpClient: &http.Client{Timeout: settings.RequestTimeout},
		settings:   settings,
	}
}

func (client *Client) Dispatch(ctx context.Context, batch event.MatchBatch) error {
	body, err := json.Marshal(batch)
	if err != nil {
		return fmt.Errorf("batch could not be encoded: %w", err)
	}

	var lastError error
	for attempt := 1; attempt <= client.settings.MaximumAttempts; attempt++ {
		lastError = client.sendOnce(ctx, body)
		if lastError == nil {
			return nil
		}
		if !isRetryable(lastError) || attempt == client.settings.MaximumAttempts {
			break
		}
		if err := waitBeforeRetry(ctx, backoffForAttempt(client.settings.InitialBackoff, attempt)); err != nil {
			return err
		}
	}
	return fmt.Errorf("batch %s was not delivered: %w", batch.MatchId, lastError)
}

func (client *Client) sendOnce(ctx context.Context, body []byte) error {
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, client.analyzeURL, bytes.NewReader(body))
	if err != nil {
		return err
	}
	request.Header.Set("Content-Type", "application/json")

	response, err := client.httpClient.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()

	if response.StatusCode >= 200 && response.StatusCode < 300 {
		return nil
	}
	responseBody, _ := io.ReadAll(io.LimitReader(response.Body, 512))
	return unsuccessfulResponse{statusCode: response.StatusCode, body: string(responseBody)}
}

func isRetryable(err error) bool {
	var response unsuccessfulResponse
	if errors.As(err, &response) {
		return response.isRetryable()
	}
	return true
}

func backoffForAttempt(initialBackoff time.Duration, attempt int) time.Duration {
	return initialBackoff * time.Duration(1<<(attempt-1))
}

func waitBeforeRetry(ctx context.Context, duration time.Duration) error {
	timer := time.NewTimer(duration)
	defer timer.Stop()
	select {
	case <-timer.C:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}
