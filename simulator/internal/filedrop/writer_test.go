package filedrop_test

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"gamestats/simulator/internal/filedrop"
	"gamestats/simulator/internal/sender"
)

func payloads(count int) [][]byte {
	result := make([][]byte, count)
	for position := range result {
		result[position] = []byte(`{"n":` + string(rune('0'+position)) + `}`)
	}
	return result
}

func readLines(t *testing.T, path string) []string {
	t.Helper()
	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	return strings.Split(strings.TrimRight(string(content), "\n"), "\n")
}

func TestAllEventsOfAMatchAreWrittenAsOneLineEachInOrder(t *testing.T) {
	directory := t.TempDir()
	writer, err := filedrop.New(directory)
	if err != nil {
		t.Fatal(err)
	}
	tally := &sender.Tally{}
	writer.Deliver(context.Background(), "match-1", payloads(4), sender.Pacing{}, tally)

	lines := readLines(t, filepath.Join(directory, "match-1.jsonl"))
	if len(lines) != 4 || lines[0] != `{"n":0}` || lines[3] != `{"n":3}` {
		t.Fatalf("unexpected lines %v", lines)
	}
	if tally.Sent.Load() != 4 || tally.Accepted.Load() != 4 || tally.Failed.Load() != 0 {
		t.Fatalf("unexpected tally sent=%d accepted=%d failed=%d", tally.Sent.Load(), tally.Accepted.Load(), tally.Failed.Load())
	}
}

func TestEachMatchGetsItsOwnFile(t *testing.T) {
	directory := t.TempDir()
	writer, _ := filedrop.New(directory)
	tally := &sender.Tally{}
	writer.Deliver(context.Background(), "match-a", payloads(2), sender.Pacing{}, tally)
	writer.Deliver(context.Background(), "match-b", payloads(3), sender.Pacing{}, tally)

	files, err := filepath.Glob(filepath.Join(directory, "*.jsonl"))
	if err != nil || len(files) != 2 {
		t.Fatalf("expected two files but found %v (%v)", files, err)
	}
}

func TestPacedWritingAppendsLineByLineAndKeepsTheOrder(t *testing.T) {
	directory := t.TempDir()
	writer, _ := filedrop.New(directory)
	tally := &sender.Tally{}
	finished := make(chan struct{})
	go func() {
		writer.Deliver(context.Background(), "live", payloads(5), sender.Pacing{EventsPerSecond: 50}, tally)
		close(finished)
	}()

	path := filepath.Join(directory, "live.jsonl")
	deadline := time.After(3 * time.Second)
	sawPartialFile := false
	for done := false; !done; {
		select {
		case <-finished:
			done = true
		case <-deadline:
			t.Fatal("paced writing did not finish")
		case <-time.After(5 * time.Millisecond):
			if content, err := os.ReadFile(path); err == nil && len(strings.Split(strings.TrimSpace(string(content)), "\n")) < 5 && len(content) > 0 {
				sawPartialFile = true
			}
		}
	}
	if !sawPartialFile {
		t.Fatal("the file should be readable while it is still growing")
	}
	if lines := readLines(t, path); len(lines) != 5 || lines[4] != `{"n":4}` {
		t.Fatalf("unexpected lines %v", lines)
	}
}

func TestCancellationStopsPacedWriting(t *testing.T) {
	directory := t.TempDir()
	writer, _ := filedrop.New(directory)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	tally := &sender.Tally{}
	writer.Deliver(ctx, "stopped", payloads(5), sender.Pacing{EventsPerSecond: 1}, tally)
	if tally.Sent.Load() > 1 {
		t.Fatalf("a cancelled run should stop after at most one event but sent %d", tally.Sent.Load())
	}
}

func TestAnUnwritableDirectoryCountsEveryEventAsFailed(t *testing.T) {
	directory := t.TempDir()
	writer, _ := filedrop.New(directory)
	if err := os.RemoveAll(directory); err != nil {
		t.Fatal(err)
	}
	tally := &sender.Tally{}
	writer.Deliver(context.Background(), "lost", payloads(3), sender.Pacing{}, tally)
	if tally.Failed.Load() != 3 || tally.Accepted.Load() != 0 {
		t.Fatalf("expected 3 failures but got failed=%d accepted=%d", tally.Failed.Load(), tally.Accepted.Load())
	}
}

func TestTheOutputDirectoryIsCreatedWhenMissing(t *testing.T) {
	nested := filepath.Join(t.TempDir(), "a", "b")
	if _, err := filedrop.New(nested); err != nil {
		t.Fatal(err)
	}
	if info, err := os.Stat(nested); err != nil || !info.IsDir() {
		t.Fatal("the nested directory should have been created")
	}
}
