package filedrop

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"path/filepath"
	"time"

	"gamestats/simulator/internal/sender"
)

const fileExtension = ".jsonl"

type Writer struct {
	directory string
}

func New(directory string) (*Writer, error) {
	if err := os.MkdirAll(directory, 0o755); err != nil {
		return nil, fmt.Errorf("output directory could not be created: %w", err)
	}
	return &Writer{directory: directory}, nil
}

func (writer *Writer) Deliver(ctx context.Context, matchId string, payloads [][]byte, pacing sender.Pacing, tally *sender.Tally) {
	file, err := os.OpenFile(filepath.Join(writer.directory, matchId+fileExtension), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644)
	if err != nil {
		tally.Sent.Add(int64(len(payloads)))
		tally.Failed.Add(int64(len(payloads)))
		return
	}
	defer file.Close()

	if pacing.EventsPerSecond > 0 {
		writeOneByOne(ctx, file, payloads, pacing.EventsPerSecond, tally)
		return
	}
	writeAtOnce(file, payloads, tally)
}

func writeAtOnce(file *os.File, payloads [][]byte, tally *sender.Tally) {
	tally.Sent.Add(int64(len(payloads)))
	content := append(bytes.Join(payloads, []byte("\n")), '\n')
	if _, err := file.Write(content); err != nil {
		tally.Failed.Add(int64(len(payloads)))
		return
	}
	tally.Accepted.Add(int64(len(payloads)))
}

func writeOneByOne(ctx context.Context, file *os.File, payloads [][]byte, eventsPerSecond float64, tally *sender.Tally) {
	interval := time.Duration(float64(time.Second) / eventsPerSecond)
	for _, payload := range payloads {
		tally.Sent.Add(1)
		if _, err := file.Write(append(append([]byte(nil), payload...), '\n')); err != nil {
			tally.Failed.Add(1)
		} else {
			tally.Accepted.Add(1)
		}
		select {
		case <-time.After(interval):
		case <-ctx.Done():
			return
		}
	}
}
