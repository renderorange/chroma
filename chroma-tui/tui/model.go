package tui

import (
	"time"

	"github.com/renderorange/chroma/chroma-tui/osc"
)

type control int

const (
	ctrlGain control = iota
	ctrlInputFreezeLen
	ctrlInputFreeze
	ctrlFilterAmount
	ctrlFilterCutoff
	ctrlFilterResonance
	ctrlOverdriveDrive
	ctrlOverdriveTone
	ctrlOverdriveMix
	ctrlGranularDensity
	ctrlGranularSize
	ctrlGranularPitchScatter
	ctrlGranularPosScatter
	ctrlGranularMix
	ctrlGranularFreeze
	ctrlGrainIntensity
	ctrlReverbDelayBlend
	ctrlDecayTime
	ctrlShimmerPitch
	ctrlDelayTime
	ctrlModRate
	ctrlModDepth
	ctrlReverbDelayMix
	ctrlBlendMode
	ctrlDryWet
	ctrlCount
)

type Model struct {
	// State
	Gain                 float32
	InputFrozen          bool
	InputFreezeLength    float32
	FilterAmount         float32
	FilterCutoff         float32
	FilterResonance      float32
	OverdriveDrive       float32
	OverdriveTone        float32
	OverdriveMix         float32
	GranularDensity      float32
	GranularSize         float32
	GranularPitchScatter float32
	GranularPosScatter   float32
	GranularMix          float32
	GranularFrozen       bool
	GrainIntensity       string // "subtle" or "pronounced"
	ReverbDelayBlend     float32
	DecayTime            float32
	ShimmerPitch         float32
	DelayTime            float32
	ModRate              float32
	ModDepth             float32
	ReverbDelayMix       float32
	BlendMode            int
	DryWet               float32

	// UI state
	focused   control
	connected bool
	midiPort  string
	width     int
	height    int

	// Visualizer state
	Spectrum [8]float32

	// Pending changes tracking
	pendingChanges map[control]time.Time

	// OSC
	client *osc.Client
}

func NewModel(client *osc.Client) Model {
	return Model{
		// Defaults matching Chroma.sc
		Gain:                 1.0,
		InputFreezeLength:    0.1,
		FilterAmount:         0.5,
		FilterCutoff:         2000,
		FilterResonance:      0.3,
		OverdriveDrive:       0.5,
		OverdriveTone:        0.7,
		OverdriveMix:         0.0,
		GranularDensity:      20,
		GranularSize:         0.15,
		GranularPitchScatter: 0.2,
		GranularPosScatter:   0.3,
		GranularMix:          0.5,
		GrainIntensity:       "subtle",
		ReverbDelayBlend:     0.5,
		DecayTime:            3,
		ShimmerPitch:         12,
		DelayTime:            0.3,
		ModRate:              0.5,
		ModDepth:             0.3,
		ReverbDelayMix:       0.3,
		DryWet:               0.5,

		focused:        ctrlGain,
		connected:      false,
		client:         client,
		pendingChanges: make(map[control]time.Time),
	}
}

func (m *Model) ApplyState(s osc.State) {
	// Clean up stale pending changes
	m.cleanupStalePendingChanges()

	// Only update values that don't have pending user changes
	if !m.hasPendingChange(ctrlGain) {
		m.Gain = s.Gain
	}
	if !m.hasPendingChange(ctrlInputFreeze) {
		m.InputFrozen = s.InputFrozen
	}
	if !m.hasPendingChange(ctrlInputFreezeLen) {
		m.InputFreezeLength = s.InputFreezeLength
	}
	if !m.hasPendingChange(ctrlFilterAmount) {
		m.FilterAmount = s.FilterAmount
	}
	if !m.hasPendingChange(ctrlFilterCutoff) {
		m.FilterCutoff = s.FilterCutoff
	}
	if !m.hasPendingChange(ctrlFilterResonance) {
		m.FilterResonance = s.FilterResonance
	}
	if !m.hasPendingChange(ctrlOverdriveDrive) {
		m.OverdriveDrive = s.OverdriveDrive
	}
	if !m.hasPendingChange(ctrlOverdriveTone) {
		m.OverdriveTone = s.OverdriveTone
	}
	if !m.hasPendingChange(ctrlOverdriveMix) {
		m.OverdriveMix = s.OverdriveMix
	}
	if !m.hasPendingChange(ctrlGranularDensity) {
		m.GranularDensity = s.GranularDensity
	}
	if !m.hasPendingChange(ctrlGranularSize) {
		m.GranularSize = s.GranularSize
	}
	if !m.hasPendingChange(ctrlGranularPitchScatter) {
		m.GranularPitchScatter = s.GranularPitchScatter
	}
	if !m.hasPendingChange(ctrlGranularPosScatter) {
		m.GranularPosScatter = s.GranularPosScatter
	}
	if !m.hasPendingChange(ctrlGranularMix) {
		m.GranularMix = s.GranularMix
	}
	if !m.hasPendingChange(ctrlGranularFreeze) {
		m.GranularFrozen = s.GranularFrozen
	}
	// Note: GrainIntensity doesn't have a direct control mapping, so always update
	m.GrainIntensity = s.GrainIntensity
	if !m.hasPendingChange(ctrlReverbDelayBlend) {
		m.ReverbDelayBlend = s.ReverbDelayBlend
	}
	if !m.hasPendingChange(ctrlDecayTime) {
		m.DecayTime = s.DecayTime
	}
	if !m.hasPendingChange(ctrlShimmerPitch) {
		m.ShimmerPitch = s.ShimmerPitch
	}
	if !m.hasPendingChange(ctrlDelayTime) {
		m.DelayTime = s.DelayTime
	}
	if !m.hasPendingChange(ctrlModRate) {
		m.ModRate = s.ModRate
	}
	if !m.hasPendingChange(ctrlModDepth) {
		m.ModDepth = s.ModDepth
	}
	if !m.hasPendingChange(ctrlReverbDelayMix) {
		m.ReverbDelayMix = s.ReverbDelayMix
	}
	if !m.hasPendingChange(ctrlBlendMode) {
		m.BlendMode = s.BlendMode
	}
	if !m.hasPendingChange(ctrlDryWet) {
		m.DryWet = s.DryWet
	}

	// Spectrum and connection status are always updated
	m.Spectrum = s.Spectrum
	m.connected = true
}

func (m *Model) NextControl() {
	m.focused = (m.focused + 1) % ctrlCount
}

func (m *Model) PrevControl() {
	m.focused = (m.focused - 1 + ctrlCount) % ctrlCount
}

func (m *Model) Focused() control {
	return m.focused
}

func (m *Model) IsConnected() bool {
	return m.connected
}

func (m *Model) SetMidiPort(name string) {
	m.midiPort = name
}

func (m *Model) markPendingChange(ctrl control) {
	m.pendingChanges[ctrl] = time.Now()
}

func (m *Model) hasPendingChange(ctrl control) bool {
	_, exists := m.pendingChanges[ctrl]
	return exists
}

func (m *Model) clearPendingChange(ctrl control) {
	delete(m.pendingChanges, ctrl)
}

func (m *Model) cleanupStalePendingChanges() {
	// Remove pending changes older than 500ms
	cutoff := time.Now().Add(-500 * time.Millisecond)
	for ctrl, timestamp := range m.pendingChanges {
		if timestamp.Before(cutoff) {
			delete(m.pendingChanges, ctrl)
		}
	}
}
