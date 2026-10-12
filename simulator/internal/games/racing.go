package games

import (
	"fmt"
	"math/rand"
	"sort"
	"time"

	"gamestats/simulator/internal/event"
)

const (
	racingGameId        = "racing"
	racingGameVersion   = "1.0"
	baseLapMilliseconds = 90000
	minimumLaps         = 3
	extraLapsRange      = 3
	raceStartDelay      = 10 * time.Second
	gridSpacing         = 100 * time.Millisecond
	slotSpacingMs       = 1000
	zigzagAmplitudeMs   = 300
	quietJitterMs       = 100
	naturalJitterMs     = 600
	pitStopLapPenaltyMs = 2400
	penaltyProbability  = 0.4
	minimumTopSpeed     = 270.0
	topSpeedRange       = 25.0
)

var racingTracks = []string{"monza", "suzuka", "spa", "interlagos", "silverstone"}

var racingVehicles = []string{"falcon", "comet", "viper", "aurora"}

type racingGenerator struct{}

func Racing() GameMatchGenerator { return racingGenerator{} }

func (racingGenerator) GameId() string { return racingGameId }

func (racingGenerator) PlayerPrefix() string { return "driver" }

func (racingGenerator) Generate(plan MatchPlan, random *rand.Rand) []event.Event {
	driverCount := len(plan.PlayerIds)
	lapCount := minimumLaps + random.Intn(extraLapsRange)
	lapTimes := lapTimesFor(plan.Storyline, driverCount, lapCount, random)
	pitStops := addPitStops(plan.Storyline, lapTimes, random)
	cumulative := cumulativeTimes(lapTimes)

	recorded := newTimeline(racingGameId, racingGameVersion, plan.MatchId)
	recorded.record(plan.StartTime, event.MatchStarted, "", "", map[string]any{
		"track":     racingTracks[random.Intn(len(racingTracks))],
		"totalLaps": lapCount,
	})
	for slot, playerId := range plan.PlayerIds {
		recorded.record(plan.StartTime.Add(time.Duration(slot+1)*gridSpacing), event.PlayerJoined, playerId, "", map[string]any{
			"displayName":   playerId,
			"vehicle":       racingVehicles[random.Intn(len(racingVehicles))],
			"startPosition": slot + 1,
		})
	}

	raceStart := plan.StartTime.Add(raceStartDelay)
	recordLaps(recorded, plan, raceStart, lapTimes, cumulative, pitStops, random)
	recordPenalty(recorded, plan, raceStart, cumulative, random)

	finalOrder := orderAtLap(cumulative, lapCount-1)
	ranking := make([]string, len(finalOrder))
	for position, slot := range finalOrder {
		ranking[position] = plan.PlayerIds[slot]
	}
	recorded.record(recorded.lastTimestamp().Add(2*time.Second), event.MatchFinished, "", "", map[string]any{
		"winnerPlayerId": ranking[0],
		"ranking":        ranking,
	})
	return recorded.ordered()
}

func lapTimesFor(storyline Storyline, driverCount, lapCount int, random *rand.Rand) [][]int {
	lapTimes := make([][]int, driverCount)
	switch storyline {
	case Comeback:
		winnerSlot := random.Intn(driverCount)
		for slot := range lapTimes {
			lapTimes[slot] = comebackLaps(slot, winnerSlot, driverCount, lapCount, random)
		}
	case Streak:
		for slot := range lapTimes {
			lapTimes[slot] = streakLaps(slot, lapCount, random)
		}
	case Quiet:
		for slot := range lapTimes {
			lapTimes[slot] = zigzagLaps(slot, lapCount, random)
		}
	default:
		for slot := range lapTimes {
			lapTimes[slot] = naturalLaps(slot, lapCount, random)
		}
	}
	return lapTimes
}

func naturalLaps(slot, lapCount int, random *rand.Rand) []int {
	laps := make([]int, lapCount)
	for lap := range laps {
		laps[lap] = baseLapMilliseconds + slot*slotSpacingMs/2 + jitter(random, naturalJitterMs)
	}
	return laps
}

func zigzagLaps(slot, lapCount int, random *rand.Rand) []int {
	laps := make([]int, lapCount)
	for lap := range laps {
		direction := zigzagAmplitudeMs
		if lap%2 == 1 {
			direction = -zigzagAmplitudeMs
		}
		laps[lap] = baseLapMilliseconds + slot*slotSpacingMs + direction + jitter(random, quietJitterMs)
	}
	return laps
}

func streakLaps(slot, lapCount int, random *rand.Rand) []int {
	if slot != 0 {
		return zigzagLaps(slot, lapCount, random)
	}
	laps := make([]int, lapCount)
	for lap := range laps {
		laps[lap] = baseLapMilliseconds - 500*(lap+1)
	}
	return laps
}

