package runner

import (
	"context"
	"fmt"
	"math/rand"
	"sync"
	"time"

	"gamestats/simulator/internal/games"
	"gamestats/simulator/internal/scenario"
	"gamestats/simulator/internal/sender"
)

type Settings struct {
	Seed            int64
	RunId           string
	StartTime       time.Time
	MatchesPerGame  int
	PlayersPerMatch int
	Pacing          sender.Pacing
	Parallel        int
}

type Summary struct {
	Matches  int
	Sent     int64
	Accepted int64
	Rejected int64
	Failed   int64
}

type job struct {
	generator games.GameMatchGenerator
	index     int
}

func Run(ctx context.Context, settings Settings, generators []games.GameMatchGenerator, chosen scenario.Scenario, target *sender.Sender) Summary {
	matchesPerGame := settings.MatchesPerGame
	if matchesPerGame == 0 {
		matchesPerGame = chosen.DefaultMatchesPerGame
	}

	jobs := make(chan job)
	tally := &sender.Tally{}
	var workers sync.WaitGroup
	for worker := 0; worker < settings.Parallel; worker++ {
		workers.Add(1)
		go func() {
			defer workers.Done()
			for pending := range jobs {
				playMatch(ctx, settings, chosen, pending, target, tally)
			}
		}()
	}

	for index := 0; index < matchesPerGame; index++ {
		for _, generator := range generators {
			jobs <- job{generator: generator, index: index}
		}
	}
	close(jobs)
	workers.Wait()

	return Summary{
		Matches:  matchesPerGame * len(generators),
		Sent:     tally.Sent.Load(),
		Accepted: tally.Accepted.Load(),
		Rejected: tally.Rejected.Load(),
		Failed:   tally.Failed.Load(),
	}
}

func playMatch(ctx context.Context, settings Settings, chosen scenario.Scenario, pending job, target *sender.Sender, tally *sender.Tally) {
	random := rand.New(rand.NewSource(seedFor(settings.Seed, pending.generator.GameId(), pending.index)))
	variation := chosen.VariationFor(pending.index)
	plan := games.MatchPlan{
		MatchId:   fmt.Sprintf("match-%s-%s-%03d", pending.generator.GameId(), settings.RunId, pending.index+1),
		PlayerIds: playersFor(pending.generator, settings.PlayersPerMatch, random),
		StartTime: settings.StartTime.Add(time.Duration(pending.index) * time.Minute),
		Storyline: variation.Storyline,
	}
	payloads := scenario.Apply(pending.generator.Generate(plan, random), variation, random)
	target.Send(ctx, payloads, settings.Pacing, tally)
}

func playersFor(generator games.GameMatchGenerator, count int, random *rand.Rand) []string {
	pool := games.PlayerPool(generator)
	random.Shuffle(len(pool), func(first, second int) { pool[first], pool[second] = pool[second], pool[first] })
	return pool[:count]
}

func seedFor(seed int64, gameId string, matchIndex int) int64 {
	const mixingFactor = 31
	derived := seed*mixingFactor + int64(matchIndex)
	for _, character := range gameId {
		derived = derived*mixingFactor + int64(character)
	}
	return derived
}
