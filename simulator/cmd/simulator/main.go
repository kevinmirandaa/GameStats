package main

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"gamestats/simulator/internal/configuration"
	"gamestats/simulator/internal/games"
	"gamestats/simulator/internal/runner"
	"gamestats/simulator/internal/scenario"
	"gamestats/simulator/internal/sender"
)

func main() {
	if err := run(); err != nil {
		if errors.Is(err, flag.ErrHelp) {
			return
		}
		slog.Error("simulator stopped with an error", "error", err)
		os.Exit(1)
	}
}

func run() error {
	settings, err := configuration.Load(os.Args[1:], os.LookupEnv, time.Now())
	if err != nil {
		return err
	}
	chosen, err := scenario.Find(settings.ScenarioName)
	if err != nil {
		return err
	}
	generators := generatorsFor(settings.GameIds)

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	target := sender.New(settings.TargetURL)
	slog.Info("waiting for the target", "url", settings.TargetURL)
	if err := target.WaitUntilHealthy(ctx, settings.WaitForTarget); err != nil {
		return err
	}

	slog.Info("simulation started", "scenario", chosen.Name, "games", settings.GameIds, "runId", settings.RunId, "seed", settings.Seed)
	summary := runner.Run(ctx, runner.Settings{
		Seed:            settings.Seed,
		RunId:           settings.RunId,
		StartTime:       settings.StartTime,
		MatchesPerGame:  settings.MatchesPerGame,
		PlayersPerMatch: settings.PlayersPerMatch,
		Pacing:          sender.Pacing{EventsPerSecond: settings.EventsPerSecond, BatchSize: settings.BatchSize},
		Parallel:        settings.Parallel,
	}, generators, chosen, target)

	fmt.Printf("matches=%d sent=%d accepted=%d rejected=%d failed=%d\n", summary.Matches, summary.Sent, summary.Accepted, summary.Rejected, summary.Failed)
	if summary.Failed > 0 {
		return fmt.Errorf("%d events could not be delivered", summary.Failed)
	}
	return nil
}

func generatorsFor(gameIds []string) []games.GameMatchGenerator {
	generators := make([]games.GameMatchGenerator, 0, len(gameIds))
	for _, gameId := range gameIds {
		if generator, known := games.Find(gameId); known {
			generators = append(generators, generator)
		}
	}
	return generators
}
