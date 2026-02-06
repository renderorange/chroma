package tui

import (
	"fmt"
	"strings"

	"github.com/charmbracelet/lipgloss"
)

const (
	labelWidth  = 12
	minBarWidth = 20
)

var (
	sectionTitleStyle = lipgloss.NewStyle().
				Bold(true).
				Foreground(lipgloss.Color("205"))

	focusedStyle = lipgloss.NewStyle().
			Foreground(lipgloss.Color("205"))

	normalStyle = lipgloss.NewStyle().
			Foreground(lipgloss.Color("249"))

	activeButtonStyle = lipgloss.NewStyle().
				Background(lipgloss.Color("205")).
				Foreground(lipgloss.Color("255"))

	inactiveButtonStyle = lipgloss.NewStyle().
				Background(lipgloss.Color("244")).
				Foreground(lipgloss.Color("255"))

	selectedModeStyle = lipgloss.NewStyle().
				Background(lipgloss.Color("205")).
				Foreground(lipgloss.Color("255"))

	unselectedModeStyle = lipgloss.NewStyle().
				Foreground(lipgloss.Color("244"))
)

func (m Model) View() string {
	// Default width if not yet received
	width := m.width
	if width < 40 {
		width = 80
	}

	var sections []string

	// Visualizer at top (if connected)
	if m.connected {
		vis := m.renderVisualizer(width)
		if vis != "" {
			sections = append(sections, vis)
		}
	}

	sections = append(sections, m.renderSection("INPUT", width, m.renderInputControls))
	sections = append(sections, m.renderSection("FILTER", width, m.renderFilterControls))
	sections = append(sections, m.renderSection("OVERDRIVE", width, m.renderOverdriveControls))
	sections = append(sections, m.renderSection("GRANULAR", width, m.renderGranularControls))
	sections = append(sections, m.renderSection("REVERB/DELAY", width, m.renderReverbDelayControls))
	sections = append(sections, m.renderSection("GLOBAL", width, m.renderGlobalControls))

	content := lipgloss.JoinVertical(lipgloss.Left, sections...)

	// Status bar (with left margin to align with box content)
	margin := "  "
	status := "\n" + margin + "Status: "
	if m.connected {
		status += "Connected"
	} else {
		status += "Disconnected"
	}
	if m.midiPort != "" {
		status += " │ MIDI: " + m.midiPort
	}
	status += "\n"
	status += margin + "Tab/↑↓: Navigate │ ←→: Adjust │ Enter: Toggle │ i: Intensity │ 1-3: Mode │ q: Quit"

	return content + status
}

func (m Model) renderSection(title string, width int, renderControls func(int) []string) string {
	innerWidth := width - 4 // Account for border padding

	// Section title
	titleLine := sectionTitleStyle.Render(fmt.Sprintf("─── %s ───", title))

	// Get control lines
	controls := renderControls(innerWidth)

	// Build section content
	lines := []string{titleLine}
	lines = append(lines, controls...)
	content := strings.Join(lines, "\n")

	// Create box style with dynamic width
	boxStyle := lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		Padding(0, 1).
		Width(width - 2)

	return boxStyle.Render(content)
}

func (m Model) renderInputControls(width int) []string {
	return []string{
		m.renderSlider("Gain", m.Gain, 0, 2, width, ctrlGain),
		m.renderSlider("Loop", m.InputFreezeLength, 0.05, 0.5, width, ctrlInputFreezeLen),
		m.renderButton("INPUT FREEZE", m.InputFrozen, ctrlInputFreeze),
	}
}

func (m Model) renderFilterControls(width int) []string {
	return []string{
		m.renderSlider("Amount", m.FilterAmount, 0, 1, width, ctrlFilterAmount),
		m.renderSlider("Cutoff", m.FilterCutoff, 200, 8000, width, ctrlFilterCutoff),
		m.renderSlider("Resonance", m.FilterResonance, 0, 1, width, ctrlFilterResonance),
	}
}

func (m Model) renderOverdriveControls(width int) []string {
	return []string{
		m.renderSlider("Drive", m.OverdriveDrive, 0, 1, width, ctrlOverdriveDrive),
		m.renderSlider("Tone", m.OverdriveTone, 0, 1, width, ctrlOverdriveTone),
		m.renderSlider("Mix", m.OverdriveMix, 0, 1, width, ctrlOverdriveMix),
	}
}

