package inbox

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"time"

	"gamestats/ingest/internal/ingestion"
	"gamestats/ingest/internal/metrics"
	"gamestats/ingest/internal/rejectedlog"
)

const (
	processedDirectoryName = "processed"
	failedDirectoryName    = "failed"
	maximumFileBytes       = 64 << 20
	failedSampleBytes      = 1024
)

var ignoredSuffixes = []string{".tmp", ".part", ".swp", "~"}

type Ingestor interface {
	Ingest(rawEvent []byte) ingestion.Result
}

type fileState struct {
	size       int64
	modifiedAt time.Time
}

type tally struct {
	accepted int
	rejected int
}

type Watcher struct {
	directory    string
	pollInterval time.Duration
	ingestor     Ingestor
	rejections   *rejectedlog.Log
	counters     *metrics.Counters
	tailOffsets  map[string]int64
	lastObserved map[string]fileState
}

func New(directory string, pollInterval time.Duration, ingestor Ingestor, rejections *rejectedlog.Log, counters *metrics.Counters) *Watcher {
	return &Watcher{
		directory:    directory,
		pollInterval: pollInterval,
		ingestor:     ingestor,
		rejections:   rejections,
		counters:     counters,
		tailOffsets:  make(map[string]int64),
		lastObserved: make(map[string]fileState),
	}
}

