package osc

import (
	"testing"
)

func TestNewClient(t *testing.T) {
	c := NewClient("127.0.0.1", 57120)
	if c == nil {
		t.Fatal("expected non-nil client")
	}
	if c.client == nil {
		t.Fatal("expected non-nil internal client")
	}
}

func TestBoolToInt(t *testing.T) {
	if boolToInt(true) != 1 {
		t.Error("expected true to be 1")
	}
	if boolToInt(false) != 0 {
		t.Error("expected false to be 0")
	}
}
