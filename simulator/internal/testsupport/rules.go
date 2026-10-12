package testsupport

import (
	"gamestats/simulator/internal/event"
)

func number(data map[string]any, name string) float64 {
	value, _ := data[name].(float64)
	if value == 0 {
		if integer, isInteger := data[name].(int); isInteger {
			return float64(integer)
		}
	}
	return value
}

func text(data map[string]any, name string) string {
	value, _ := data[name].(string)
	return value
}

func Winner(events []event.Event) string {
	return text(events[len(events)-1].Data, "winnerPlayerId")
}

func LapPositions(events []event.Event, playerId string) []int {
	var positions []int
	for _, candidate := range events {
		if candidate.Action == "LAP_COMPLETED" && candidate.PlayerId == playerId {
			positions = append(positions, int(number(candidate.Data, "position")))
		}
	}
	return positions
}

func LongestImprovingRun(events []event.Event, playerId string) int {
	longest, current := 0, 0
	previous := 0.0
	for _, candidate := range events {
		if candidate.Action != "LAP_COMPLETED" || candidate.PlayerId != playerId {
			continue
		}
		lapTime := number(candidate.Data, "lapTimeMs")
		if current > 0 && lapTime < previous {
			current++
		} else {
			current = 1
		}
		previous = lapTime
		longest = max(longest, current)
	}
	return longest
}

func CumulativeLapTimes(events []event.Event, playerId string) []float64 {
	var cumulative []float64
	total := 0.0
	for _, candidate := range events {
		if candidate.Action == "LAP_COMPLETED" && candidate.PlayerId == playerId {
			total += number(candidate.Data, "lapTimeMs")
			cumulative = append(cumulative, total)
		}
	}
	return cumulative
}

func LongestKillStreak(events []event.Event) int {
	streaks := make(map[string]int)
	longest := 0
	for _, candidate := range events {
		if candidate.Action != "ELIMINATION" {
			continue
		}
		streaks[text(candidate.Data, "victimPlayerId")] = 0
		streaks[candidate.PlayerId]++
		longest = max(longest, streaks[candidate.PlayerId])
	}
	return longest
}

func DeepestDeficit(events []event.Event, playerId string) int {
	scores := make(map[string]int)
	deepest := 0
	for _, candidate := range events {
		if candidate.Action != "ELIMINATION" {
			continue
		}
		scores[candidate.PlayerId]++
		leader := 0
		for other, score := range scores {
			if other != playerId {
				leader = max(leader, score)
			}
		}
		deepest = max(deepest, leader-scores[playerId])
	}
	return deepest
}

func Eliminations(events []event.Event) []event.Event {
	var eliminations []event.Event
	for _, candidate := range events {
		if candidate.Action == "ELIMINATION" {
			eliminations = append(eliminations, candidate)
		}
	}
	return eliminations
}

func VictimOf(elimination event.Event) string {
	return text(elimination.Data, "victimPlayerId")
}

func Ranking(events []event.Event) []string {
	rawRanking, _ := events[len(events)-1].Data["ranking"].([]string)
	return rawRanking
}
