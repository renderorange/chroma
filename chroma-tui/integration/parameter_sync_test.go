package integration

import (
	"testing"
	"time"

	"github.com/renderorange/chroma/chroma-tui/osc"
	"github.com/renderorange/chroma/chroma-tui/tui"
)

func TestParameterSync_PendingChangesPreventOverwrites(t *testing.T) {
	// This test specifically verifies that the settings bug fix works:
	// When user adjusts a parameter, the markPendingChange() call prevents
	// server state updates from overwriting the user's change.

	client := osc.NewClient("127.0.0.1", 57126)
	model := tui.NewModel(client)

	// Simulate the exact scenario from the bug:
	// 1. User has gain set to 0.5
	initialGain := float32(0.5)
	model.Gain = initialGain

	// 2. User adjusts gain using left/right keys (this should mark pending change)
	// We simulate this by calling the test helper methods
	model.SetFocused(tui.TestCtrlGain) // Use test helper to set focus
	model.AdjustFocused(0.05)          // This should now call markPendingChange()
	userAdjustedGain := model.Gain     // Should be ~0.7

	if userAdjustedGain <= initialGain {
		t.Errorf("Expected gain to increase from %f, got %f", initialGain, userAdjustedGain)
	}

	// 3. Server sends state update with old gain value (race condition)
	oldServerState := osc.State{
		Gain:         initialGain, // Old value
		FilterAmount: 0.8,
		BlendMode:    1,
	}

	// 4. Apply server state - this should NOT overwrite user's gain change
	model.ApplyState(oldServerState)

	// 5. Verify user's gain change is preserved
	if model.Gain != userAdjustedGain {
		t.Errorf("BUG REPRODUCTION: User's gain change was overwritten! Expected %f, got %f",
			userAdjustedGain, model.Gain)
	}

	// 6. Verify other parameters (without pending changes) are still updated
	if model.FilterAmount != 0.8 {
		t.Errorf("Expected filter amount to be updated (no pending change), got %f", model.FilterAmount)
	}
	if model.BlendMode != 1 {
		t.Errorf("Expected blend mode to be updated (no pending change), got %d", model.BlendMode)
	}

	t.Log("Settings bug fix verified: pending changes prevent parameter overwrites")
}

func TestParameterSync_MultipleConcurrentChanges(t *testing.T) {
	// Test that multiple rapid parameter changes are all preserved

	client := osc.NewClient("127.0.0.1", 57127)
	model := tui.NewModel(client)

	// Set initial values
	model.Gain = 0.5
	model.FilterCutoff = 1000
	model.OverdriveDrive = 0.3

	// User rapidly changes multiple parameters
	model.SetFocused(tui.TestCtrlGain)
	model.AdjustFocused(0.1) // Change gain
	gainAfterChange := model.Gain

	model.SetFocused(tui.TestCtrlFilterCutoff)
	model.AdjustFocused(0.1) // Change filter cutoff
	cutoffAfterChange := model.FilterCutoff

	model.SetFocused(tui.TestCtrlOverdriveDrive)
	model.AdjustFocused(0.1) // Change overdrive drive
	driveAfterChange := model.OverdriveDrive

	// Server sends old state (simulating network delay/latency)
	oldState := osc.State{
		Gain:           0.5, // Old values
		FilterCutoff:   1000,
		OverdriveDrive: 0.3,
		FilterAmount:   0.8,
	}

	// Apply old state - none of the user's changes should be overwritten
	model.ApplyState(oldState)

	// Verify all user changes are preserved
	if model.Gain != gainAfterChange {
		t.Errorf("Gain change not preserved: expected %f, got %f", gainAfterChange, model.Gain)
	}
	if model.FilterCutoff != cutoffAfterChange {
		t.Errorf("Filter cutoff change not preserved: expected %f, got %f", cutoffAfterChange, model.FilterCutoff)
	}
	if model.OverdriveDrive != driveAfterChange {
		t.Errorf("Overdrive drive change not preserved: expected %f, got %f", driveAfterChange, model.OverdriveDrive)
	}

	// Verify non-changed parameters are still updated
	if model.FilterAmount != 0.8 {
		t.Errorf("Filter amount not updated: expected 0.8, got %f", model.FilterAmount)
	}

	t.Log("Multiple concurrent changes preserved correctly")
}

