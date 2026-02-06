package tui

import (
	"strings"

	"github.com/charmbracelet/lipgloss"
)

// renderSpectrum renders 8-band spectrum analyzer
func renderSpectrum(spectrum [8]float32, width int) string {
	if width < 16 {
		return ""
	}

	barWidth := width / 8
	bars := make([]string, 8)
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

		// Style with pink accent
		style := lipgloss.NewStyle().Foreground(lipgloss.Color("205"))
		bars[i] = style.Render(bar)
	}

	return strings.Join(bars, "")
}

// renderVisualizer creates full visualizer section
func (m Model) renderVisualizer(width int) string {
	if width < 16 {
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

	// Band labels (frequency ranges)
	labelStyle := lipgloss.NewStyle().
		Foreground(lipgloss.Color("240")).
		Width(width)
	labels := "30Hz  120Hz 375Hz  1kHz  3kHz  5kHz  9kHz 16kHz"
	sections = append(sections, labelStyle.Render(labels))

	return lipgloss.JoinVertical(lipgloss.Left, sections...)
}
