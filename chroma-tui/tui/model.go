package tui

import (
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

		focused:   ctrlGain,
		connected: false,
		client:    client,
	}
}

func (m *Model) ApplyState(s osc.State) {
	m.Gain = s.Gain
	m.InputFrozen = s.InputFrozen
	m.InputFreezeLength = s.InputFreezeLength
	m.FilterAmount = s.FilterAmount
	m.FilterCutoff = s.FilterCutoff
	m.FilterResonance = s.FilterResonance
	m.OverdriveDrive = s.OverdriveDrive
	m.OverdriveTone = s.OverdriveTone
	m.OverdriveMix = s.OverdriveMix
	m.GranularDensity = s.GranularDensity
	m.GranularSize = s.GranularSize
	m.GranularPitchScatter = s.GranularPitchScatter
	m.GranularPosScatter = s.GranularPosScatter
	m.GranularMix = s.GranularMix
	m.GranularFrozen = s.GranularFrozen
	m.GrainIntensity = s.GrainIntensity
	m.ReverbDelayBlend = s.ReverbDelayBlend
	m.DecayTime = s.DecayTime
	m.ShimmerPitch = s.ShimmerPitch
	m.DelayTime = s.DelayTime
	m.ModRate = s.ModRate
	m.ModDepth = s.ModDepth
	m.ReverbDelayMix = s.ReverbDelayMix
	m.BlendMode = s.BlendMode
	m.DryWet = s.DryWet
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