func TestParameterSync_ToggleControlsWork(t *testing.T) {
	// Test that toggle controls (bool parameters) also work with pending changes

	client := osc.NewClient("127.0.0.1", 57128)
	model := tui.NewModel(client)

	// Test input freeze toggle
	model.InputFrozen = false
	model.SetFocused(tui.TestCtrlInputFreeze)
	model.ToggleFocused() // Should toggle to true and mark pending change

	// Server sends old state
	oldState := osc.State{
		InputFrozen: false, // Old value
		Gain:        1.0,
	}

	model.ApplyState(oldState)

	// User's toggle should be preserved
	if !model.InputFrozen {
		t.Error("Input freeze toggle not preserved by pending changes")
	}

	// Test granular freeze toggle
	model.GranularFrozen = false
	model.SetFocused(tui.TestCtrlGranularFreeze)
	model.ToggleFocused() // Should toggle to true and mark pending change

	// Server sends old state
	oldState = osc.State{
		InputFrozen: false, // Old value
		Gain:        1.0,
	}

	model.ApplyState(oldState)

	// User's toggle should be preserved
	if !model.InputFrozen {
		t.Error("Input freeze toggle not preserved by pending changes")
	}

	// Test granular freeze toggle
	model.GranularFrozen = false
	model.SetFocused(tui.TestCtrlGranularFreeze)
	model.ToggleFocused() // Should toggle to true and mark pending change

	model.ApplyState(oldState)

	if !model.GranularFrozen {
		t.Error("Granular freeze toggle not preserved by pending changes")
	}

	t.Log("Toggle controls work with pending changes")
}

func TestParameterSync_StaleChangesAreCleared(t *testing.T) {
	// Test that stale pending changes are eventually cleared

	client := osc.NewClient("127.0.0.1", 57129)
	model := tui.NewModel(client)

	// User changes a parameter
	model.Gain = 0.8
	model.SetFocused(tui.TestCtrlGain)
	model.AdjustFocused(0.05) // Should mark pending change
	userSetGain := model.Gain

	// Server sends different value
	state1 := osc.State{Gain: 0.9}
	model.ApplyState(state1)

	// User's change should be preserved initially
	if model.Gain != userSetGain {
		t.Errorf("User change should be preserved initially, got %f", model.Gain)
	}

	// Wait longer than the 500ms timeout
	time.Sleep(600 * time.Millisecond)

	// Now server should be able to update the value
	state2 := osc.State{Gain: 1.2}
	model.ApplyState(state2)

	if model.Gain != 1.2 {
		t.Errorf("Stale pending change not cleared, expected 1.2, got %f", model.Gain)
	}

	t.Log("Stale pending changes cleared correctly")
}

func TestParameterSync_SetBlendModePreserved(t *testing.T) {
	// Test that setBlendMode also works with pending changes

	client := osc.NewClient("127.0.0.1", 57130)
	model := tui.NewModel(client)

	// User changes blend mode
	model.SetBlendMode(2) // Should call markPendingChange for ctrlBlendMode

	// Server sends old blend mode
	oldState := osc.State{
		BlendMode: 0, // Old value
		Gain:      1.0,
	}

	model.ApplyState(oldState)

	// User's blend mode should be preserved
	if model.BlendMode != 2 {
		t.Errorf("Blend mode change not preserved: expected 2, got %d", model.BlendMode)
	}

	// Other parameters should still update
	if model.Gain != 1.0 {
		t.Errorf("Gain not updated: expected 1.0, got %f", model.Gain)
	}

	t.Log("Blend mode changes preserved correctly")
}
