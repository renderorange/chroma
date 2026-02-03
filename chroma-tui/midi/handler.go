//go:build cgo

package midi

import (
	"fmt"

	"gitlab.com/gomidi/midi/v2"
	"gitlab.com/gomidi/midi/v2/drivers"
	_ "gitlab.com/gomidi/midi/v2/drivers/rtmididrv"

	"github.com/renderorange/chroma/chroma-tui/config"
	"github.com/renderorange/chroma/chroma-tui/osc"
)

type Handler struct {
	client *osc.Client
	config config.Config
	port   drivers.In
	stop   func()
}

func NewHandler(client *osc.Client, cfg config.Config) *Handler {
	return &Handler{
		client: client,
		config: cfg,
	}
}

func (h *Handler) Start() error {
	ins := midi.GetInPorts()
	if len(ins) == 0 {
		return fmt.Errorf("no MIDI input ports found")
	}

	// Use first available port
	h.port = ins[0]

	stop, err := midi.ListenTo(h.port, h.handleMessage)
	if err != nil {
		return err
	}
	h.stop = stop

	return nil
}

func (h *Handler) Stop() {
	if h.stop != nil {
		h.stop()
	}
}

func (h *Handler) PortName() string {
	if h.port != nil {
		return h.port.String()
	}
	return ""
}

func (h *Handler) handleMessage(msg midi.Message, timestamp int32) {
	var ch, key, vel uint8
	var cc, val uint8

	switch {
	case msg.GetControlChange(&ch, &cc, &val):
		h.handleCC(int(cc), float32(val)/127.0)
	case msg.GetNoteOn(&ch, &key, &vel):
		if vel > 0 {
			h.handleNoteOn(int(key))
		}
	}
}

func (h *Handler) handleCC(cc int, value float32) {
	cfg := h.config.CC

	switch cc {
	case cfg["gain"]:
		h.client.SetGain(value * 2)
	case cfg["input_freeze_len"]:
		h.client.SetInputFreezeLength(0.05 + value*0.45)
	case cfg["filter_amount"]:
		h.client.SetFilterAmount(value)
	case cfg["filter_cutoff"]:
		h.client.SetFilterCutoff(200 + value*7800)
	case cfg["filter_resonance"]:
		h.client.SetFilterResonance(value)
	case cfg["granular_density"]:
		h.client.SetGranularDensity(1 + value*49)
	case cfg["granular_size"]:
		h.client.SetGranularSize(0.01 + value*0.49)
	case cfg["granular_mix"]:
		h.client.SetGranularMix(value)
	case cfg["reverb_delay_blend"]:
		h.client.SetReverbDelayBlend(value)
	case cfg["decay_time"]:
		h.client.SetDecayTime(0.1 + value*9.9)
	case cfg["dry_wet"]:
		h.client.SetDryWet(value)
	}
}

func (h *Handler) handleNoteOn(note int) {
	cfg := h.config.Notes

	switch note {
	case cfg["input_freeze"]:
		h.client.SendInt("/chroma/inputFreeze", 1)
	case cfg["granular_freeze"]:
		h.client.SendInt("/chroma/granularFreeze", 1)
	case cfg["mode_mirror"]:
		h.client.SetBlendMode(0)
	case cfg["mode_complement"]:
		h.client.SetBlendMode(1)
	case cfg["mode_transform"]:
		h.client.SetBlendMode(2)
	}
}
