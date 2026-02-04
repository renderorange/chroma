# TUI Refactor Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make the TUI the primary interface with full-width layout, stacked sections, and instant responsiveness while removing the SuperCollider GUI.

**Architecture:** Remove all GUI code from Chroma.sc making it headless. Refactor TUI to use full terminal width with vertically stacked sections (one control per line). Local-first state updates for instant UI feedback.

**Tech Stack:** SuperCollider (Chroma.sc), Go (Bubble Tea, Lipgloss)

---

### Task 1: Remove GUI from SuperCollider

**Files:**
- Modify: `Chroma.sc:9` (remove window var)
- Modify: `Chroma.sc:96` (remove buildGUI call)
- Modify: `Chroma.sc:639-884` (delete buildGUI method)
- Modify: `Chroma.sc:887-900` (delete updateBlendButtons method)
- Modify: `Chroma.sc:902-912` (update cleanup method)

**Step 1: Remove window instance variable**

In `Chroma.sc`, delete line 9:
```supercollider
var <window;
```

**Step 2: Remove buildGUI call from boot method**

In `Chroma.sc`, change the boot method (lines 89-100) to remove line 96:

Before:
```supercollider
boot {
    server.waitForBoot {
        this.allocateResources;
        server.sync;
        this.loadSynthDefs;
        server.sync;
        this.createSynths;
        this.buildGUI;
        this.setupOSC;
        "Chroma ready".postln;
    };
}
```

After:
```supercollider
boot {
    server.waitForBoot {
        this.allocateResources;
        server.sync;
        this.loadSynthDefs;
        server.sync;
        this.createSynths;
        this.setupOSC;
        "Chroma ready".postln;
    };
}
```

**Step 3: Delete buildGUI method**

Delete the entire `buildGUI` method (lines 639-885).

**Step 4: Delete updateBlendButtons method**

Delete the entire `updateBlendButtons` method (lines 887-900).

**Step 5: Update cleanup method**

Change cleanup method to remove window handling:

Before:
```supercollider
cleanup {
    this.cleanupOSC;
    synths.do(_.free);
    buses.do(_.free);
    fftBuffer.free;
    grainBuffer.free;
    freezeBuffer.free;
    inputFreezeBuffer.free;
    if(window.notNil) { window.close };
    "Chroma stopped".postln;
}
```

After:
```supercollider
cleanup {
    this.cleanupOSC;
    synths.do(_.free);
    buses.do(_.free);
    fftBuffer.free;
    grainBuffer.free;
    freezeBuffer.free;
    inputFreezeBuffer.free;
    "Chroma stopped".postln;
}
```

**Step 6: Test headless operation**

Run: `./test_integration.sh headless`
Expected: All tests pass

**Step 7: Commit**

```bash
git add Chroma.sc
git commit -m "Remove GUI from Chroma.sc, make app headless"
```

---

### Task 2: Add terminal width tracking to TUI model

**Files:**
- Modify: `chroma-tui/tui/model.go:59-61`
- Modify: `chroma-tui/tui/update.go:14-23`

**Step 1: Add width/height fields to Model**

In `chroma-tui/tui/model.go`, add width and height fields after line 61:

```go
type Model struct {
	// State
	Gain                 float32
	InputFrozen          bool
	InputFreezeLength    float32
	FilterAmount         float32
	FilterCutoff         float32
	FilterResonance      float32
	GranularDensity      float32
	GranularSize         float32
	GranularPitchScatter float32
	GranularPosScatter   float32
	GranularMix          float32
	GranularFrozen       bool
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

	// OSC
	client *osc.Client
}
```

**Step 2: Handle WindowSizeMsg in Update**

In `chroma-tui/tui/update.go`, add a case for `tea.WindowSizeMsg` in the Update function:

```go
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
```

**Step 3: Build and verify compilation**

Run: `cd chroma-tui && go build`
Expected: Compiles without errors

**Step 4: Commit**

```bash
git add chroma-tui/tui/model.go chroma-tui/tui/update.go
git commit -m "Add terminal width/height tracking to TUI model"
```

---

### Task 3: Refactor view.go for full-width stacked layout

**Files:**
- Modify: `chroma-tui/tui/view.go`

**Step 1: Update boxStyle to accept width**

Replace the entire `chroma-tui/tui/view.go` file with the new full-width layout:

