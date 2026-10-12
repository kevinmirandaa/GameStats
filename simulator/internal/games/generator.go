package games

import (
	"fmt"
	"math/rand"
	"time"

	"gamestats/simulator/internal/event"
)

type Storyline int

const (
	Natural Storyline = iota
	Comeback
	Streak
	Quiet
)

const PlayerPoolSize = 12

type MatchPlan struct {
	MatchId   string
	PlayerIds []string
	StartTime time.Time
	Storyline Storyline
}

type GameMatchGenerator interface {
	GameId() string
	PlayerPrefix() string
	Generate(plan MatchPlan, random *rand.Rand) []event.Event
}

func All() []GameMatchGenerator {
	return []GameMatchGenerator{Racing(), Combat()}
}

func Find(gameId string) (GameMatchGenerator, bool) {
	for _, generator := range All() {
		if generator.GameId() == gameId {
			return generator, true
		}
	}
	return nil, false
}

func PlayerPool(generator GameMatchGenerator) []string {
	pool := make([]string, PlayerPoolSize)
	for position := range pool {
		pool[position] = fmt.Sprintf("%s-%02d", generator.PlayerPrefix(), position+1)
	}
	return pool
}

func roundTo(value float64, decimals int) float64 {
	factor := 1.0
	for step := 0; step < decimals; step++ {
		factor *= 10
	}
	return float64(int(value*factor+0.5)) / factor
}
