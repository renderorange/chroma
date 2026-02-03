package tui

import (
	"fmt"
	"strings"

	"github.com/charmbracelet/lipgloss"
)

var (
	titleStyle = lipgloss.NewStyle().
			Bold(true).
			Foreground(lipgloss.Color("205"))

	boxStyle = lipgloss.NewStyle().
			Border(lipgloss.RoundedBorder()).
			Padding(0, 1)

	focusedStyle = lipgloss.NewStyle().
			Foreground(lipgloss.Color("205"))

	normalStyle = lipgloss.NewStyle().
			Foreground(lipgloss.Color("252"))

	activeStyle = lipgloss.NewStyle().
			Background(lipgloss.Color("196")).
			Foreground(lipgloss.Color("255"))

	inactiveStyle = lipgloss.NewStyle().
			Background(lipgloss.Color("240")).
			Foreground(lipgloss.Color("255"))
)

func (m Model) View() string {
	var b strings.Builder

	// Title
	b.WriteString(titleStyle.Render(" CHROMA "))
	b.WriteString("\n\n")

	// Build sections
	inputBox := m.renderInputSection()
	filterBox := m.renderFilterSection()
	granularBox := m.renderGranularSection()
	reverbDelayBox := m.renderReverbDelaySection()
	globalBox := m.renderGlobalSection()

	// Layout: top row
	topRow := lipgloss.JoinHorizontal(lipgloss.Top, inputBox, "  ", filterBox, "  ", granularBox)
	b.WriteString(topRow)
	b.WriteString("\n\n")

	// Layout: bottom row
	bottomRow := lipgloss.JoinHorizontal(lipgloss.Top, reverbDelayBox, "  ", globalBox)
	b.WriteString(bottomRow)
	b.WriteString("\n\n")

	// Status bar
	status := "Status: "
	if m.connected {
		status += "Connected"
	} else {
		status += "Disconnected"
	}
	if m.midiPort != "" {
		status += " │ MIDI: " + m.midiPort
	}
	b.WriteString(status)
	b.WriteString("\n")
	b.WriteString("Tab/↑↓: Navigate │ ←→: Adjust │ Enter: Toggle │ 1-3: Mode │ q: Quit")

	return b.String()
}

func (m Model) renderInputSection() string {
	var lines []string
	lines = append(lines, "─ INPUT ─")
	lines = append(lines, m.renderSlider("Gain", m.Gain, 0, 2, ctrlGain))
	lines = append(lines, m.renderSlider("Loop", m.InputFreezeLength, 0.05, 0.5, ctrlInputFreezeLen))
	lines = append(lines, m.renderButton("INPUT FREEZE", m.InputFrozen, ctrlInputFreeze))
	return boxStyle.Render(strings.Join(lines, "\n"))
}

func (m Model) renderFilterSection() string {
	var lines []string
	lines = append(lines, "─ FILTER ─")
	lines = append(lines, m.renderSlider("Amount", m.FilterAmount, 0, 1, ctrlFilterAmount))
	lines = append(lines, m.renderSlider("Cutoff", m.FilterCutoff, 200, 8000, ctrlFilterCutoff))
	lines = append(lines, m.renderSlider("Resonance", m.FilterResonance, 0, 1, ctrlFilterResonance))
	return boxStyle.Render(strings.Join(lines, "\n"))
}

func (m Model) renderGranularSection() string {
	var lines []string
	lines = append(lines, "─ GRANULAR ─")
	lines = append(lines, m.renderSlider("Density", m.GranularDensity, 1, 50, ctrlGranularDensity))
	lines = append(lines, m.renderSlider("Size", m.GranularSize, 0.01, 0.5, ctrlGranularSize))
	lines = append(lines, m.renderSlider("PitchScat", m.GranularPitchScatter, 0, 1, ctrlGranularPitchScatter))
	lines = append(lines, m.renderSlider("PosScat", m.GranularPosScatter, 0, 1, ctrlGranularPosScatter))
	lines = append(lines, m.renderSlider("Mix", m.GranularMix, 0, 1, ctrlGranularMix))
	lines = append(lines, m.renderButton("GRAIN FREEZE", m.GranularFrozen, ctrlGranularFreeze))
	return boxStyle.Render(strings.Join(lines, "\n"))
}

func (m Model) renderReverbDelaySection() string {
	var lines []string
	lines = append(lines, "─ REVERB/DELAY ─")
	lines = append(lines, m.renderSlider("Rev<>Dly", m.ReverbDelayBlend, 0, 1, ctrlReverbDelayBlend))
	lines = append(lines, m.renderSlider("Decay", m.DecayTime, 0.1, 10, ctrlDecayTime))
	lines = append(lines, m.renderSlider("Shimmer", m.ShimmerPitch, 0, 24, ctrlShimmerPitch))
	lines = append(lines, m.renderSlider("DelayTime", m.DelayTime, 0.01, 1, ctrlDelayTime))
	lines = append(lines, m.renderSlider("ModRate", m.ModRate, 0.1, 10, ctrlModRate))
	lines = append(lines, m.renderSlider("ModDepth", m.ModDepth, 0, 1, ctrlModDepth))
	lines = append(lines, m.renderSlider("Mix", m.ReverbDelayMix, 0, 1, ctrlReverbDelayMix))
	return boxStyle.Render(strings.Join(lines, "\n"))
}

func (m Model) renderGlobalSection() string {
	var lines []string
	lines = append(lines, "─ GLOBAL ─")
	modes := []string{"MIRROR", "COMPLEMENT", "TRANSFORM"}
	modeStr := fmt.Sprintf("Mode: [%s]", modes[m.BlendMode])
	if m.focused == ctrlBlendMode {
		modeStr = focusedStyle.Render(modeStr)
	}
	lines = append(lines, modeStr)
	lines = append(lines, m.renderSlider("Dry/Wet", m.DryWet, 0, 1, ctrlDryWet))
	return boxStyle.Render(strings.Join(lines, "\n"))
}

func (m Model) renderSlider(label string, value, min, max float32, ctrl control) string {
	// Normalize value to 0-1
	norm := (value - min) / (max - min)
	if norm < 0 {
		norm = 0
	}
	if norm > 1 {
		norm = 1
	}

	// Build slider bar (10 chars)
	filled := int(norm * 10)
	bar := strings.Repeat("█", filled) + strings.Repeat("─", 10-filled)

	line := fmt.Sprintf("%-9s [%s]", label, bar)
	if m.focused == ctrl {
		return focusedStyle.Render(line)
	}
	return normalStyle.Render(line)
}

func (m Model) renderButton(label string, active bool, ctrl control) string {
	style := inactiveStyle
	if active {
		style = activeStyle
	}
	btn := style.Render(fmt.Sprintf(" %s ", label))
	if m.focused == ctrl {
		return focusedStyle.Render("▶ ") + btn
	}
	return "  " + btn
}