```go
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
	titleStyle = lipgloss.NewStyle().
		Bold(true).
		Foreground(lipgloss.Color("205"))

	sectionTitleStyle = lipgloss.NewStyle().
		Bold(true).
		Foreground(lipgloss.Color("69"))

	focusedStyle = lipgloss.NewStyle().
		Foreground(lipgloss.Color("205"))

	normalStyle = lipgloss.NewStyle().
		Foreground(lipgloss.Color("252"))

	activeButtonStyle = lipgloss.NewStyle().
		Background(lipgloss.Color("196")).
		Foreground(lipgloss.Color("255"))

	inactiveButtonStyle = lipgloss.NewStyle().
		Background(lipgloss.Color("240")).
		Foreground(lipgloss.Color("255"))

	selectedModeStyle = lipgloss.NewStyle().
		Background(lipgloss.Color("69")).
		Foreground(lipgloss.Color("255"))

	unselectedModeStyle = lipgloss.NewStyle().
		Foreground(lipgloss.Color("240"))
)

func (m Model) View() string {
	// Default width if not yet received
	width := m.width
	if width < 40 {
		width = 80
	}

	var sections []string

	sections = append(sections, m.renderSection("INPUT", width, m.renderInputControls))
	sections = append(sections, m.renderSection("FILTER", width, m.renderFilterControls))
	sections = append(sections, m.renderSection("GRANULAR", width, m.renderGranularControls))
	sections = append(sections, m.renderSection("REVERB/DELAY", width, m.renderReverbDelayControls))
	sections = append(sections, m.renderSection("GLOBAL", width, m.renderGlobalControls))

	content := lipgloss.JoinVertical(lipgloss.Left, sections...)

	// Status bar
	status := "\nStatus: "
	if m.connected {
		status += "Connected"
	} else {
		status += "Disconnected"
	}
	if m.midiPort != "" {
		status += " │ MIDI: " + m.midiPort
	}
	status += "\n"
	status += "Tab/↑↓: Navigate │ ←→: Adjust │ Enter: Toggle │ 1-3: Mode │ q: Quit"

	return content + status
}

func (m Model) renderSection(title string, width int, renderControls func(int) []string) string {
	innerWidth := width - 4 // Account for border padding

	// Section title centered
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

func (m Model) renderGranularControls(width int) []string {
	return []string{
		m.renderSlider("Density", m.GranularDensity, 1, 50, width, ctrlGranularDensity),
		m.renderSlider("Size", m.GranularSize, 0.01, 0.5, width, ctrlGranularSize),
		m.renderSlider("PitchScat", m.GranularPitchScatter, 0, 1, width, ctrlGranularPitchScatter),
		m.renderSlider("PosScat", m.GranularPosScatter, 0, 1, width, ctrlGranularPosScatter),
		m.renderSlider("Mix", m.GranularMix, 0, 1, width, ctrlGranularMix),
		m.renderButton("GRAIN FREEZE", m.GranularFrozen, ctrlGranularFreeze),
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
	if m.focused == ctrl {
		return focusedStyle.Render("▶ ") + btn
	}
	return "  " + btn
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
```

**Step 2: Build and verify compilation**

Run: `cd chroma-tui && go build`
Expected: Compiles without errors

**Step 3: Test TUI visually**

Run: `cd chroma-tui && ./chroma-tui --no-midi`
Expected: TUI displays with full-width stacked sections, resize terminal to verify responsive width

**Step 4: Commit**

```bash
git add chroma-tui/tui/view.go
git commit -m "Refactor TUI for full-width stacked layout with one control per line"
```

---

### Task 4: Update documentation

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`

**Step 1: Update README.md**

Add note that the TUI is the primary interface and the SuperCollider app runs headless.

In the Quick Start section, update to clarify:
- SuperCollider runs headless (no GUI window)
- Use chroma-tui for the interface

**Step 2: Update CLAUDE.md**

Update the Current Status section to reflect completed TUI refactor.

**Step 3: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "Update documentation for headless operation and TUI as primary interface"
```

---

### Task 5: Run full integration test

**Files:**
- None (verification only)

**Step 1: Install updated Chroma.sc**

Run: `cp Chroma.sc ~/.local/share/SuperCollider/Extensions/`

**Step 2: Run integration tests**

Run: `./test_integration.sh`
Expected: All tests pass

**Step 3: Manual verification**

1. Start SuperCollider: `sclang -e "Chroma.start"`
2. Verify no GUI window appears
3. In another terminal: `cd chroma-tui && ./chroma-tui`
4. Verify TUI displays full-width
5. Test navigation and adjustments
6. Verify instant responsiveness (no lag on key press)
