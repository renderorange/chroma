package osc

import (
	"testing"

	"github.com/hypebeast/go-osc/osc"
)

func TestNewServer(t *testing.T) {
	s := NewServer(57121)
	if s == nil {
		t.Fatal("expected non-nil server")
	}
	if s.server == nil {
		t.Fatal("expected non-nil internal server")
	}
	if s.stateChan == nil {
		t.Fatal("expected non-nil state channel")
	}
	if cap(s.stateChan) != 10 {
		t.Errorf("expected state channel capacity 10, got %d", cap(s.stateChan))
	}
}

func TestSpectrumMessageHandler(t *testing.T) {
	s := NewServer(57122)

	// Create a spectrum message with 8 float32 values
	msg := osc.NewMessage("/chroma/spectrum")
	for i := 0; i < 8; i++ {
		msg.Append(float32(i) * 0.1)
	}

	// Dispatch the message to trigger the handler
	d := s.server.Dispatcher
	d.Dispatch(msg)

	// Check that the state was updated
	select {
	case state := <-s.stateChan:
		for i := 0; i < 8; i++ {
			expected := float32(i) * 0.1
			if state.Spectrum[i] != expected {
				t.Errorf("expected spectrum[%d] = %f, got %f", i, expected, state.Spectrum[i])
			}
		}
	default:
		t.Error("expected state update on state channel")
	}
}

func TestSpectrumMessageHandlerWithFloat64(t *testing.T) {
	s := NewServer(57123)

	// Create a spectrum message with 8 float64 values
	msg := osc.NewMessage("/chroma/spectrum")
	for i := 0; i < 8; i++ {
		msg.Append(float64(i) * 0.1)
	}

	// Dispatch the message to trigger the handler
	d := s.server.Dispatcher
	d.Dispatch(msg)

	// Check that the state was updated
	select {
	case state := <-s.stateChan:
		for i := 0; i < 8; i++ {
			expected := float32(i) * 0.1
			if state.Spectrum[i] != expected {
				t.Errorf("expected spectrum[%d] = %f, got %f", i, expected, state.Spectrum[i])
			}
		}
	default:
		t.Error("expected state update on state channel")
	}
}

func TestSpectrumMessageHandlerTooFewArguments(t *testing.T) {
	s := NewServer(57124)

	// Create a spectrum message with only 5 values (should be ignored)
	msg := osc.NewMessage("/chroma/spectrum")
	for i := 0; i < 5; i++ {
		msg.Append(float32(i) * 0.1)
	}

	// Dispatch the message to trigger the handler
	d := s.server.Dispatcher
	d.Dispatch(msg)

	// Check that no state was sent (channel should be empty)
	select {
	case <-s.stateChan:
		t.Error("expected no state update with too few arguments")
	default:
		// This is expected - no state should be sent
	}
}

func TestStateChan(t *testing.T) {
	s := NewServer(57125)
	if s.StateChan() == nil {
		t.Fatal("expected non-nil state channel")
	}
}
