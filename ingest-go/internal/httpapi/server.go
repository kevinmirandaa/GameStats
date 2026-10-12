package httpapi

import (
	"encoding/json"
	"io"
	"log/slog"
	"net/http"

	"gamestats/ingest/internal/ingestion"
)

const (
	maximumSingleEventBytes = 1 << 20
	maximumBatchBytes       = 32 << 20
)

type Server struct {
	service *ingestion.Service
}

type rejectionDetail struct {
	EventId string   `json:"eventId,omitempty"`
	Reasons []string `json:"reasons"`
}

type singleEventAnswer struct {
	EventId string            `json:"eventId,omitempty"`
	Status  string            `json:"status,omitempty"`
	Errors  []rejectionDetail `json:"errors,omitempty"`
}

type batchAnswer struct {
	Accepted int               `json:"accepted"`
	Rejected int               `json:"rejected"`
	Errors   []rejectionDetail `json:"errors"`
}

func NewHandler(service *ingestion.Service) http.Handler {
	server := &Server{service: service}
	routes := http.NewServeMux()
	routes.HandleFunc("POST /events", server.receiveEvent)
	routes.HandleFunc("POST /events/batch", server.receiveBatch)
	routes.HandleFunc("GET /health", server.reportHealth)
	routes.HandleFunc("GET /metrics", server.reportMetrics)
	routes.HandleFunc("GET /rejected", server.reportRejected)
	return routes
}

func (server *Server) receiveEvent(response http.ResponseWriter, request *http.Request) {
	body, err := readLimitedBody(response, request, maximumSingleEventBytes)
	if err != nil {
		writeJSON(response, http.StatusBadRequest, singleEventAnswer{Errors: []rejectionDetail{{Reasons: []string{"request body could not be read"}}}})
		return
	}
	result := server.service.Ingest(body)
	if result.Outcome == ingestion.Accepted {
		writeJSON(response, http.StatusAccepted, singleEventAnswer{EventId: result.EventId, Status: "ACCEPTED"})
		return
	}
	writeJSON(response, statusFor(result.Outcome), singleEventAnswer{Errors: []rejectionDetail{detailOf(result)}})
}

func (server *Server) receiveBatch(response http.ResponseWriter, request *http.Request) {
	body, err := readLimitedBody(response, request, maximumBatchBytes)
	if err != nil {
		writeJSON(response, http.StatusBadRequest, singleEventAnswer{Errors: []rejectionDetail{{Reasons: []string{"request body could not be read"}}}})
		return
	}
	var rawEvents []json.RawMessage
	if err := json.Unmarshal(body, &rawEvents); err != nil {
		writeJSON(response, http.StatusBadRequest, singleEventAnswer{Errors: []rejectionDetail{{Reasons: []string{"body must be a JSON array of events"}}}})
		return
	}

	answer := batchAnswer{Errors: []rejectionDetail{}}
	for _, rawEvent := range rawEvents {
		result := server.service.Ingest(rawEvent)
		if result.Outcome == ingestion.Accepted {
			answer.Accepted++
			continue
		}
		answer.Rejected++
		answer.Errors = append(answer.Errors, detailOf(result))
	}
	writeJSON(response, http.StatusOK, answer)
}

func (server *Server) reportHealth(response http.ResponseWriter, _ *http.Request) {
	writeJSON(response, http.StatusOK, map[string]string{"status": "UP"})
}

func (server *Server) reportMetrics(response http.ResponseWriter, _ *http.Request) {
	snapshot := server.service.Counters().Snapshot(server.service.ActiveMatches())
	writeJSON(response, http.StatusOK, snapshot)
}

func (server *Server) reportRejected(response http.ResponseWriter, _ *http.Request) {
	writeJSON(response, http.StatusOK, map[string]any{"rejected": server.service.RecentRejections()})
}

func statusFor(outcome ingestion.Outcome) int {
	switch outcome {
	case ingestion.Invalid:
		return http.StatusBadRequest
	case ingestion.Duplicate, ingestion.MatchClosed:
		return http.StatusConflict
	default:
		return http.StatusServiceUnavailable
	}
}

func detailOf(result ingestion.Result) rejectionDetail {
	return rejectionDetail{EventId: result.EventId, Reasons: result.Reasons}
}

func readLimitedBody(response http.ResponseWriter, request *http.Request, limit int64) ([]byte, error) {
	return io.ReadAll(http.MaxBytesReader(response, request.Body, limit))
}

func writeJSON(response http.ResponseWriter, statusCode int, payload any) {
	response.Header().Set("Content-Type", "application/json")
	response.WriteHeader(statusCode)
	if err := json.NewEncoder(response).Encode(payload); err != nil {
		slog.Error("response could not be written", "error", err)
	}
}
