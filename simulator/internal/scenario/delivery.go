package scenario

import (
	"encoding/json"
	"fmt"
	"math/rand"
	"time"

	"gamestats/simulator/internal/event"
)

const (
	swapProbability       = 0.3
	duplicateProbability  = 0.2
	invalidEveryNthEvent  = 10
	unfinishedKeptPercent = 80
)

func Apply(events []event.Event, variation Variation, random *rand.Rand) [][]byte {
	prepared := events
	if variation.Disconnect {
		prepared = withDisconnect(prepared, random)
	}
	switch variation.Delivery {
	case Shuffled:
		prepared = swapNeighbors(prepared, random)
	case Unfinished:
		prepared = withoutFinish(prepared)
	}

	payloads := encodeAll(prepared)
	switch variation.Delivery {
	case Duplicated:
		return withDuplicates(payloads, random)
	case WithInvalidEvents:
		return withInvalidEvents(prepared, payloads, random)
	}
	return payloads
}

func encodeAll(events []event.Event) [][]byte {
	payloads := make([][]byte, len(events))
	for position, sent := range events {
		payloads[position] = mustEncode(sent)
	}
	return payloads
}

func mustEncode(value any) []byte {
	encoded, err := json.Marshal(value)
	if err != nil {
		panic(fmt.Sprintf("generated event could not be encoded: %v", err))
	}
	return encoded
}

func withDisconnect(events []event.Event, random *rand.Rand) []event.Event {
	joined := playersWhoJoined(events)
	if len(joined) == 0 {
		return events
	}
	leavingPlayer := joined[random.Intn(len(joined))]
	lastPosition := positionOfLastEventBy(events, leavingPlayer)
	anchor := events[lastPosition]
	left := event.Event{
		SchemaVersion: event.SchemaVersion,
		EventId:       fmt.Sprintf("%s-left", anchor.EventId),
		Timestamp:     anchor.Timestamp.Add(time.Millisecond),
		GameId:        anchor.GameId,
		GameVersion:   anchor.GameVersion,
		MatchId:       anchor.MatchId,
		PlayerId:      leavingPlayer,
		Type:          event.PlayerLeft,
		Data:          map[string]any{"reason": "disconnected"},
	}
	result := append([]event.Event(nil), events[:lastPosition+1]...)
	result = append(result, left)
	return append(result, events[lastPosition+1:]...)
}

func positionOfLastEventBy(events []event.Event, playerId string) int {
	last := 0
	for position, candidate := range events {
		if candidate.PlayerId == playerId {
			last = position
		}
	}
	return last
}

func playersWhoJoined(events []event.Event) []string {
	var players []string
	for _, candidate := range events {
		if candidate.Type == event.PlayerJoined {
			players = append(players, candidate.PlayerId)
		}
	}
	return players
}

func swapNeighbors(events []event.Event, random *rand.Rand) []event.Event {
	shuffled := append([]event.Event(nil), events...)
	for position := 0; position < len(shuffled)-1; position++ {
		if random.Float64() < swapProbability {
			shuffled[position], shuffled[position+1] = shuffled[position+1], shuffled[position]
		}
	}
	return shuffled
}

func withoutFinish(events []event.Event) []event.Event {
	kept := events[:len(events)*unfinishedKeptPercent/100]
	result := make([]event.Event, 0, len(kept))
	for _, candidate := range kept {
		if candidate.Type != event.MatchFinished {
			result = append(result, candidate)
		}
	}
	return result
}

func withDuplicates(payloads [][]byte, random *rand.Rand) [][]byte {
	result := make([][]byte, 0, len(payloads))
	for _, payload := range payloads {
		result = append(result, payload)
		if random.Float64() < duplicateProbability {
			result = append(result, payload)
		}
	}
	return result
}

func withInvalidEvents(events []event.Event, payloads [][]byte, random *rand.Rand) [][]byte {
	result := make([][]byte, 0, len(payloads)+len(payloads)/invalidEveryNthEvent+1)
	for position, payload := range payloads {
		result = append(result, payload)
		if position%invalidEveryNthEvent == invalidEveryNthEvent-1 {
			result = append(result, corrupt(events[position], random))
		}
	}
	return result
}

func corrupt(template event.Event, random *rand.Rand) []byte {
	var fields map[string]any
	if err := json.Unmarshal(mustEncode(template), &fields); err != nil {
		panic(fmt.Sprintf("generated event could not be decoded: %v", err))
	}
	fields["eventId"] = fmt.Sprintf("%s-invalid", template.EventId)
	corruptions := []func(){
		func() { delete(fields, "eventId") },
		func() { fields["type"] = "NOT_A_REAL_TYPE" },
		func() { fields["timestamp"] = "last tuesday" },
		func() { fields["unexpectedField"] = true },
		func() { fields["data"] = "not an object" },
		func() { delete(fields, "matchId") },
	}
	corruptions[random.Intn(len(corruptions))]()
	return mustEncode(fields)
}
