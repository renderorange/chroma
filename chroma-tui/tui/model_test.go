package tui

import (
	"testing"
	"time"

	"github.com/renderorange/chroma/chroma-tui/osc"
)

func TestTUIModel_ConcurrentUpdatePreservesUserChanges(t *testing.T) {
	// Create a model with real client (to avoid nil pointer)
	client := osc.NewClient("127.0.0.1", 57120)
	model := NewModel(client)

	// Initial state
	initialGain := float32(0.5)
	model.Gain = initialGain

	// Simulate user adjusting gain from 0.5 to 0.7
	model.adjustFocused(0.05) // This should set gain to 0.7 and send OSC message
	adjustedGain := model.Gain

	if adjustedGain <= initialGain {
		t.Errorf("expected gain to increase from %f, got %f", initialGain, adjustedGain)
	}

	// Simulate server broadcasting state (but with old gain value due to race condition)
	serverState := osc.State{
		Gain: initialGain, // Server still has old value
	}

	// Apply server state - this currently overwrites user's change
	model.ApplyState(serverState)

	// This demonstrates the bug: user's change was lost
	if model.Gain == initialGain {
		t.Errorf("BUG: User's gain change was reverted by server update. Expected ~%f, got %f",
			adjustedGain, model.Gain)
	}
}

func TestTUIModel_PendingChangePreventsOverwrite(t *testing.T) {
	// This test shows what SHOULD happen after implementing the fix
	client := osc.NewClient("127.0.0.1", 57120)
	model := NewModel(client)

	initialGain := float32(0.5)
	model.Gain = initialGain

	// Simulate user adjusting gain
	model.adjustFocused(0.05)
	adjustedGain := model.Gain

	// Simulate server broadcasting old state
	serverState := osc.State{
		Gain: initialGain,
	}

	// After implementing pending changes, this should preserve user's adjustment
	model.ApplyState(serverState)

	// User's change should be preserved
	if model.Gain != adjustedGain {
		t.Errorf("expected pending gain change to be preserved. Expected %f, got %f",
			adjustedGain, model.Gain)
	}
}

func TestTUIModel_MultipleConcurrentChanges(t *testing.T) {
	client := osc.NewClient("127.0.0.1", 57120)
	model := NewModel(client)

	// Set multiple initial values
	model.Gain = 0.5
	model.FilterCutoff = 1000

	// Simulate user changing multiple controls rapidly
	model.focused = ctrlGain
	model.adjustFocused(0.1) // Change gain
	adjustedGain := model.Gain

	model.focused = ctrlFilterCutoff
	model.adjustFocused(0.1) // Change filter cutoff
	adjustedCutoff := model.FilterCutoff

	// Server sends update with old values for both
	serverState := osc.State{
		Gain:         0.5,  // Old value
		FilterCutoff: 1000, // Old value
	}

	model.ApplyState(serverState)

	// Both pending changes should be preserved
	if model.Gain != adjustedGain {
		t.Errorf("expected pending gain change to be preserved. Expected %f, got %f",
			adjustedGain, model.Gain)
	}

	if model.FilterCutoff != adjustedCutoff {
		t.Errorf("expected pending filter cutoff change to be preserved. Expected %f, got %f",
			adjustedCutoff, model.FilterCutoff)
	}
}

func TestTUIModel_StalePendingChangeCleanup(t *testing.T) {
	client := osc.NewClient("127.0.0.1", 57120)
	model := NewModel(client)

	// Simulate user changing gain
	model.focused = ctrlGain
	model.adjustFocused(0.1)

	// Manually set an old timestamp to simulate stale change
	model.pendingChanges[ctrlGain] = model.pendingChanges[ctrlGain].Add(-3 * time.Second)

	// Apply state should clear stale changes and update the value
	serverState := osc.State{
		Gain: 1.0, // New value from server
	}

	model.ApplyState(serverState)

	// Stale pending change should be cleared and server value applied
	if model.Gain != 1.0 {
		t.Errorf("expected stale pending change to be cleared and server value applied. Expected 1.0, got %f",
			model.Gain)
	}
}

func TestTUIModel_NonPendingControlsUpdated(t *testing.T) {
	client := osc.NewClient("127.0.0.1", 57120)
	model := NewModel(client)

	// Set initial values
	model.Gain = 0.5
	model.FilterCutoff = 1000

	// User changes only gain
	model.focused = ctrlGain
	model.adjustFocused(0.1)
	adjustedGain := model.Gain

	// Server sends new values for both controls
	serverState := osc.State{
		Gain:         0.8,  // Different from user's change
		FilterCutoff: 2000, // New value for unchanged control
	}

	model.ApplyState(serverState)

	// Gain should be preserved (user's pending change)
	if model.Gain != adjustedGain {
		t.Errorf("expected pending gain change to be preserved. Expected %f, got %f",
			adjustedGain, model.Gain)
	}

	// Filter cutoff should be updated (no pending change)
	if model.FilterCutoff != 2000 {
		t.Errorf("expected non-pending filter cutoff to be updated. Expected 2000, got %f",
			model.FilterCutoff)
	}
}

func TestTUIModel_ToggleControlsWithPendingChanges(t *testing.T) {
	client := osc.NewClient("127.0.0.1", 57120)
	model := NewModel(client)

	// Initial state
	model.InputFrozen = false
	model.GranularFrozen = false

	// Toggle input freeze
	model.focused = ctrlInputFreeze
	model.toggleFocused()

	// Server sends old state
	serverState := osc.State{
		InputFrozen:    false, // Old value
		GranularFrozen: false,
	}

	model.ApplyState(serverState)

	// User's change should be preserved
	if !model.InputFrozen {
		t.Error("expected pending input freeze change to be preserved")
	}

	// Now toggle granular freeze
	model.focused = ctrlGranularFreeze
	model.toggleFocused()

	// Server sends new state
	serverState2 := osc.State{
		InputFrozen:    true,  // Should match current state
		GranularFrozen: false, // Old value
	}

	model.ApplyState(serverState2)

	if !model.GranularFrozen {
		t.Error("expected pending granular freeze change to be preserved")
	}
}
