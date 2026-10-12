package validation_test

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"gamestats/ingest/internal/validation"
)

const examplesDirectory = "../../../contracts/examples"

func readExampleFiles(t *testing.T, relativeDirectory string) map[string][]byte {
	t.Helper()
	matches, err := filepath.Glob(filepath.Join(examplesDirectory, relativeDirectory, "*.json"))
	if err != nil || len(matches) == 0 {
		t.Fatalf("no example files found in %s", relativeDirectory)
	}
	contents := make(map[string][]byte)
	for _, path := range matches {
		raw, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		contents[filepath.Base(path)] = raw
	}
	return contents
}

func TestEveryValidExampleIsAccepted(t *testing.T) {
	for name, raw := range readExampleFiles(t, "valid") {
		result := validation.Validate(raw)
		if !result.IsValid() {
			t.Errorf("%s should be valid but was rejected: %v", name, result.Reasons)
		}
	}
}

func TestEveryInvalidExampleIsRejected(t *testing.T) {
	for name, raw := range readExampleFiles(t, "invalid") {
		result := validation.Validate(raw)
		if result.IsValid() {
			t.Errorf("%s should be rejected but was accepted", name)
		}
	}
}

func TestEveryEventOfTheGameExampleMatchesIsAccepted(t *testing.T) {
	for _, game := range []string{"racing", "combat"} {
		for name, raw := range readExampleFiles(t, filepath.Join("games", game)) {
			if !strings.HasSuffix(name, ".batch.json") {
				continue
			}
			var batch struct {
				Events []json.RawMessage `json:"events"`
			}
			if err := json.Unmarshal(raw, &batch); err != nil {
				t.Fatal(err)
			}
			for position, rawEvent := range batch.Events {
				result := validation.Validate(rawEvent)
				if !result.IsValid() {
					t.Errorf("%s event %d was rejected: %v", name, position, result.Reasons)
				}
			}
		}
	}
}

func TestTimestampWithOffsetIsNormalizedToUniversalTime(t *testing.T) {
	raw := readExampleFiles(t, "valid")["timestamp-with-offset.json"]
	result := validation.Validate(raw)
	if result.Event.Timestamp.Location() != time.UTC {
		t.Fatalf("timestamp should be in UTC but was in %s", result.Event.Timestamp.Location())
	}
}

func TestMissingDataBecomesAnEmptyObject(t *testing.T) {
	raw := readExampleFiles(t, "valid")["match-finished-without-data.json"]
	result := validation.Validate(raw)
	if string(result.Event.Data) != "{}" {
		t.Fatalf("data should default to an empty object but was %s", result.Event.Data)
	}
}

func TestAllReasonsAreReportedAtOnce(t *testing.T) {
	raw := []byte(`{"schemaVersion":"2.0","timestamp":"yesterday","gameId":"racing","gameVersion":"1.0","matchId":"m1","type":"GAME_ACTION","extra":1}`)
	result := validation.Validate(raw)
	expectedFragments := []string{"eventId is required", "schemaVersion", "timestamp must be", "playerId is required", "action is required", "unknown field"}
	for _, fragment := range expectedFragments {
		if !reasonsContain(result.Reasons, fragment) {
			t.Errorf("reasons should mention %q but were %v", fragment, result.Reasons)
		}
	}
}

func TestPayloadsThatAreNotJsonObjectsAreRejected(t *testing.T) {
	for _, payload := range []string{"", "not json", "[]", "42", `{"unterminated":`} {
		if validation.Validate([]byte(payload)).IsValid() {
			t.Errorf("payload %q should be rejected", payload)
		}
	}
}

func TestActionIsNotInterpretedOnlyRequired(t *testing.T) {
	raw := []byte(`{"schemaVersion":"1.0","eventId":"e1","timestamp":"2026-10-12T18:00:00Z","gameId":"anything","gameVersion":"9","matchId":"m1","playerId":"p1","type":"GAME_ACTION","action":"SOMETHING_NEVER_SEEN","data":{"deep":{"nested":[1,2,3]}}}`)
	if result := validation.Validate(raw); !result.IsValid() {
		t.Fatalf("unknown actions must be accepted but got %v", result.Reasons)
	}
}

func reasonsContain(reasons []string, fragment string) bool {
	for _, reason := range reasons {
		if strings.Contains(reason, fragment) {
			return true
		}
	}
	return false
}
