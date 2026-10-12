package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"gamestats/ingest/internal/analyticsclient"
	"gamestats/ingest/internal/configuration"
	"gamestats/ingest/internal/deduplication"
	"gamestats/ingest/internal/httpapi"
	"gamestats/ingest/internal/ingestion"
	"gamestats/ingest/internal/matchregistry"
	"gamestats/ingest/internal/metrics"
	"gamestats/ingest/internal/rejectedlog"
)

const (
	recentRejectionsKept   = 200
	shutdownGracePeriod    = 20 * time.Second
	dispatchTimeoutOnFlush = 30 * time.Second
	readHeaderTimeout      = 5 * time.Second
)

func main() {
	if err := run(); err != nil {
		slog.Error("ingest stopped with an error", "error", err)
		os.Exit(1)
	}
}

func run() error {
	settings, err := configuration.FromEnvironment()
	if err != nil {
		return err
	}
	rejections, err := rejectedlog.Open(settings.RejectedEventsPath, recentRejectionsKept)
	if err != nil {
		return err
	}
	defer rejections.Close()

	counters := &metrics.Counters{}
	analytics := analyticsclient.New(analyticsclient.Settings{
		BaseURL:         settings.AnalyticsURL,
		MaximumAttempts: settings.AnalyticsMaxAttempts,
		InitialBackoff:  settings.AnalyticsBackoff,
		RequestTimeout:  settings.AnalyticsTimeout,
	})
	registry := matchregistry.New(analytics, counters, matchregistry.Settings{
		IdleTimeout:     settings.MatchIdleTimeout,
		FinishGrace:     settings.MatchFinishGrace,
		DispatchTimeout: dispatchTimeoutOnFlush,
		InboxCapacity:   settings.MatchInboxCapacity,
	})
	service := ingestion.NewService(registry, deduplication.NewEventIdRegistry(), rejections, counters)

	httpServer := &http.Server{
		Addr:              ":" + settings.Port,
		Handler:           httpapi.NewHandler(service),
		ReadHeaderTimeout: readHeaderTimeout,
	}
	return serveUntilTerminated(httpServer, registry)
}

func serveUntilTerminated(httpServer *http.Server, registry *matchregistry.Registry) error {
	terminationSignal, stopListening := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stopListening()

	serverFailure := make(chan error, 1)
	go func() {
		slog.Info("ingest listening", "address", httpServer.Addr)
		serverFailure <- httpServer.ListenAndServe()
	}()

	select {
	case err := <-serverFailure:
		if errors.Is(err, http.ErrServerClosed) {
			return nil
		}
		return err
	case <-terminationSignal.Done():
		slog.Info("termination requested, closing active matches")
	}

	shutdownContext, cancel := context.WithTimeout(context.Background(), shutdownGracePeriod)
	defer cancel()
	if err := httpServer.Shutdown(shutdownContext); err != nil {
		return err
	}
	return registry.Shutdown(shutdownContext)
}
