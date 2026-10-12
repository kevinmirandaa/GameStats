package games_test

import (
	"encoding/json"
	"fmt"
	"math/rand"
	"reflect"
	"sort"
	"testing"
	"time"

	"gamestats/simulator/internal/event"
	"gamestats/simulator/internal/games"
	"gamestats/simulator/internal/testsupport"
)

var startTime = time.Date(2026, 10, 12, 18, 0, 0, 0, time.UTC)

func playersFor(generator games.GameMatchGenerator, count int) []string {
	return games.PlayerPool(generator)[:count]
}

func generate(generator games.GameMatchGenerator, storyline games.Storyline, players int, seed int64) []event.Event {
	plan := games.MatchPlan{
		MatchId:   fmt.Sprintf("match-test-%d", seed),
		PlayerIds: playersFor(generator, players),
		StartTime: startTime,
		Storyline: storyline,
	}
	return generator.Generate(plan, rand.New(rand.NewSource(seed)))
}

func forEachCombination(t *testing.T, generator games.GameMatchGenerator, storyline games.Storyline, check func(t *testing.T, events []event.Event, players []string)) {
	t.Helper()
	for players := 4; players <= 8; players++ {
		for seed := int64(1); seed <= 25; seed++ {
			events := generate(generator, storyline, players, seed)
			check(t, events, playersFor(generator, players))
		}
	}
}

var allStorylines = []games.Storyline{games.Natural, games.Comeback, games.Streak, games.Quiet}

func TestEveryGeneratedMatchHasAValidWellFormedEnvelopeStream(t *testing.T) {
	for _, generator := range games.All() {
		for _, storyline := range allStorylines {
			forEachCombination(t, generator, storyline, func(t *testing.T, events []event.Event, players []string) {
				assertWellFormed(t, generator.GameId(), events, players)
			})
		}
	}
}

func assertWellFormed(t *testing.T, gameId string, events []event.Event, players []string) {
	t.Helper()
	seenIds := make(map[string]bool)
	for position, generated := range events {
		encoded, err := json.Marshal(generated)
		if err != nil {
			t.Fatal(err)
		}
		if problems := testsupport.EnvelopeProblems(encoded); len(problems) != 0 {
			t.Fatalf("%s event %d is invalid: %v", gameId, position, problems)
		}
		if seenIds[generated.EventId] {
			t.Fatalf("%s repeats event id %s", gameId, generated.EventId)
		}
		seenIds[generated.EventId] = true
		if position > 0 && generated.Timestamp.Before(events[position-1].Timestamp) {
			t.Fatalf("%s events are not ordered by timestamp", gameId)
		}
		if generated.PlayerId != "" && !contains(players, generated.PlayerId) {
			t.Fatalf("%s uses player %s outside the plan", gameId, generated.PlayerId)
		}
	}
	if events[0].Type != event.MatchStarted || events[len(events)-1].Type != event.MatchFinished {
		t.Fatalf("%s match must start with MATCH_STARTED and end with MATCH_FINISHED", gameId)
	}
	joined := 0
	for _, generated := range events {
		if generated.Type == event.PlayerJoined {
			joined++
		}
	}
	if joined != len(players) {
		t.Fatalf("%s: %d players joined but %d were planned", gameId, joined, len(players))
	}
}

func TestRacingPositionsAndRankingAreCoherentWithTheLapTimes(t *testing.T) {
	forEachCombination(t, games.Racing(), games.Natural, func(t *testing.T, events []event.Event, players []string) {
		lapCount := len(testsupport.LapPositions(events, players[0]))
		for lap := 0; lap < lapCount; lap++ {
			for _, driver := range players {
				cumulative := testsupport.CumulativeLapTimes(events, driver)[lap]
				fasterDrivers := 0
				for gridSlot, other := range players {
					otherCumulative := testsupport.CumulativeLapTimes(events, other)[lap]
					tiedAndAhead := otherCumulative == cumulative && gridSlot < indexOf(players, driver)
					if otherCumulative < cumulative || tiedAndAhead {
						fasterDrivers++
					}
				}
				if got := testsupport.LapPositions(events, driver)[lap]; got != fasterDrivers+1 {
					t.Fatalf("%s lap %d reports position %d but %d drivers were faster", driver, lap+1, got, fasterDrivers)
				}
			}
		}
		ranking := testsupport.Ranking(events)
		totals := func(driver string) float64 {
			cumulative := testsupport.CumulativeLapTimes(events, driver)
			return cumulative[len(cumulative)-1]
		}
		if !sort.SliceIsSorted(ranking, func(first, second int) bool { return totals(ranking[first]) < totals(ranking[second]) }) {
			t.Fatalf("ranking %v is not ordered by total lap time", ranking)
		}
		if ranking[0] != testsupport.Winner(events) {
			t.Fatal("the winner must be the first of the ranking")
		}
	})
}

func TestRacingComebackStorylineMakesTheWinnerFourthOrWorseBeforeWinning(t *testing.T) {
	forEachCombination(t, games.Racing(), games.Comeback, func(t *testing.T, events []event.Event, _ []string) {
		winner := testsupport.Winner(events)
		worst := 0
		for _, position := range testsupport.LapPositions(events, winner) {
			worst = max(worst, position)
		}
		if worst < 4 {
			t.Fatalf("the winner %s was never fourth or worse (worst position %d)", winner, worst)
		}
		if testsupport.Ranking(events)[0] != winner {
			t.Fatal("the comeback driver must win")
		}
		if testsupport.LongestImprovingRun(events, winner) < 3 {
			t.Fatal("the comeback driver should also improve lap after lap")
		}
	})
}

