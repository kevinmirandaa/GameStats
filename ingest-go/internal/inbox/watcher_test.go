package inbox_test

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"gamestats/ingest/internal/deduplication"
	"gamestats/ingest/internal/event"
	"gamestats/ingest/internal/inbox"
	"gamestats/ingest/internal/ingestion"
	"gamestats/ingest/internal/matchregistry"
	"gamestats/ingest/internal/metrics"
	"gamestats/ingest/internal/rejectedlog"
)

type recordingIngestor struct {
	mutex    sync.Mutex
	payloads []string
}

func (ingestor *recordingIngestor) Ingest(rawEvent []byte) ingestion.Result {
	ingestor.mutex.Lock()
	defer ingestor.mutex.Unlock()
	ingestor.payloads = append(ingestor.payloads, string(rawEvent))
	if strings.Contains(string(rawEvent), "bad") {
		return ingestion.Result{Outcome: ingestion.Invalid}
	}
	return ingestion.Result{Outcome: ingestion.Accepted}
}

func (ingestor *recordingIngestor) received() []string {
	ingestor.mutex.Lock()
	defer ingestor.mutex.Unlock()
	return append([]string(nil), ingestor.payloads...)
}

type fixture struct {
	directory  string
	ingestor   *recordingIngestor
	counters   *metrics.Counters
	rejections *rejectedlog.Log
	watcher    *inbox.Watcher
}

func newFixture(t *testing.T) *fixture {
	t.Helper()
	rejections, err := rejectedlog.Open("", 20)
	if err != nil {
		t.Fatal(err)
	}
	directory := t.TempDir()
	ingestor := &recordingIngestor{}
	counters := &metrics.Counters{}
	return &fixture{
		directory:  directory,
		ingestor:   ingestor,
		counters:   counters,
		rejections: rejections,
		watcher:    inbox.New(directory, 10*time.Millisecond, ingestor, rejections, counters),
	}
}

func (state *fixture) write(t *testing.T, name, content string) string {
	t.Helper()
	path := filepath.Join(state.directory, name)
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
	return path
}

func (state *fixture) append(t *testing.T, path, content string) {
	t.Helper()
	file, err := os.OpenFile(path, os.O_APPEND|os.O_WRONLY, 0o644)
	if err != nil {
		t.Fatal(err)
	}
	defer file.Close()
	if _, err := file.WriteString(content); err != nil {
		t.Fatal(err)
	}
}

func (state *fixture) exists(parts ...string) bool {
	_, err := os.Stat(filepath.Join(append([]string{state.directory}, parts...)...))
	return err == nil
}

func assertPayloads(t *testing.T, actual []string, expected ...string) {
	t.Helper()
	if len(actual) != len(expected) {
		t.Fatalf("expected %v but got %v", expected, actual)
	}
	for position := range expected {
		if actual[position] != expected[position] {
			t.Fatalf("expected %v but got %v", expected, actual)
		}
	}
}

func TestLinesOfAGrowingJsonlFileAreReadOnceAsTheyAppear(t *testing.T) {
	state := newFixture(t)
	path := state.write(t, "match.jsonl", `{"n":1}`+"\n"+`{"n":2}`+"\n")

	state.watcher.ScanOnce()
	assertPayloads(t, state.ingestor.received(), `{"n":1}`, `{"n":2}`)

	state.append(t, path, `{"n":3}`+"\n")
	state.watcher.ScanOnce()
	state.watcher.ScanOnce()
	assertPayloads(t, state.ingestor.received(), `{"n":1}`, `{"n":2}`, `{"n":3}`)
}

func TestAnIncompleteLastLineWaitsUntilItIsFinished(t *testing.T) {
	state := newFixture(t)
	path := state.write(t, "match.jsonl", `{"n":1}`+"\n"+`{"n":`)

	state.watcher.ScanOnce()
	assertPayloads(t, state.ingestor.received(), `{"n":1}`)

	state.append(t, path, `2}`+"\n")
	state.watcher.ScanOnce()
	assertPayloads(t, state.ingestor.received(), `{"n":1}`, `{"n":2}`)
}

func TestAFinalLineWithoutNewlineIsReadOnceTheFileStopsChanging(t *testing.T) {
	state := newFixture(t)
	state.write(t, "match.jsonl", `{"n":1}`+"\n"+`{"n":2}`)

	state.watcher.ScanOnce()
	assertPayloads(t, state.ingestor.received(), `{"n":1}`)

	state.watcher.ScanOnce()
	assertPayloads(t, state.ingestor.received(), `{"n":1}`, `{"n":2}`)
}