func comebackLaps(slot, winnerSlot, driverCount, lapCount int, random *rand.Rand) []int {
	laps := make([]int, lapCount)
	if slot != winnerSlot {
		rankAmongOthers := slot
		if slot > winnerSlot {
			rankAmongOthers--
		}
		for lap := range laps {
			laps[lap] = baseLapMilliseconds + rankAmongOthers*400 + jitter(random, 300)
		}
		return laps
	}
	laps[0] = baseLapMilliseconds + driverCount*400 + 1500
	for lap := 1; lap < lapCount; lap++ {
		laps[lap] = baseLapMilliseconds - 3000 - 500*(lap-1)
	}
	return laps
}

func addPitStops(storyline Storyline, lapTimes [][]int, random *rand.Rand) map[[2]int]bool {
	pitStops := make(map[[2]int]bool)
	if storyline != Natural {
		return pitStops
	}
	driver := random.Intn(len(lapTimes))
	lap := random.Intn(len(lapTimes[driver]))
	lapTimes[driver][lap] += pitStopLapPenaltyMs + random.Intn(400)
	pitStops[[2]int{driver, lap}] = true
	return pitStops
}

func cumulativeTimes(lapTimes [][]int) [][]int {
	cumulative := make([][]int, len(lapTimes))
	for slot, laps := range lapTimes {
		cumulative[slot] = make([]int, len(laps))
		running := 0
		for lap, lapTime := range laps {
			running += lapTime
			cumulative[slot][lap] = running
		}
	}
	return cumulative
}

func orderAtLap(cumulative [][]int, lap int) []int {
	order := make([]int, len(cumulative))
	for slot := range order {
		order[slot] = slot
	}
	sort.SliceStable(order, func(first, second int) bool {
		return cumulative[order[first]][lap] < cumulative[order[second]][lap]
	})
	return order
}

func positionsAtLap(cumulative [][]int, lap int) []int {
	positions := make([]int, len(cumulative))
	for position, slot := range orderAtLap(cumulative, lap) {
		positions[slot] = position + 1
	}
	return positions
}

func recordLaps(recorded *timeline, plan MatchPlan, raceStart time.Time, lapTimes, cumulative [][]int, pitStops map[[2]int]bool, random *rand.Rand) {
	lapCount := len(lapTimes[0])
	var previousPositions []int
	for lap := 0; lap < lapCount; lap++ {
		positions := positionsAtLap(cumulative, lap)
		for slot, playerId := range plan.PlayerIds {
			finishedAt := raceStart.Add(time.Duration(cumulative[slot][lap]+slot) * time.Millisecond)
			recorded.record(finishedAt, event.GameAction, playerId, "LAP_COMPLETED", map[string]any{
				"lap":         lap + 1,
				"lapTimeMs":   lapTimes[slot][lap],
				"position":    positions[slot],
				"topSpeedKmh": roundTo(minimumTopSpeed+random.Float64()*topSpeedRange, 1),
			})
			if pitStops[[2]int{slot, lap}] {
				recorded.record(finishedAt.Add(-time.Millisecond), event.GameAction, playerId, "PIT_STOP", map[string]any{
					"lap":        lap + 1,
					"durationMs": 2000 + random.Intn(1000),
				})
			}
			if previousPositions != nil && previousPositions[slot] != positions[slot] {
				recorded.record(finishedAt.Add(time.Millisecond), event.GameAction, playerId, "POSITION_CHANGED", map[string]any{
					"lap":          lap + 1,
					"fromPosition": previousPositions[slot],
					"toPosition":   positions[slot],
				})
			}
		}
		previousPositions = positions
	}
}

func recordPenalty(recorded *timeline, plan MatchPlan, raceStart time.Time, cumulative [][]int, random *rand.Rand) {
	if random.Float64() >= penaltyProbability {
		return
	}
	slot := random.Intn(len(plan.PlayerIds))
	lap := random.Intn(len(cumulative[slot]))
	issuedAt := raceStart.Add(time.Duration(cumulative[slot][lap]+slot+2) * time.Millisecond)
	recorded.record(issuedAt, event.GameAction, plan.PlayerIds[slot], "PENALTY_APPLIED", map[string]any{
		"lap":     lap + 1,
		"seconds": 5 * (1 + random.Intn(2)),
		"reason":  fmt.Sprintf("track-limits-%d", lap+1),
	})
}

func jitter(random *rand.Rand, amplitude int) int {
	return random.Intn(2*amplitude+1) - amplitude
}
