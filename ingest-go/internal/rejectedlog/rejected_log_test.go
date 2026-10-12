package rejectedlog_test

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"gamestats/ingest/internal/rejectedlog"
)

func TestRecentEntriesAreLimitedToTheNewest(t *testing.T) {
	rejections, err := rejectedlog.Open("", 2)
	if err != nil {
		t.Fatal(err)
	}
	rejections.Record("first", []string{"reason"}, []byte("{}"))
	rejections.Record("second", []string{"reason"}, []byte("{}"))
	rejections.Record("third", []string{"reason"}, []byte("{}"))

	recent := rejections.Recent()
	if len(recent) != 2 || recent[0].EventId != "second" || recent[1].EventId != "third" {
		t.Fatalf("unexpected recent entries: %+v", recent)
	}
}

func TestEntriesAreAppendedToTheFileAsJsonLines(t *testing.T) {
	path := filepath.Join(t.TempDir(), "rejected.log")
	rejections, err := rejectedlog.Open(path, 10)
	if err != nil {
		t.Fatal(err)
	}
	rejections.Record("evt-1", []string{"bad"}, []byte(`{"broken"`))
	rejections.Record("evt-2", []string{"worse"}, []byte(`[]`))
	if err := rejections.Close(); err != nil {
		t.Fatal(err)
	}

	content, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	lines := strings.Split(strings.TrimSpace(string(content)), "\n")
	if len(lines) != 2 || !strings.Contains(lines[0], "evt-1") || !strings.Contains(lines[1], "evt-2") {
		t.Fatalf("unexpected file content: %q", content)
	}
}

func TestLongPayloadsAreTruncated(t *testing.T) {
	rejections, _ := rejectedlog.Open("", 1)
	rejections.Record("evt", []string{"r"}, []byte(strings.Repeat("x", 5000)))
	if length := len(rejections.Recent()[0].Payload); length != 1024 {
		t.Fatalf("payload should be truncated to 1024 characters but had %d", length)
	}
}
