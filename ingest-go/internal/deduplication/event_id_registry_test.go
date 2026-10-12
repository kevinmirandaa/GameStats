package deduplication_test

import (
	"sync"
	"sync/atomic"
	"testing"

	"gamestats/ingest/internal/deduplication"
)

func TestSecondRegistrationOfTheSameIdIsRefused(t *testing.T) {
	registry := deduplication.NewEventIdRegistry()
	if !registry.RegisterIfNew("evt-1") {
		t.Fatal("first registration should succeed")
	}
	if registry.RegisterIfNew("evt-1") {
		t.Fatal("second registration should be refused")
	}
}

func TestOnlyOneConcurrentRegistrationOfTheSameIdSucceeds(t *testing.T) {
	registry := deduplication.NewEventIdRegistry()
	var successfulRegistrations atomic.Int64
	var group sync.WaitGroup
	for attempt := 0; attempt < 200; attempt++ {
		group.Add(1)
		go func() {
			defer group.Done()
			if registry.RegisterIfNew("shared-id") {
				successfulRegistrations.Add(1)
			}
		}()
	}
	group.Wait()
	if successfulRegistrations.Load() != 1 {
		t.Fatalf("exactly one registration should succeed but %d did", successfulRegistrations.Load())
	}
}

func TestForgottenIdCanBeRegisteredAgain(t *testing.T) {
	registry := deduplication.NewEventIdRegistry()
	registry.RegisterIfNew("evt-1")
	registry.Forget("evt-1")
	if !registry.RegisterIfNew("evt-1") {
		t.Fatal("a forgotten id should be accepted again")
	}
}
