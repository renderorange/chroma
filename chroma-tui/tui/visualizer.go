package tui

import (
	"strings"

	"github.com/charmbracelet/lipgloss"
)

const (
	spectrumBands      = 8
	minVisualizerWidth = 16
)

var spectrumBarStyle = lipgloss.NewStyle().Foreground(lipgloss.Color("205"))

// renderSpectrum renders 8-band spectrum analyzer
func renderSpectrum(spectrum [spectrumBands]float32, width int) string {
	if width < minVisualizerWidth {
		return ""
	}

	barWidth := width / spectrumBands
	bars := make([]string, spectrumBands)
	levels := []string{"▁", "▂", "▃", "▄", "▅", "▆", "▇", "█"}

	for i, val := range spectrum {
		// Clamp value 0-1
		if val < 0 {
			val = 0
		}
		if val > 1 {
			val = 1
		}

		// Map to 8 levels
		levelIdx := int(val * 7)
		bar := strings.Repeat(levels[levelIdx], barWidth)

		// Style with pink accent using pre-created style
		bars[i] = spectrumBarStyle.Render(bar)
	}

	return strings.Join(bars, "")
}

// renderVisualizer creates full visualizer section
func (m Model) renderVisualizer(width int) string {
	if width < minVisualizerWidth {
		return ""
	}

	var sections []string

	// Title
	titleStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("205")).
		Bold(true)
	sections = append(sections, titleStyle.Render("─── SPECTRUM ──────────────────────────────────────"))

	// Spectrum bars
	spectrumLine := renderSpectrum(m.Spectrum, width)
	sections = append(sections, spectrumLine)

	// Band labels (frequency ranges) - dynamically positioned
	labelStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("240")).
		Width(width)

	labels := []string{"30Hz", "120Hz", "375Hz", "1kHz", "3kHz", "5kHz", "9kHz", "16kHz"}
	barWidth := width / spectrumBands

	var labelLine strings.Builder
	for i, label := range labels {
		// Calculate center position for each label within its bar
		centerPos := i*barWidth + barWidth/2
		labelStart := centerPos - len(label)/2
		if labelStart < 0 {
			labelStart = 0
		}

		// Add spacing before label
		currentLen := labelLine.Len()
		if currentLen < labelStart {
			labelLine.WriteString(strings.Repeat(" ", labelStart-currentLen))
		}

		labelLine.WriteString(label)
	}

	sections = append(sections, labelStyle.Render(labelLine.String()))

	return lipgloss.JoinVertical(lipgloss.Left, sections...)
}
