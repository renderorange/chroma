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
		m.markPendingChange(m.focused)
		m.client.SetGain(m.Gain)
	case ctrlInputFreezeLen:
		m.InputFreezeLength = clamp(m.InputFreezeLength+delta*0.45, 0.05, 0.5)
		m.markPendingChange(m.focused)
		m.client.SetInputFreezeLength(m.InputFreezeLength)
	case ctrlFilterAmount:
		m.FilterAmount = clamp(m.FilterAmount+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetFilterAmount(m.FilterAmount)
	case ctrlFilterCutoff:
		m.FilterCutoff = clamp(m.FilterCutoff+delta*7800, 200, 8000)
		m.markPendingChange(m.focused)
		m.client.SetFilterCutoff(m.FilterCutoff)
	case ctrlFilterResonance:
		m.FilterResonance = clamp(m.FilterResonance+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetFilterResonance(m.FilterResonance)
	case ctrlOverdriveDrive:
		m.OverdriveDrive = clamp(m.OverdriveDrive+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetOverdriveDrive(m.OverdriveDrive)
	case ctrlOverdriveTone:
		m.OverdriveTone = clamp(m.OverdriveTone+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetOverdriveTone(m.OverdriveTone)
	case ctrlOverdriveMix:
		m.OverdriveMix = clamp(m.OverdriveMix+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetOverdriveMix(m.OverdriveMix)
	case ctrlBitDepth:
		m.BitDepth = clamp(m.BitDepth+delta*12, 4, 16)
		m.markPendingChange(m.focused)
		m.client.SetBitDepth(m.BitDepth)
	case ctrlBitcrushSampleRate:
		m.BitcrushSampleRate = clamp(m.BitcrushSampleRate+delta*43100, 1000, 44100)
		m.markPendingChange(m.focused)
		m.client.SetBitcrushSampleRate(m.BitcrushSampleRate)
	case ctrlBitcrushDrive:
		m.BitcrushDrive = clamp(m.BitcrushDrive+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetBitcrushDrive(m.BitcrushDrive)
	case ctrlBitcrushMix:
		m.BitcrushMix = clamp(m.BitcrushMix+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetBitcrushMix(m.BitcrushMix)
	case ctrlGranularDensity:
		m.GranularDensity = clamp(m.GranularDensity+delta*49, 1, 50)
		m.markPendingChange(m.focused)
		m.client.SetGranularDensity(m.GranularDensity)
	case ctrlGranularSize:
		m.GranularSize = clamp(m.GranularSize+delta*0.49, 0.01, 0.5)
		m.markPendingChange(m.focused)
		m.client.SetGranularSize(m.GranularSize)
	case ctrlGranularPitchScatter:
		m.GranularPitchScatter = clamp(m.GranularPitchScatter+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetGranularPitchScatter(m.GranularPitchScatter)
	case ctrlGranularPosScatter:
		m.GranularPosScatter = clamp(m.GranularPosScatter+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetGranularPosScatter(m.GranularPosScatter)
	case ctrlGranularMix:
		m.GranularMix = clamp(m.GranularMix+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetGranularMix(m.GranularMix)
	case ctrlReverbDecayTime:
		m.ReverbDecayTime = clamp(m.ReverbDecayTime+delta*9.5, 0.5, 10)
		m.markPendingChange(m.focused)
		m.client.SetReverbDecayTime(m.ReverbDecayTime)
	case ctrlReverbMix:
		m.ReverbMix = clamp(m.ReverbMix+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetReverbMix(m.ReverbMix)
	case ctrlDelayTime:
		m.DelayTime = clamp(m.DelayTime+delta*0.9, 0.1, 1)
		m.markPendingChange(m.focused)
		m.client.SetDelayTime(m.DelayTime)
	case ctrlDelayDecayTime:
		m.DelayDecayTime = clamp(m.DelayDecayTime+delta*9.5, 0.5, 10)
		m.markPendingChange(m.focused)
		m.client.SetDelayDecayTime(m.DelayDecayTime)
	case ctrlModRate:
		m.ModRate = clamp(m.ModRate+delta*9.9, 0.1, 10)
		m.markPendingChange(m.focused)
		m.client.SetModRate(m.ModRate)
	case ctrlModDepth:
		m.ModDepth = clamp(m.ModDepth+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetModDepth(m.ModDepth)
	case ctrlDelayMix:
		m.DelayMix = clamp(m.DelayMix+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetDelayMix(m.DelayMix)
	case ctrlDryWet:
		m.DryWet = clamp(m.DryWet+delta, 0, 1)
		m.markPendingChange(m.focused)
		m.client.SetDryWet(m.DryWet)
	}
}

func (m *Model) toggleFocused() {
	switch m.focused {
	case ctrlInputFreeze:
		m.InputFrozen = !m.InputFrozen
		m.markPendingChange(m.focused)
		m.client.SetInputFreeze(m.InputFrozen)
	case ctrlBitcrushEnabled:
		m.BitcrushEnabled = !m.BitcrushEnabled
		m.markPendingChange(m.focused)
		m.client.SetBitcrushEnabled(m.BitcrushEnabled)
	case ctrlGranularFreeze:
		m.GranularFrozen = !m.GranularFrozen
		m.markPendingChange(m.focused)
		m.client.SetGranularFreeze(m.GranularFrozen)
	case ctrlReverbEnabled:
		m.ReverbEnabled = !m.ReverbEnabled
		m.markPendingChange(m.focused)
		m.client.SetReverbEnabled(m.ReverbEnabled)
	case ctrlDelayEnabled:
		m.DelayEnabled = !m.DelayEnabled
		m.markPendingChange(m.focused)
		m.client.SetDelayEnabled(m.DelayEnabled)
	}
}

func (m *Model) setBlendMode(mode int) {
	m.BlendMode = mode
	m.markPendingChange(ctrlBlendMode)
	m.client.SetBlendMode(mode)
}

func (m *Model) toggleGrainIntensity() {
	switch m.GrainIntensity {
	case "subtle":
		m.GrainIntensity = "pronounced"
	case "pronounced":
		m.GrainIntensity = "extreme"
	case "extreme":
		m.GrainIntensity = "subtle"
	default:
		m.GrainIntensity = "subtle"
	}
	// Note: GrainIntensity doesn't have a control mapping, so no markPendingChange
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
