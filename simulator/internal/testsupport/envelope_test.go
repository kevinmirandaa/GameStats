package testsupport_test

import (
	"os"
	"path/filepath"
	"testing"

	"gamestats/simulator/internal/testsupport"
)

func readExamples(t *testing.T, directory string) map[string][]byte {
	t.Helper()
	matches, err := filepath.Glob(filepath.Join("../../../contracts/examples", directory, "*.json"))
	if err != nil || len(matches) == 0 {
		t.Fatalf("no examples found in %s", directory)
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

func TestTheCheckerAcceptsEveryValidContractExample(t *testing.T) {
	for name, raw := range readExamples(t, "valid") {
		if problems := testsupport.EnvelopeProblems(raw); len(problems) != 0 {
			t.Errorf("%s should be valid but got %v", name, problems)
		}
	}
}

func TestTheCheckerRejectsEveryInvalidContractExample(t *testing.T) {
	for name, raw := range readExamples(t, "invalid") {
		if len(testsupport.EnvelopeProblems(raw)) == 0 {
			t.Errorf("%s should be rejected", name)
		}
	}
}
