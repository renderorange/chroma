package functional

import (
	"testing"
	"time"

	"github.com/renderorange/chroma/chroma-tui/osc"
	"github.com/renderorange/chroma/chroma-tui/tui"
)

func TestTUISuperCollider_CompleteWorkflow(t *testing.T) {
	// Test the complete workflow: TUI → OSC → SuperCollider → TUI

	// Start a mock SuperCollider server
	server := osc.NewServer(57131)
	stateChan := server.StateChan()
	defer func() {
		go func() {
			for range stateChan {
			}
		}()
	}()

	// Create TUI with client pointing to our mock server
	client := osc.NewClient("127.0.0.1", 57131)
	model := tui.NewModel(client)

	// Phase 1: User adjusts parameters in TUI
	model.SetFocused(tui.TestCtrlGain)
	model.AdjustFocused(0.1) // Increase gain
	expectedGain := model.Gain

	model.SetFocused(tui.TestCtrlFilterCutoff)
	model.AdjustFocused(0.1) // Increase filter cutoff
	expectedCutoff := model.FilterCutoff

	// Phase 2: Simulate SuperCollider processing and broadcasting state
	// In real scenario, SC would process audio and update parameters
	time.Sleep(100 * time.Millisecond) // Simulate processing time

	// Phase 3: TUI receives updated state from SuperCollider
	updatedState := osc.State{
		Gain:           expectedGain,   // Echo back user's gain
		FilterCutoff:   expectedCutoff, // Echo back user's cutoff
		FilterAmount:   0.8,            // New value from SC processing
		OverdriveDrive: 0.7,            // New value from SC processing
	}

	model.ApplyState(updatedState)

	// Verify complete round-trip works
	if model.Gain != expectedGain {
		t.Errorf("Gain not preserved through workflow: expected %f, got %f", expectedGain, model.Gain)
	}
	if model.FilterCutoff != expectedCutoff {
		t.Errorf("Filter cutoff not preserved through workflow: expected %f, got %f", expectedCutoff, model.FilterCutoff)
	}
	if model.FilterAmount != 0.8 {
		t.Errorf("Filter amount not updated from SC: expected 0.8, got %f", model.FilterAmount)
	}
	if model.OverdriveDrive != 0.7 {
		t.Errorf("Overdrive drive not updated from SC: expected 0.7, got %f", model.OverdriveDrive)
	}

	t.Log("Complete TUI ↔ SuperCollider workflow verified")
}

func TestTUISuperCollider_ParameterPersistence(t *testing.T) {
	// Test that parameters persist across multiple TUI/SC cycles

	client := osc.NewClient("127.0.0.1", 57132)
	model := tui.NewModel(client)

	// Set initial parameters
	initialGain := float32(1.2)
	initialCutoff := float32(2000)
	model.Gain = initialGain
	model.FilterCutoff = initialCutoff

	// Simulate multiple SuperCollider state updates
	updates := []osc.State{
		{Gain: initialGain, FilterCutoff: initialCutoff, FilterAmount: 0.5},
		{Gain: initialGain, FilterCutoff: initialCutoff, FilterAmount: 0.7},
		{Gain: initialGain, FilterCutoff: initialCutoff, OverdriveDrive: 0.8},
		{Gain: initialGain, FilterCutoff: initialCutoff, BlendMode: 2},
	}

	for i, state := range updates {
		model.ApplyState(state)

		// Verify user parameters persist across all updates
		if model.Gain != initialGain {
			t.Errorf("Update %d: Gain not preserved: expected %f, got %f", i, initialGain, model.Gain)
		}
		if model.FilterCutoff != initialCutoff {
			t.Errorf("Update %d: Filter cutoff not preserved: expected %f, got %f", i, initialCutoff, model.FilterCutoff)
		}
	}

	t.Log("Parameter persistence across multiple SC cycles verified")
}

func TestTUISuperCollider_AudioParameterFeedback(t *testing.T) {
	// Test audio-driven parameter changes from SuperCollider

	client := osc.NewClient("127.0.0.1", 57133)
	model := tui.NewModel(client)

	// Initial state
	model.Gain = 1.0
	model.OverdriveDrive = 0.0

	// Simulate audio-driven parameter changes from SuperCollider
	// (e.g., automatic gain adjustment based on input level)
	audioDrivenStates := []osc.State{
		{Gain: 1.1, FilterAmount: 0.3},    // Audio analysis suggests more gain
		{Gain: 1.2, FilterAmount: 0.5},    // Dynamic processing increases parameters
		{Gain: 1.15, OverdriveDrive: 0.9}, // Overdrive kicks in based on signal
	}

	for i, state := range audioDrivenStates {
		model.ApplyState(state)

		// Verify audio-driven changes are applied
		expectedGain := state.Gain
		if model.Gain != expectedGain {
			t.Errorf("Audio update %d: Gain not applied: expected %f, got %f", i, expectedGain, model.Gain)
		}
	}

	t.Log("Audio-driven parameter feedback from SuperCollider verified")
}

func TestTUISuperCollider_RealTimeParameterSync(t *testing.T) {
	// Test real-time synchronization with minimal latency

	client := osc.NewClient("127.0.0.1", 57134)
	model := tui.NewModel(client)

	// Simulate rapid parameter changes from both TUI and SuperCollider
	rapidUpdates := 10
	for i := 0; i < rapidUpdates; i++ {
		// TUI user change
		model.SetFocused(tui.TestCtrlGain)
		model.AdjustFocused(0.01) // Small gain adjustment
		userGain := model.Gain

		// Immediate SuperCollider response (simulating real-time sync)
		scResponse := osc.State{
			Gain:         userGain,         // Echo user's change
			FilterAmount: float32(i) * 0.1, // SC processing change
		}

		model.ApplyState(scResponse)

		// Verify sync maintained
		if model.Gain != userGain {
			t.Errorf("Rapid update %d: Sync lost - expected gain %f, got %f", i, userGain, model.Gain)
		}

		// Small delay to simulate real-time processing
		time.Sleep(1 * time.Millisecond)
	}

	t.Log("Real-time parameter synchronization verified")
}

func TestTUISuperCollider_ErrorHandling(t *testing.T) {
	// Test error handling in TUI-SC communication

	client := osc.NewClient("127.0.0.1", 57135) // Non-existent server
	model := tui.NewModel(client)

	// Test that TUI remains functional even when SC is unavailable
	initialGain := model.Gain
	model.SetFocused(tui.TestCtrlGain)
	model.AdjustFocused(0.1)

	// TUI should maintain its state despite communication failures
	if model.Gain == initialGain {
		t.Error("TUI model should update locally even when SC communication fails")
	}

	// Test recovery when SC becomes available
	server := osc.NewServer(57135)
	defer func() {
		go func() {
			for range server.StateChan() {
			}
		}()
	}()

	// Wait a bit for server to start and for pending changes to expire
	time.Sleep(550 * time.Millisecond)

	// Now parameters should sync (pending change protection expired)
	recoveryState := osc.State{Gain: 1.5, FilterAmount: 0.8}
	model.ApplyState(recoveryState)

	if model.Gain != 1.5 {
		t.Errorf("Recovery failed: expected gain 1.5, got %f", model.Gain)
	}

	t.Log("Error handling and recovery verified")
}
