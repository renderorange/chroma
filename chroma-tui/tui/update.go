package tui

import (
	tea "github.com/charmbracelet/bubbletea"
	"github.com/renderorange/chroma/chroma-tui/osc"
)

type StateMsg osc.State

func (m Model) Init() tea.Cmd {
	return nil
}

func (m Model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		return m, nil
	case tea.KeyMsg:
		return m.handleKey(msg)
	case StateMsg:
		m.ApplyState(osc.State(msg))
		return m, nil
	}
	return m, nil
}

func (m Model) handleKey(msg tea.KeyMsg) (tea.Model, tea.Cmd) {
	switch msg.String() {
	case "q", "ctrl+c":
		return m, tea.Quit

	case "tab", "down", "j":
		m.NextControl()

	case "shift+tab", "up", "k":
		m.PrevControl()

	case "left", "h":
		m.adjustFocused(-0.05)

	case "right", "l":
		m.adjustFocused(0.05)

	case "enter", " ":
		m.toggleFocused()

	case "i":
		m.toggleGrainIntensity()

	case "1":
		m.setBlendMode(0)
	case "2":
		m.setBlendMode(1)
	case "3":
		m.setBlendMode(2)
	}

	return m, nil
}

func (m *Model) adjustFocused(delta float32) {
	switch m.focused {
	case ctrlGain:
		m.Gain = clamp(m.Gain+delta*2, 0, 2)
		m.client.SetGain(m.Gain)
	case ctrlInputFreezeLen:
		m.InputFreezeLength = clamp(m.InputFreezeLength+delta*0.45, 0.05, 0.5)
		m.client.SetInputFreezeLength(m.InputFreezeLength)
	case ctrlFilterAmount:
		m.FilterAmount = clamp(m.FilterAmount+delta, 0, 1)
		m.client.SetFilterAmount(m.FilterAmount)
	case ctrlFilterCutoff:
		m.FilterCutoff = clamp(m.FilterCutoff+delta*7800, 200, 8000)
		m.client.SetFilterCutoff(m.FilterCutoff)
	case ctrlFilterResonance:
		m.FilterResonance = clamp(m.FilterResonance+delta, 0, 1)
		m.client.SetFilterResonance(m.FilterResonance)
	case ctrlOverdriveDrive:
		m.OverdriveDrive = clamp(m.OverdriveDrive+delta, 0, 1)
		m.client.SetOverdriveDrive(m.OverdriveDrive)
	case ctrlOverdriveTone:
		m.OverdriveTone = clamp(m.OverdriveTone+delta, 0, 1)
		m.client.SetOverdriveTone(m.OverdriveTone)
	case ctrlOverdriveMix:
		m.OverdriveMix = clamp(m.OverdriveMix+delta, 0, 1)
		m.client.SetOverdriveMix(m.OverdriveMix)
	case ctrlGranularDensity:
		m.GranularDensity = clamp(m.GranularDensity+delta*49, 1, 50)
		m.client.SetGranularDensity(m.GranularDensity)
	case ctrlGranularSize:
		m.GranularSize = clamp(m.GranularSize+delta*0.49, 0.01, 0.5)
		m.client.SetGranularSize(m.GranularSize)
	case ctrlGranularPitchScatter:
		m.GranularPitchScatter = clamp(m.GranularPitchScatter+delta, 0, 1)
		m.client.SetGranularPitchScatter(m.GranularPitchScatter)
	case ctrlGranularPosScatter:
		m.GranularPosScatter = clamp(m.GranularPosScatter+delta, 0, 1)
		m.client.SetGranularPosScatter(m.GranularPosScatter)
	case ctrlGranularMix:
		m.GranularMix = clamp(m.GranularMix+delta, 0, 1)
		m.client.SetGranularMix(m.GranularMix)
	case ctrlReverbDelayBlend:
		m.ReverbDelayBlend = clamp(m.ReverbDelayBlend+delta, 0, 1)
		m.client.SetReverbDelayBlend(m.ReverbDelayBlend)
	case ctrlDecayTime:
		m.DecayTime = clamp(m.DecayTime+delta*9.9, 0.1, 10)
		m.client.SetDecayTime(m.DecayTime)
	case ctrlShimmerPitch:
		m.ShimmerPitch = clamp(m.ShimmerPitch+delta*24, 0, 24)
		m.client.SetShimmerPitch(m.ShimmerPitch)
	case ctrlDelayTime:
		m.DelayTime = clamp(m.DelayTime+delta*0.99, 0.01, 1)
		m.client.SetDelayTime(m.DelayTime)
	case ctrlModRate:
		m.ModRate = clamp(m.ModRate+delta*9.9, 0.1, 10)
		m.client.SetModRate(m.ModRate)
	case ctrlModDepth:
		m.ModDepth = clamp(m.ModDepth+delta, 0, 1)
		m.client.SetModDepth(m.ModDepth)
	case ctrlReverbDelayMix:
		m.ReverbDelayMix = clamp(m.ReverbDelayMix+delta, 0, 1)
		m.client.SetReverbDelayMix(m.ReverbDelayMix)
	case ctrlDryWet:
		m.DryWet = clamp(m.DryWet+delta, 0, 1)
		m.client.SetDryWet(m.DryWet)
	}
}

func (m *Model) toggleFocused() {
	switch m.focused {
	case ctrlInputFreeze:
		m.InputFrozen = !m.InputFrozen
		m.client.SetInputFreeze(m.InputFrozen)
	case ctrlGranularFreeze:
		m.GranularFrozen = !m.GranularFrozen
		m.client.SetGranularFreeze(m.GranularFrozen)
	}
}

func (m *Model) setBlendMode(mode int) {
	m.BlendMode = mode
	m.client.SetBlendMode(mode)
}

func (m *Model) toggleGrainIntensity() {
	if m.GrainIntensity == "subtle" {
		m.GrainIntensity = "pronounced"
	} else {
		m.GrainIntensity = "subtle"
	}
	m.client.SetGrainIntensity(m.GrainIntensity)
}

func clamp(v, min, max float32) float32 {
	if v < min {
		return min
	}
	if v > max {
		return max
	}
	return v
}
