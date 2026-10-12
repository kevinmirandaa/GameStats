package rejectedlog

import (
	"encoding/json"
	"log/slog"
	"os"
	"sync"
	"time"
)

const maximumPayloadCharacters = 1024

type Entry struct {
	RejectedAt time.Time `json:"rejectedAt"`
	EventId    string    `json:"eventId,omitempty"`
	Reasons    []string  `json:"reasons"`
	Payload    string    `json:"payload"`
}

type Log struct {
	mutex         sync.Mutex
	recentLimit   int
	recentEntries []Entry
	file          *os.File
	clock         func() time.Time
}

func Open(filePath string, recentLimit int) (*Log, error) {
	rejections := &Log{recentLimit: recentLimit, clock: time.Now}
	if filePath == "" {
		return rejections, nil
	}
	file, err := os.OpenFile(filePath, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0o644)
	if err != nil {
		return nil, err
	}
	rejections.file = file
	return rejections, nil
}

func (rejections *Log) Record(eventId string, reasons []string, payload []byte) {
	entry := Entry{
		RejectedAt: rejections.clock().UTC(),
		EventId:    eventId,
		Reasons:    reasons,
		Payload:    truncate(string(payload)),
	}
	rejections.mutex.Lock()
	defer rejections.mutex.Unlock()
	rejections.recentEntries = append(rejections.recentEntries, entry)
	if len(rejections.recentEntries) > rejections.recentLimit {
		rejections.recentEntries = rejections.recentEntries[len(rejections.recentEntries)-rejections.recentLimit:]
	}
	rejections.appendToFile(entry)
}

func (rejections *Log) Recent() []Entry {
	rejections.mutex.Lock()
	defer rejections.mutex.Unlock()
	return append([]Entry{}, rejections.recentEntries...)
}

func (rejections *Log) Close() error {
	rejections.mutex.Lock()
	defer rejections.mutex.Unlock()
	if rejections.file == nil {
		return nil
	}
	return rejections.file.Close()
}

func (rejections *Log) appendToFile(entry Entry) {
	if rejections.file == nil {
		return
	}
	line, err := json.Marshal(entry)
	if err != nil {
		slog.Error("rejected event entry could not be encoded", "error", err)
		return
	}
	if _, err := rejections.file.Write(append(line, '\n')); err != nil {
		slog.Error("rejected event entry could not be written", "error", err)
	}
}

func truncate(text string) string {
	runes := []rune(text)
	if len(runes) <= maximumPayloadCharacters {
		return text
	}
	return string(runes[:maximumPayloadCharacters])
}