func (m Model) renderGranularControls(width int) []string {
	return []string{
		m.renderSlider("Density", m.GranularDensity, 1, 50, width, ctrlGranularDensity),
		m.renderSlider("Size", m.GranularSize, 0.01, 0.5, width, ctrlGranularSize),
		m.renderSlider("PitchScat", m.GranularPitchScatter, 0, 1, width, ctrlGranularPitchScatter),
		m.renderSlider("PosScat", m.GranularPosScatter, 0, 1, width, ctrlGranularPosScatter),
		m.renderSlider("Mix", m.GranularMix, 0, 1, width, ctrlGranularMix),
		m.renderButton("GRAIN FREEZE", m.GranularFrozen, ctrlGranularFreeze),
		m.renderIntensitySelector(width),
	}
}

func (m Model) renderReverbDelayControls(width int) []string {
	return []string{
		m.renderSlider("Rev<>Dly", m.ReverbDelayBlend, 0, 1, width, ctrlReverbDelayBlend),
		m.renderSlider("Decay", m.DecayTime, 0.1, 10, width, ctrlDecayTime),
		m.renderSlider("Shimmer", m.ShimmerPitch, 0, 24, width, ctrlShimmerPitch),
		m.renderSlider("DelayTime", m.DelayTime, 0.01, 1, width, ctrlDelayTime),
		m.renderSlider("ModRate", m.ModRate, 0.1, 10, width, ctrlModRate),
		m.renderSlider("ModDepth", m.ModDepth, 0, 1, width, ctrlModDepth),
		m.renderSlider("Mix", m.ReverbDelayMix, 0, 1, width, ctrlReverbDelayMix),
	}
}

func (m Model) renderGlobalControls(width int) []string {
	return []string{
		m.renderModeSelector(width),
		m.renderSlider("Dry/Wet", m.DryWet, 0, 1, width, ctrlDryWet),
	}
}

func (m Model) renderSlider(label string, value, min, max float32, width int, ctrl control) string {
	// Normalize value to 0-1
	norm := (value - min) / (max - min)
	if norm < 0 {
		norm = 0
	}
	if norm > 1 {
		norm = 1
	}

	// Calculate bar width (leave room for label and value)
	valueStr := formatValue(value, min, max)
	barWidth := width - labelWidth - len(valueStr) - 5 // 5 for " [" + "] "
	if barWidth < minBarWidth {
		barWidth = minBarWidth
	}

	// Build slider bar
	filled := int(norm * float32(barWidth))
	bar := strings.Repeat("█", filled) + strings.Repeat("─", barWidth-filled)

	line := fmt.Sprintf("%-*s [%s] %s", labelWidth, label, bar, valueStr)
	if m.focused == ctrl {
		return focusedStyle.Render(line)
	}
	return normalStyle.Render(line)
}

func formatValue(value, min, max float32) string {
	// Show integers for large ranges, decimals for small ranges
	if max-min >= 10 {
		return fmt.Sprintf("%5.1f", value)
	}
	return fmt.Sprintf("%5.2f", value)
}

func (m Model) renderButton(label string, active bool, ctrl control) string {
	style := inactiveButtonStyle
	if active {
		style = activeButtonStyle
	}
	btn := style.Render(fmt.Sprintf(" %s ", label))

	// Add top margin (1 line) and left-align
	margin := "\n"
	if m.focused == ctrl {
		return margin + focusedStyle.Render("▶ ") + btn
	}
	return margin + btn
}

func (m Model) renderModeSelector(width int) string {
	modes := []string{"MIRROR", "COMPLEMENT", "TRANSFORM"}
	var parts []string

	for i, mode := range modes {
		if i == m.BlendMode {
			parts = append(parts, selectedModeStyle.Render(fmt.Sprintf(" %s ", mode)))
		} else {
			parts = append(parts, unselectedModeStyle.Render(fmt.Sprintf(" %s ", mode)))
		}
	}

	line := fmt.Sprintf("%-*s %s", labelWidth, "Mode", strings.Join(parts, " "))
	if m.focused == ctrlBlendMode {
		return focusedStyle.Render(line)
	}
	return normalStyle.Render(line)
}

func (m Model) renderIntensitySelector(width int) string {
	intensities := []string{"SUBTLE", "PRONOUNCED"}
	var parts []string

	for _, intensity := range intensities {
		if intensity == strings.ToUpper(m.GrainIntensity) {
			parts = append(parts, selectedModeStyle.Render(fmt.Sprintf(" %s ", intensity)))
		} else {
			parts = append(parts, unselectedModeStyle.Render(fmt.Sprintf(" %s ", intensity)))
		}
	}

	line := fmt.Sprintf("%-*s %s", labelWidth, "Intensity", strings.Join(parts, " "))
	if m.focused == ctrlGrainIntensity {
		return focusedStyle.Render(line)
	}
	return normalStyle.Render(line)
}