func (watcher *Watcher) Run(ctx context.Context) {
	ticker := time.NewTicker(watcher.pollInterval)
	defer ticker.Stop()
	for {
		watcher.ScanOnce()
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

func (watcher *Watcher) ScanOnce() {
	entries, err := os.ReadDir(watcher.directory)
	if err != nil {
		slog.Error("inbox directory could not be read", "directory", watcher.directory, "error", err)
		return
	}
	present := make(map[string]bool)
	for _, entry := range entries {
		if entry.IsDir() || isIgnored(entry.Name()) {
			continue
		}
		path := filepath.Join(watcher.directory, entry.Name())
		switch strings.ToLower(filepath.Ext(entry.Name())) {
		case ".jsonl", ".ndjson":
			present[path] = true
			watcher.followGrowingFile(path)
		case ".json":
			present[path] = true
			watcher.consumeWhenStable(path)
		}
	}
	watcher.forgetMissingFiles(present)
}

func (watcher *Watcher) followGrowingFile(path string) {
	info, err := os.Stat(path)
	if err != nil {
		return
	}
	stable := watcher.recordObservation(path, info)
	offset := watcher.tailOffsets[path]
	if info.Size() < offset {
		offset = 0
	}
	if info.Size() == offset {
		watcher.tailOffsets[path] = offset
		return
	}
	data, err := readFrom(path, offset)
	if err != nil {
		slog.Error("inbox file could not be read", "file", path, "error", err)
		return
	}
	consumed := bytes.LastIndexByte(data, '\n') + 1
	if stable && consumed < len(data) {
		consumed = len(data)
	}
	if consumed == 0 {
		return
	}
	result := watcher.ingestAll(nonBlankLines(data[:consumed]))
	watcher.tailOffsets[path] = offset + int64(consumed)
	slog.Info("lines read from inbox file", "file", filepath.Base(path), "accepted", result.accepted, "rejected", result.rejected)
}

func (watcher *Watcher) consumeWhenStable(path string) {
	info, err := os.Stat(path)
	if err != nil {
		return
	}
	if !watcher.recordObservation(path, info) {
		return
	}
	payloads, err := readPayloads(path, info.Size())
	if err != nil {
		watcher.markFailed(path, err)
		return
	}
	result := watcher.ingestAll(payloads)
	if err := moveInto(path, filepath.Join(watcher.directory, processedDirectoryName)); err != nil {
		slog.Error("inbox file could not be archived", "file", path, "error", err)
		return
	}
	delete(watcher.lastObserved, path)
	watcher.counters.FileProcessed()
	slog.Info("inbox file ingested", "file", filepath.Base(path), "events", len(payloads), "accepted", result.accepted, "rejected", result.rejected)
}

func (watcher *Watcher) recordObservation(path string, info os.FileInfo) bool {
	current := fileState{size: info.Size(), modifiedAt: info.ModTime()}
	previous, known := watcher.lastObserved[path]
	watcher.lastObserved[path] = current
	return known && previous == current
}

func (watcher *Watcher) ingestAll(payloads [][]byte) tally {
	var result tally
	for _, payload := range payloads {
		if watcher.ingestor.Ingest(payload).Outcome == ingestion.Accepted {
			result.accepted++
		} else {
			result.rejected++
		}
	}
	return result
}

func (watcher *Watcher) markFailed(path string, cause error) {
	watcher.counters.FileFailed()
	watcher.rejections.Record("", []string{fmt.Sprintf("file %s could not be used: %v", filepath.Base(path), cause)}, sampleOf(path))
	if err := moveInto(path, filepath.Join(watcher.directory, failedDirectoryName)); err != nil {
		slog.Error("inbox file could not be moved to the failed folder", "file", path, "error", err)
	}
	delete(watcher.lastObserved, path)
	slog.Warn("inbox file rejected", "file", filepath.Base(path), "reason", cause)
}

func (watcher *Watcher) forgetMissingFiles(present map[string]bool) {
	for path := range watcher.tailOffsets {
		if !present[path] {
			delete(watcher.tailOffsets, path)
		}
	}
	for path := range watcher.lastObserved {
		if !present[path] {
			delete(watcher.lastObserved, path)
		}
	}
}

func isIgnored(name string) bool {
	if strings.HasPrefix(name, ".") {
		return true
	}
	for _, suffix := range ignoredSuffixes {
		if strings.HasSuffix(name, suffix) {
			return true
		}
	}
	return false
}

func readFrom(path string, offset int64) ([]byte, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	if _, err := file.Seek(offset, io.SeekStart); err != nil {
		return nil, err
	}
	return io.ReadAll(io.LimitReader(file, maximumFileBytes))
}

func nonBlankLines(data []byte) [][]byte {
	var lines [][]byte
	for _, line := range bytes.Split(data, []byte("\n")) {
		if trimmed := bytes.TrimSpace(line); len(trimmed) > 0 {
			lines = append(lines, trimmed)
		}
	}
	return lines
}

func readPayloads(path string, size int64) ([][]byte, error) {
	if size > maximumFileBytes {
		return nil, fmt.Errorf("file is larger than %d bytes", maximumFileBytes)
	}
	content, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	trimmed := bytes.TrimSpace(content)
	switch {
	case len(trimmed) == 0:
		return nil, errors.New("file is empty")
	case trimmed[0] == '[':
		return payloadsOfArray(trimmed)
	case trimmed[0] == '{':
		return payloadsOfObject(trimmed)
	default:
		return nil, errors.New("file must contain a JSON array of events, an object with an events array, or one event object")
	}
}

func payloadsOfArray(content []byte) ([][]byte, error) {
	var elements []json.RawMessage
	if err := json.Unmarshal(content, &elements); err != nil {
		return nil, fmt.Errorf("invalid JSON array: %w", err)
	}
	payloads := make([][]byte, len(elements))
	for position, element := range elements {
		payloads[position] = element
	}
	return payloads, nil
}

func payloadsOfObject(content []byte) ([][]byte, error) {
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(content, &fields); err != nil {
		return nil, fmt.Errorf("invalid JSON object: %w", err)
	}
	if events, hasEvents := fields["events"]; hasEvents {
		return payloadsOfArray(events)
	}
	return [][]byte{content}, nil
}

func moveInto(path, directory string) error {
	if err := os.MkdirAll(directory, 0o755); err != nil {
		return err
	}
	target := filepath.Join(directory, filepath.Base(path))
	if _, err := os.Stat(target); err == nil {
		target = fmt.Sprintf("%s.%d", target, time.Now().UnixNano())
	}
	return os.Rename(path, target)
}

func sampleOf(path string) []byte {
	file, err := os.Open(path)
	if err != nil {
		return nil
	}
	defer file.Close()
	sample, err := io.ReadAll(io.LimitReader(file, failedSampleBytes))
	if err != nil {
		return nil
	}
	return sample
}