func TestBlankLinesAreSkipped(t *testing.T) {
	state := newFixture(t)
	state.write(t, "match.jsonl", "\n"+`{"n":1}`+"\n\n   \n"+`{"n":2}`+"\n")
	state.watcher.ScanOnce()
	assertPayloads(t, state.ingestor.received(), `{"n":1}`, `{"n":2}`)
}

func TestATruncatedJsonlFileIsReadAgainFromTheStart(t *testing.T) {
	state := newFixture(t)
	path := state.write(t, "match.jsonl", `{"n":1}`+"\n"+`{"n":2}`+"\n")
	state.watcher.ScanOnce()
	if err := os.WriteFile(path, []byte(`{"n":9}`+"\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	state.watcher.ScanOnce()
	assertPayloads(t, state.ingestor.received(), `{"n":1}`, `{"n":2}`, `{"n":9}`)
}

func TestAJsonArrayIsIngestedOnlyAfterItIsStableAndThenArchived(t *testing.T) {
	state := newFixture(t)
	path := state.write(t, "events.json", `[{"n":1},{"n":2}]`)

	state.watcher.ScanOnce()
	if len(state.ingestor.received()) != 0 {
		t.Fatal("a file seen for the first time must wait until it stops changing")
	}

	state.watcher.ScanOnce()
	assertPayloads(t, state.ingestor.received(), `{"n":1}`, `{"n":2}`)
	if _, err := os.Stat(path); err == nil {
		t.Fatal("the ingested file should have been moved out of the inbox")
	}
	if !state.exists("processed", "events.json") {
		t.Fatal("the file should be archived in the processed folder")
	}
	if state.counters.Snapshot(0).FilesProcessed != 1 {
		t.Fatal("one processed file should be counted")
	}
}

func TestAMatchBatchObjectDeliversItsEvents(t *testing.T) {
	state := newFixture(t)
	state.write(t, "batch.json", `{"gameId":"g","matchId":"m","status":"COMPLETED","events":[{"n":1},{"n":2},{"n":3}]}`)
	state.watcher.ScanOnce()
	state.watcher.ScanOnce()
	assertPayloads(t, state.ingestor.received(), `{"n":1}`, `{"n":2}`, `{"n":3}`)
}

func TestASingleEventObjectIsIngested(t *testing.T) {
	state := newFixture(t)
	state.write(t, "one.json", `{"eventId":"only"}`)
	state.watcher.ScanOnce()
	state.watcher.ScanOnce()
	assertPayloads(t, state.ingestor.received(), `{"eventId":"only"}`)
}

func TestAFileStillBeingWrittenIsNotConsumedUntilItIsStable(t *testing.T) {
	state := newFixture(t)
	path := state.write(t, "events.json", `[{"n":1}`)
	state.watcher.ScanOnce()
	state.append(t, path, `,{"n":2}]`)
	state.watcher.ScanOnce()
	if len(state.ingestor.received()) != 0 {
		t.Fatal("a file that changed between scans must not be consumed yet")
	}
	state.watcher.ScanOnce()
	assertPayloads(t, state.ingestor.received(), `{"n":1}`, `{"n":2}`)
}

func TestAnInvalidFileGoesToTheFailedFolderAndIsLogged(t *testing.T) {
	state := newFixture(t)
	state.write(t, "broken.json", `[{"n":1},`)
	state.watcher.ScanOnce()
	state.watcher.ScanOnce()

	if len(state.ingestor.received()) != 0 {
		t.Fatal("nothing should be ingested from an invalid file")
	}
	if !state.exists("failed", "broken.json") {
		t.Fatal("the invalid file should be moved to the failed folder")
	}
	if state.counters.Snapshot(0).FilesFailed != 1 {
		t.Fatal("one failed file should be counted")
	}
	if recent := state.rejections.Recent(); len(recent) != 1 || !strings.Contains(recent[0].Reasons[0], "broken.json") {
		t.Fatalf("the failure should be recorded with the file name but got %+v", recent)
	}
}

func TestEmptyAndUnsupportedContentIsRejectedAsFailedFiles(t *testing.T) {
	state := newFixture(t)
	state.write(t, "empty.json", "   \n")
	state.write(t, "text.json", "hello")
	state.watcher.ScanOnce()
	state.watcher.ScanOnce()
	if !state.exists("failed", "empty.json") || !state.exists("failed", "text.json") {
		t.Fatal("both files should be moved to the failed folder")
	}
}

func TestSubdirectoriesHiddenTemporaryAndOtherFilesAreIgnored(t *testing.T) {
	state := newFixture(t)
	if err := os.MkdirAll(filepath.Join(state.directory, "nested"), 0o755); err != nil {
		t.Fatal(err)
	}
	state.write(t, filepath.Join("nested", "inside.json"), `[{"n":1}]`)
	state.write(t, ".hidden.json", `[{"n":2}]`)
	state.write(t, "upload.json.tmp", `[{"n":3}]`)
	state.write(t, "notes.txt", `[{"n":4}]`)
	state.watcher.ScanOnce()
	state.watcher.ScanOnce()
	if len(state.ingestor.received()) != 0 {
		t.Fatalf("nothing should be ingested but got %v", state.ingestor.received())
	}
}

func TestRejectedEventsInsideAFileDoNotStopTheRest(t *testing.T) {
	state := newFixture(t)
	state.write(t, "mixed.jsonl", `{"n":1}`+"\n"+`{"bad":true}`+"\n"+`{"n":3}`+"\n")
	state.watcher.ScanOnce()
	assertPayloads(t, state.ingestor.received(), `{"n":1}`, `{"bad":true}`, `{"n":3}`)
}

func TestAFileWithTheSameNameIsArchivedWithoutOverwritingTheFirst(t *testing.T) {
	state := newFixture(t)
	state.write(t, "events.json", `[{"n":1}]`)
	state.watcher.ScanOnce()
	state.watcher.ScanOnce()
	state.write(t, "events.json", `[{"n":2}]`)
	state.watcher.ScanOnce()
	state.watcher.ScanOnce()

	archived, err := os.ReadDir(filepath.Join(state.directory, "processed"))
	if err != nil || len(archived) != 2 {
		t.Fatalf("both files should be archived but found %d (%v)", len(archived), err)
	}
}

func TestRunPicksUpFilesAndStopsWhenTheContextEnds(t *testing.T) {
	state := newFixture(t)
	ctx, cancel := context.WithCancel(context.Background())
	finished := make(chan struct{})
	go func() {
		state.watcher.Run(ctx)
		close(finished)
	}()

	state.write(t, "live.jsonl", `{"n":1}`+"\n")
	deadline := time.After(3 * time.Second)
	for len(state.ingestor.received()) == 0 {
		select {
		case <-deadline:
			t.Fatal("the running watcher never read the file")
		case <-time.After(10 * time.Millisecond):
		}
	}
	cancel()
	select {
	case <-finished:
	case <-time.After(3 * time.Second):
		t.Fatal("the watcher did not stop after cancellation")
	}
}

type collectingDispatcher struct {
	mutex   sync.Mutex
	batches map[string]event.MatchBatch
	arrived chan string
}

func (dispatcher *collectingDispatcher) Dispatch(_ context.Context, batch event.MatchBatch) error {
	dispatcher.mutex.Lock()
	dispatcher.batches[batch.MatchId] = batch
	dispatcher.mutex.Unlock()
	dispatcher.arrived <- batch.MatchId
	return nil
}

func TestAnExampleMatchDroppedInTheFolderReachesAnalyticsAsACompleteMatch(t *testing.T) {
	dispatcher := &collectingDispatcher{batches: make(map[string]event.MatchBatch), arrived: make(chan string, 4)}
	counters := &metrics.Counters{}
	registry := matchregistry.New(dispatcher, counters, matchregistry.Settings{
		IdleTimeout: 300 * time.Millisecond, FinishGrace: 60 * time.Millisecond, DispatchTimeout: time.Second, InboxCapacity: 256,
	})
	rejections, err := rejectedlog.Open("", 10)
	if err != nil {
		t.Fatal(err)
	}
	service := ingestion.NewService(registry, deduplication.NewEventIdRegistry(), rejections, counters)
	t.Cleanup(func() { _ = registry.Shutdown(context.Background()) })

	directory := t.TempDir()
	watcher := inbox.New(directory, 10*time.Millisecond, service, rejections, counters)
	example, err := os.ReadFile("../../../contracts/examples/games/racing/patterns-triggered.batch.json")
	if err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(directory, "race.json"), example, 0o644); err != nil {
		t.Fatal(err)
	}
	watcher.ScanOnce()
	watcher.ScanOnce()

	select {
	case matchId := <-dispatcher.arrived:
		batch := dispatcher.batches[matchId]
		if matchId != "match-racing-001" || batch.Status != event.MatchCompleted || len(batch.Events) != 30 {
			t.Fatalf("unexpected batch %s %s with %d events", matchId, batch.Status, len(batch.Events))
		}
	case <-time.After(5 * time.Second):
		t.Fatal("the match from the file never reached analytics")
	}
	snapshot := counters.Snapshot(0)
	if snapshot.EventsAcceptedByGame["racing"] != 30 || snapshot.FilesProcessed != 1 {
		t.Fatalf("unexpected counters %+v", snapshot)
	}
}