func TestRacingQuietStorylineProducesNoStreakAndNoComeback(t *testing.T) {
	forEachCombination(t, games.Racing(), games.Quiet, func(t *testing.T, events []event.Event, players []string) {
		for _, driver := range players {
			if run := testsupport.LongestImprovingRun(events, driver); run >= 3 {
				t.Fatalf("%s has an improving run of %d laps", driver, run)
			}
		}
		for _, position := range testsupport.LapPositions(events, testsupport.Winner(events)) {
			if position >= 4 {
				t.Fatalf("the winner dropped to position %d", position)
			}
		}
	})
}

func TestRacingStreakStorylineGivesTheWinnerAnImprovingRunWithoutAComeback(t *testing.T) {
	forEachCombination(t, games.Racing(), games.Streak, func(t *testing.T, events []event.Event, _ []string) {
		winner := testsupport.Winner(events)
		if testsupport.LongestImprovingRun(events, winner) < 3 {
			t.Fatal("the winner should have an improving run of at least three laps")
		}
		for _, position := range testsupport.LapPositions(events, winner) {
			if position >= 4 {
				t.Fatal("the streak storyline must not also be a comeback")
			}
		}
	})
}

func TestCombatStreakStorylineGivesSomeoneFourEliminationsInARow(t *testing.T) {
	forEachCombination(t, games.Combat(), games.Streak, func(t *testing.T, events []event.Event, _ []string) {
		if streak := testsupport.LongestKillStreak(events); streak < 4 {
			t.Fatalf("longest streak was %d", streak)
		}
	})
}

func TestCombatComebackStorylineMakesTheWinnerTrailByThree(t *testing.T) {
	forEachCombination(t, games.Combat(), games.Comeback, func(t *testing.T, events []event.Event, _ []string) {
		winner := testsupport.Winner(events)
		if deficit := testsupport.DeepestDeficit(events, winner); deficit < 3 {
			t.Fatalf("the winner %s only trailed by %d", winner, deficit)
		}
	})
}

func TestCombatQuietStorylineStaysBelowEveryThreshold(t *testing.T) {
	forEachCombination(t, games.Combat(), games.Quiet, func(t *testing.T, events []event.Event, _ []string) {
		if streak := testsupport.LongestKillStreak(events); streak >= 3 {
			t.Fatalf("quiet match has a streak of %d", streak)
		}
		if deficit := testsupport.DeepestDeficit(events, testsupport.Winner(events)); deficit >= 3 {
			t.Fatalf("quiet match winner trailed by %d", deficit)
		}
	})
}

func TestNobodyEliminatesThemselvesAndTheWinnerHasTheMostEliminations(t *testing.T) {
	for _, storyline := range allStorylines {
		forEachCombination(t, games.Combat(), storyline, func(t *testing.T, events []event.Event, _ []string) {
			scores := make(map[string]int)
			for _, elimination := range testsupport.Eliminations(events) {
				if elimination.PlayerId == testsupport.VictimOf(elimination) {
					t.Fatal("a player eliminated themselves")
				}
				scores[elimination.PlayerId]++
			}
			winner := testsupport.Winner(events)
			for player, score := range scores {
				if score > scores[winner] {
					t.Fatalf("%s has %d eliminations, more than the winner %s with %d", player, score, winner, scores[winner])
				}
			}
		})
	}
}

func TestTheSameSeedGeneratesExactlyTheSameMatch(t *testing.T) {
	for _, generator := range games.All() {
		first := generate(generator, games.Natural, 5, 7)
		second := generate(generator, games.Natural, 5, 7)
		if !reflect.DeepEqual(first, second) {
			t.Fatalf("%s is not reproducible", generator.GameId())
		}
		if reflect.DeepEqual(first, generate(generator, games.Natural, 5, 8)) {
			t.Fatalf("%s ignores the seed", generator.GameId())
		}
	}
}

func TestEveryRacingActionAppearsAcrossNaturalMatches(t *testing.T) {
	seenActions := make(map[string]bool)
	for seed := int64(1); seed <= 40; seed++ {
		for _, generated := range generate(games.Racing(), games.Natural, 6, seed) {
			seenActions[generated.Action] = true
		}
	}
	for _, action := range []string{"LAP_COMPLETED", "POSITION_CHANGED", "PENALTY_APPLIED", "PIT_STOP"} {
		if !seenActions[action] {
			t.Errorf("racing never generated %s", action)
		}
	}
}

func TestFindLocatesRegisteredGamesOnly(t *testing.T) {
	if _, found := games.Find("racing"); !found {
		t.Fatal("racing should be registered")
	}
	if _, found := games.Find("chess"); found {
		t.Fatal("chess should not be registered")
	}
}

func contains(values []string, wanted string) bool {
	for _, value := range values {
		if value == wanted {
			return true
		}
	}
	return false
}

func indexOf(values []string, wanted string) int {
	for position, value := range values {
		if value == wanted {
			return position
		}
	}
	return -1
}
