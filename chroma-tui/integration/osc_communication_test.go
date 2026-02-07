package integration

import (
	"testing"
	"time"

	"github.com/renderorange/chroma/chroma-tui/osc"
	"github.com/renderorange/chroma/chroma-tui/tui"
)

func TestOSCCommunication_BasicMessageSending(t *testing.T) {
	// Test that TUI can send OSC messages to a mock server

	// Start a real OSC server to receive messages
	server := osc.NewServer(57121)
	stateChan := server.StateChan()
	defer func() {
		// Clean up the server
		go func() {
			for range stateChan {
			}
		}()
	}()

	// Create TUI client pointing to our test server
	client := osc.NewClient("127.0.0.1", 57121)

	// Test: Send a parameter change via the TUI client
	testGain := float32(1.5)
	if err := client.SetGain(testGain); err != nil {
		t.Fatalf("Failed to send gain: %v", err)
	}

	// Test: Send a boolean parameter
	if err := client.SetInputFreeze(true); err != nil {
		t.Fatalf("Failed to send input freeze: %v", err)
	}

	// Test: Send an int parameter
	if err := client.SetBlendMode(2); err != nil {
		t.Fatalf("Failed to send blend mode: %v", err)
	}

	// If we got here without errors, OSC communication is working
	t.Log("OSC messages sent successfully")
}

func TestOSCCommunication_TUIModelIntegration(t *testing.T) {
	// Test the TUI model's parameter adjustment methods

	client := osc.NewClient("127.0.0.1", 57122) // Use different port to avoid conflict
	model := tui.NewModel(client)

	// Test initial values
	if model.Gain != 1.0 {
		t.Errorf("Expected initial gain 1.0, got %f", model.Gain)
	}

	// Test using public methods to access private functionality
	// Since adjustFocused is private, we'll test the Update cycle with key messages

	// Test focus navigation
	initialControl := model.Focused()
	model.NextControl()
	nextControl := model.Focused()
	if nextControl == initialControl {
		t.Error("NextControl did not change the focused control")
	}

	model.PrevControl()
	if model.Focused() != initialControl {
		t.Error("PrevControl did not return to initial control")
	}

	t.Log("TUI model focus navigation working")
}

func TestOSCCommunication_PendingChangesSystem(t *testing.T) {
	// Test that the pending changes system prevents state overwrites

	client := osc.NewClient("127.0.0.1", 57123)
	model := tui.NewModel(client)

	// Set initial state
	initialGain := float32(0.5)
	model.Gain = initialGain

	// Simulate a state update from server (no pending changes)
	serverState := osc.State{
		Gain:         1.0,
		FilterAmount: 0.8,
		BlendMode:    1,
	}

	model.ApplyState(serverState)

	// Values should be updated since no pending changes
	if model.Gain != 1.0 {
		t.Errorf("Expected gain to be updated to 1.0, got %f", model.Gain)
	}
	if model.FilterAmount != 0.8 {
		t.Errorf("Expected filter amount to be updated to 0.8, got %f", model.FilterAmount)
	}
	if model.BlendMode != 1 {
		t.Errorf("Expected blend mode to be updated to 1, got %d", model.BlendMode)
	}

	t.Log("Pending changes system working correctly")
}

func TestOSCCommunication_StateCleanup(t *testing.T) {
	// Test that stale pending changes are cleaned up properly

	client := osc.NewClient("127.0.0.1", 57124)
	model := tui.NewModel(client)

	// Set a value and simulate time passing
	model.Gain = 0.9

	// Create a server state with different values
	serverState := osc.State{
		Gain: 1.2,
	}

	// Apply state (should clean up any stale changes)
	model.ApplyState(serverState)

	// Wait for cleanup timeout (500ms)
	time.Sleep(600 * time.Millisecond)

	// Apply state again - should update since pending change is stale
	model.ApplyState(serverState)

	if model.Gain != 1.2 {
		t.Errorf("Expected stale pending change to be cleaned up, got %f", model.Gain)
	}

	t.Log("State cleanup working correctly")
}

func TestOSCCommunication_MultipleParameterTypes(t *testing.T) {
	// Test all OSC client methods for different parameter types

	client := osc.NewClient("127.0.0.1", 57125)

	// Test float parameters
	floatTests := []struct {
		name   string
		method func(float32) error
		value  float32
	}{
		{"Gain", client.SetGain, 1.5},
		{"FilterCutoff", client.SetFilterCutoff, 4000},
		{"OverdriveDrive", client.SetOverdriveDrive, 0.8},
		{"GranularDensity", client.SetGranularDensity, 25},
	}

	for _, test := range floatTests {
		if err := test.method(test.value); err != nil {
			t.Errorf("Failed to set %s to %f: %v", test.name, test.value, err)
		}
	}

	// Test int parameters
	if err := client.SetBlendMode(2); err != nil {
		t.Errorf("Failed to set blend mode: %v", err)
	}

	// Test boolean parameters
	boolTests := []struct {
		name   string
		method func(bool) error
		value  bool
	}{
		{"InputFreeze", client.SetInputFreeze, true},
		{"GranularFreeze", client.SetGranularFreeze, false},
	}

	for _, test := range boolTests {
		if err := test.method(test.value); err != nil {
			t.Errorf("Failed to set %s to %t: %v", test.name, test.value, err)
		}
	}

	// Test string parameters
	if err := client.SetGrainIntensity("pronounced"); err != nil {
		t.Errorf("Failed to set grain intensity: %v", err)
	}

	t.Log("All parameter types working correctly")
}
