package scenario

import (
	"fmt"
	"sort"
	"strings"

	"gamestats/simulator/internal/games"
)

type Delivery int

const (
	Ordered Delivery = iota
	Shuffled
	Duplicated
	WithInvalidEvents
	Unfinished
)

type Variation struct {
	Storyline  games.Storyline
	Delivery   Delivery
	Disconnect bool
}

type Scenario struct {
	Name                  string
	DefaultMatchesPerGame int
	VariationFor          func(matchIndex int) Variation
}

const (
	defaultMatches      = 4
	bulkMatches         = 200
	mixedMatches        = 12
	shuffledEvery       = 5
	shuffledRemainder   = 3
	duplicatedEvery     = 7
	duplicatedRemainder = 5
	invalidEvery        = 11
	invalidRemainder    = 10
	disconnectEvery     = 6
	disconnectRemainder = 2
)

var mixedStorylines = []games.Storyline{games.Comeback, games.Natural, games.Streak, games.Quiet}

func constant(variation Variation) func(int) Variation {
	return func(int) Variation { return variation }
}

func All() []Scenario {
	return []Scenario{
		{"normal", defaultMatches, constant(Variation{Storyline: games.Natural})},
		{"comeback", defaultMatches, constant(Variation{Storyline: games.Comeback})},
		{"killstreak", defaultMatches, constant(Variation{Storyline: games.Streak})},
		{"out-of-order", defaultMatches, constant(Variation{Storyline: games.Natural, Delivery: Shuffled})},
		{"duplicates", defaultMatches, constant(Variation{Storyline: games.Natural, Delivery: Duplicated})},
		{"invalid-events", defaultMatches, constant(Variation{Storyline: games.Natural, Delivery: WithInvalidEvents})},
		{"disconnect", defaultMatches, constant(Variation{Storyline: games.Natural, Disconnect: true})},
		{"unfinished-match", defaultMatches, constant(Variation{Storyline: games.Natural, Delivery: Unfinished})},
		{"bulk", bulkMatches, constant(Variation{Storyline: games.Natural})},
		{"mixed-parallel", mixedMatches, mixedVariation},
	}
}

func Find(name string) (Scenario, error) {
	for _, candidate := range All() {
		if candidate.Name == name {
			return candidate, nil
		}
	}
	return Scenario{}, fmt.Errorf("unknown scenario %q, choose one of: %s", name, strings.Join(Names(), ", "))
}

func Names() []string {
	names := make([]string, 0, len(All()))
	for _, candidate := range All() {
		names = append(names, candidate.Name)
	}
	sort.Strings(names)
	return names
}

func mixedVariation(matchIndex int) Variation {
	variation := Variation{Storyline: mixedStorylines[matchIndex%len(mixedStorylines)]}
	switch {
	case matchIndex%invalidEvery == invalidRemainder:
		variation.Delivery = WithInvalidEvents
	case matchIndex%duplicatedEvery == duplicatedRemainder:
		variation.Delivery = Duplicated
	case matchIndex%shuffledEvery == shuffledRemainder:
		variation.Delivery = Shuffled
	}
	variation.Disconnect = matchIndex%disconnectEvery == disconnectRemainder
	return variation
}
