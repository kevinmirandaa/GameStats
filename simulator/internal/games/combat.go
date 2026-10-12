package games

import (
	"math/rand"
	"sort"
	"time"

	"gamestats/simulator/internal/event"
)

const (
	combatGameId          = "combat"
	combatGameVersion     = "1.0"
	naturalMinimumKills   = 12
	naturalExtraKills     = 9
	hunterStreakLength    = 4
	leaderLeadLength      = 3
	winnerFightBackLength = 4
	stepMinimumMs         = 2000
	stepExtraMs           = 3000
	shotSpacing           = 250 * time.Millisecond
	eliminationDelay      = 900 * time.Millisecond
	combatStartDelay      = 5 * time.Second
	joinSpacing           = 100 * time.Millisecond
	minimumDamage         = 25
	damageRange           = 76
	minimumDistance       = 5.0
	distanceRange         = 60.0
)

var combatMaps = []string{"dust-yard", "iron-docks", "glass-tower"}

var combatModes = []string{"deathmatch", "duel-rounds"}

var combatWeapons = []string{"rifle", "shotgun", "sniper", "pistol"}

type kill struct {
	killer string
	victim string
}

type combatGenerator struct{}

func Combat() GameMatchGenerator { return combatGenerator{} }

func (combatGenerator) GameId() string { return combatGameId }

func (combatGenerator) PlayerPrefix() string { return "fighter" }

func (combatGenerator) Generate(plan MatchPlan, random *rand.Rand) []event.Event {
	kills := killsFor(plan.Storyline, plan.PlayerIds, random)

	recorded := newTimeline(combatGameId, combatGameVersion, plan.MatchId)
	recorded.record(plan.StartTime, event.MatchStarted, "", "", map[string]any{
		"map":  combatMaps[random.Intn(len(combatMaps))],
		"mode": combatModes[random.Intn(len(combatModes))],
	})
	for slot, playerId := range plan.PlayerIds {
		recorded.record(plan.StartTime.Add(time.Duration(slot+1)*joinSpacing), event.PlayerJoined, playerId, "", map[string]any{
			"displayName": playerId,
		})
	}

	clock := plan.StartTime.Add(combatStartDelay)
	for _, scored := range kills {
		clock = clock.Add(time.Duration(stepMinimumMs+random.Intn(stepExtraMs)) * time.Millisecond)
		recordEngagement(recorded, scored, clock, random)
	}

	ranking := rankingOf(plan.PlayerIds, kills)
	recorded.record(recorded.lastTimestamp().Add(2*time.Second), event.MatchFinished, "", "", map[string]any{
		"winnerPlayerId": ranking[0],
		"ranking":        ranking,
	})
	return recorded.ordered()
}

func killsFor(storyline Storyline, players []string, random *rand.Rand) []kill {
	switch storyline {
	case Streak:
		return streakKills(players, random)
	case Comeback:
		return comebackKills(players, random)
	case Quiet:
		return roundRobinKills(players)
	default:
		return randomKills(players, random, naturalMinimumKills+random.Intn(naturalExtraKills))
	}
}

func randomKills(players []string, random *rand.Rand, count int) []kill {
	kills := make([]kill, count)
	for position := range kills {
		killer := players[random.Intn(len(players))]
		kills[position] = kill{killer: killer, victim: otherThan(players, killer, random)}
	}
	return kills
}

func roundRobinKills(players []string) []kill {
	count := 2*len(players) + 1
	kills := make([]kill, count)
	for position := range kills {
		kills[position] = kill{killer: players[position%len(players)], victim: players[(position+1)%len(players)]}
	}
	return kills
}

func streakKills(players []string, random *rand.Rand) []kill {
	hunter := players[random.Intn(len(players))]
	others := without(players, hunter)
	var kills []kill
	kills = append(kills, killsBy(others, players, random, 3)...)
	kills = append(kills, repeatedKills(hunter, players, random, hunterStreakLength)...)
	kills = append(kills, killsBy(others, players, random, 2)...)
	return kills
}

func comebackKills(players []string, random *rand.Rand) []kill {
	leader := players[random.Intn(len(players))]
	winner := otherThan(players, leader, random)
	var kills []kill
	kills = append(kills, repeatedKills(leader, players, random, leaderLeadLength)...)
	kills = append(kills, repeatedKills(winner, players, random, winnerFightBackLength)...)
	return kills
}

func killsBy(killers, everyone []string, random *rand.Rand, count int) []kill {
	kills := make([]kill, count)
	for position := range kills {
		killer := killers[random.Intn(len(killers))]
		kills[position] = kill{killer: killer, victim: otherThan(everyone, killer, random)}
	}
	return kills
}

func repeatedKills(killer string, everyone []string, random *rand.Rand, count int) []kill {
	kills := make([]kill, count)
	for position := range kills {
		kills[position] = kill{killer: killer, victim: otherThan(everyone, killer, random)}
	}
	return kills
}

func recordEngagement(recorded *timeline, scored kill, startedAt time.Time, random *rand.Rand) {
	shotCount := 1 + random.Intn(3)
	for shot := 0; shot < shotCount; shot++ {
		hit := shot == shotCount-1 || random.Float64() < 0.5
		recorded.record(startedAt.Add(time.Duration(shot)*shotSpacing), event.GameAction, scored.killer, "SHOT_FIRED", map[string]any{
			"weapon": combatWeapons[random.Intn(len(combatWeapons))],
			"hit":    hit,
		})
	}
	damageCount := 1 + random.Intn(2)
	for hitNumber := 0; hitNumber < damageCount; hitNumber++ {
		recorded.record(startedAt.Add(time.Duration(shotCount+hitNumber)*shotSpacing), event.GameAction, scored.killer, "DAMAGE_DEALT", map[string]any{
			"targetPlayerId": scored.victim,
			"amount":         minimumDamage + random.Intn(damageRange),
			"weapon":         combatWeapons[random.Intn(len(combatWeapons))],
		})
	}
	recorded.record(startedAt.Add(eliminationDelay+time.Duration(shotCount+damageCount)*shotSpacing), event.GameAction, scored.killer, "ELIMINATION", map[string]any{
		"victimPlayerId": scored.victim,
		"weapon":         combatWeapons[random.Intn(len(combatWeapons))],
		"distanceMeters": roundTo(minimumDistance+random.Float64()*distanceRange, 2),
	})
}

func rankingOf(players []string, kills []kill) []string {
	scores := make(map[string]int, len(players))
	for _, scored := range kills {
		scores[scored.killer]++
	}
	ranking := append([]string(nil), players...)
	sort.SliceStable(ranking, func(first, second int) bool {
		if scores[ranking[first]] != scores[ranking[second]] {
			return scores[ranking[first]] > scores[ranking[second]]
		}
		return ranking[first] < ranking[second]
	})
	return ranking
}

func otherThan(players []string, excluded string, random *rand.Rand) string {
	return without(players, excluded)[random.Intn(len(players)-1)]
}

func without(players []string, excluded string) []string {
	remaining := make([]string, 0, len(players)-1)
	for _, player := range players {
		if player != excluded {
			remaining = append(remaining, player)
		}
	}
	return remaining
}
