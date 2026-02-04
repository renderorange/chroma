# TUI Refactor Implementation Plan

> **Status: COMPLETE** - All tasks implemented and tested (2026-02-04)

**Goal:** Make the TUI the primary interface with full-width layout, stacked sections (including OVERDRIVE), and instant responsiveness while removing the SuperCollider GUI.

**Architecture:** Remove all GUI code from Chroma.sc making it headless (already done). Refactor TUI to use full terminal width with vertically stacked sections (one control per line). Add overdrive controls. Local-first state updates for instant UI feedback.

**Tech Stack:** SuperCollider (Chroma.sc), Go (Bubble Tea, Lipgloss)

---

### Task 1: Remove GUI from SuperCollider

**Status:** COMPLETE (commit b2c2fd7)

The GUI has already been removed from Chroma.sc. The app now runs headless with OSC as the only control interface.

---

### Task 2: Add overdrive support to OSC client and server

**Status:** COMPLETE (commit ae2eb89)

**Files:**
- Modify: `chroma-tui/osc/client.go`
- Modify: `chroma-tui/osc/server.go`

**Step 1: Add overdrive methods to client.go**

In `chroma-tui/osc/client.go`, add these convenience methods after the existing ones:

```go
func (c *Client) SetOverdriveDrive(v float32) error { return c.SendFloat("/chroma/overdriveDrive", v) }
func (c *Client) SetOverdriveTone(v float32) error  { return c.SendFloat("/chroma/overdriveTone", v) }
func (c *Client) SetOverdriveMix(v float32) error   { return c.SendFloat("/chroma/overdriveMix", v) }
```

**Step 2: Add overdrive fields to State struct in server.go**

In `chroma-tui/osc/server.go`, update the State struct to include overdrive fields (insert after FilterResonance):

```go
type State struct {
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
	ReverbDelayBlend     float32
	DecayTime            float32
	ShimmerPitch         float32
	DelayTime            float32
	ModRate              float32
	ModDepth             float32
	ReverbDelayMix       float32
	BlendMode            int
	DryWet               float32
}
```

**Step 3: Update state parsing in server.go**

Update the `/chroma/state` handler to parse 24 parameters (was 21). The order matches Chroma.sc's sendState method:

```go
s.server.Handle("/chroma/state", func(msg *osc.Message) {
	if len(msg.Arguments) >= 24 {
		state := State{
			Gain:                 toFloat32(msg.Arguments[0]),
			InputFrozen:          toInt(msg.Arguments[1]) == 1,
			InputFreezeLength:    toFloat32(msg.Arguments[2]),
			FilterAmount:         toFloat32(msg.Arguments[3]),
			FilterCutoff:         toFloat32(msg.Arguments[4]),
			FilterResonance:      toFloat32(msg.Arguments[5]),
			OverdriveDrive:       toFloat32(msg.Arguments[6]),
			OverdriveTone:        toFloat32(msg.Arguments[7]),
			OverdriveMix:         toFloat32(msg.Arguments[8]),
			GranularDensity:      toFloat32(msg.Arguments[9]),
			GranularSize:         toFloat32(msg.Arguments[10]),
			GranularPitchScatter: toFloat32(msg.Arguments[11]),
			GranularPosScatter:   toFloat32(msg.Arguments[12]),
			GranularMix:          toFloat32(msg.Arguments[13]),
			GranularFrozen:       toInt(msg.Arguments[14]) == 1,
			ReverbDelayBlend:     toFloat32(msg.Arguments[15]),
			DecayTime:            toFloat32(msg.Arguments[16]),
			ShimmerPitch:         toFloat32(msg.Arguments[17]),
			DelayTime:            toFloat32(msg.Arguments[18]),
			ModRate:              toFloat32(msg.Arguments[19]),
			ModDepth:             toFloat32(msg.Arguments[20]),
			ReverbDelayMix:       toFloat32(msg.Arguments[21]),
			BlendMode:            toInt(msg.Arguments[22]),
			DryWet:               toFloat32(msg.Arguments[23]),
		}
		// Non-blocking send
		select {
		case s.stateChan <- state:
		default:
		}
	}
})
```

**Step 4: Build and run tests**

Run: `cd chroma-tui && go build && go test ./osc/...`
Expected: Build succeeds, tests pass

**Step 5: Commit**

```bash
git add chroma-tui/osc/
git commit -m "Add overdrive support to OSC client and server"
```

---

### Task 3: Add overdrive to TUI model and update logic

**Status:** COMPLETE (commit a6a82f2)

**Files:**
- Modify: `chroma-tui/tui/model.go`
- Modify: `chroma-tui/tui/update.go`

**Step 1: Add overdrive controls to control enum in model.go**

Update the control constants to include overdrive (insert after ctrlFilterResonance):

```go
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
```

**Step 2: Add overdrive state fields to Model struct**

Add overdrive fields after FilterResonance:

```go
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
	// ... rest of fields

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

**Step 3: Update NewModel with overdrive defaults**

Add defaults matching Chroma.sc:

```go
func NewModel(client *osc.Client) Model {
	return Model{
		// ... existing fields
		FilterResonance:      0.3,
		OverdriveDrive:       0.5,
		OverdriveTone:        0.7,
		OverdriveMix:         0.0,
		GranularDensity:      20,
		// ... rest
	}
}
```

**Step 4: Update ApplyState to include overdrive**

```go
func (m *Model) ApplyState(s osc.State) {
	// ... existing fields
	m.FilterResonance = s.FilterResonance
	m.OverdriveDrive = s.OverdriveDrive
	m.OverdriveTone = s.OverdriveTone
	m.OverdriveMix = s.OverdriveMix
	m.GranularDensity = s.GranularDensity
	// ... rest
}
```

**Step 5: Add WindowSizeMsg handler and overdrive cases to update.go**

Add WindowSizeMsg case in Update:

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

**Step 6: Add overdrive cases to adjustFocused**

In the `adjustFocused` method, add cases for overdrive controls:

```go
case ctrlOverdriveDrive:
	m.OverdriveDrive = clamp(m.OverdriveDrive+delta, 0, 1)
	m.client.SetOverdriveDrive(m.OverdriveDrive)
case ctrlOverdriveTone:
	m.OverdriveTone = clamp(m.OverdriveTone+delta, 0, 1)
	m.client.SetOverdriveTone(m.OverdriveTone)
case ctrlOverdriveMix:
	m.OverdriveMix = clamp(m.OverdriveMix+delta, 0, 1)
	m.client.SetOverdriveMix(m.OverdriveMix)
```

**Step 7: Build and verify**

Run: `cd chroma-tui && go build`
Expected: Compiles without errors

**Step 8: Commit**

```bash
git add chroma-tui/tui/model.go chroma-tui/tui/update.go
git commit -m "Add overdrive controls and terminal size tracking to TUI model"
```

---

### Task 4: Refactor view.go for full-width stacked layout with overdrive

**Status:** COMPLETE (commits c3d8386, f9c88aa, 6a63e0e, 2283d9f, 3120703)

**Files:**
- Modify: `chroma-tui/tui/view.go`

**Step 1: Replace view.go with full-width stacked layout**

Replace the entire `chroma-tui/tui/view.go` file:

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
	sections = append(sections, m.renderSection("OVERDRIVE", width, m.renderOverdriveControls))
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
Expected: TUI displays with full-width stacked sections including OVERDRIVE, resize terminal to verify responsive width

**Step 4: Commit**

```bash
git add chroma-tui/tui/view.go
git commit -m "Refactor TUI for full-width stacked layout with overdrive section"
```

---

### Task 5: Update documentation

**Status:** COMPLETE (commit 01929c1)

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`

**Step 1: Update README.md**

Ensure documentation reflects:
- SuperCollider runs headless (no GUI window)
- Use chroma-tui for the interface
- Overdrive controls are available

**Step 2: Update CLAUDE.md**

Update the Current Status section to reflect completed TUI refactor with overdrive.

**Step 3: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "Update documentation for TUI refactor with overdrive"
```

---

### Task 6: Ensure jackd cleanup on exit

**Status:** COMPLETE (commit 43e73b4)

**Files:**
- Modify: `run.sh`

**Problem:** Currently `run.sh` only cleans up jackd if sclang exits normally. If interrupted with Ctrl+C or killed, jackd continues running.

**Step 1: Add signal trap to run.sh**

Add a cleanup function and trap after the JACK_PID assignment (after line 85):

```bash
# Cleanup function
cleanup() {
    echo ""
    echo "Stopping JACK..."
    kill $JACK_PID 2>/dev/null
    wait $JACK_PID 2>/dev/null
    exit 0
}

# Trap signals for cleanup
trap cleanup SIGINT SIGTERM EXIT
```

**Step 2: Remove redundant cleanup line**

Remove the existing line 98:
```bash
kill $JACK_PID 2>/dev/null
```

Since the trap now handles cleanup on EXIT, this is no longer needed.

**Step 3: Test signal handling**

Run: `./run.sh`
Then press Ctrl+C
Expected: "Stopping JACK..." message appears and jackd process is terminated

Verify no orphan jackd:
Run: `pgrep jackd`
Expected: No output (no jackd processes running)

**Step 4: Commit**

```bash
git add run.sh
git commit -m "Add signal trap to ensure jackd cleanup on exit"
```

---

### Task 7: Run full integration test

**Status:** COMPLETE (all tests passing)

**Files:**
- None (verification only)

**Step 1: Install updated Chroma.sc**

Run: `cp Chroma.sc ~/.local/share/SuperCollider/Extensions/`

**Step 2: Run integration tests**

Run: `./test_integration.sh`
Expected: All tests pass

**Step 3: Manual verification**

1. Start SuperCollider: `./run.sh` or `sclang -e "Chroma.start"`
2. Verify no GUI window appears
3. In another terminal: `cd chroma-tui && ./chroma-tui`
4. Verify TUI displays full-width with 6 sections (INPUT, FILTER, OVERDRIVE, GRANULAR, REVERB/DELAY, GLOBAL)
5. Test navigation through all controls including overdrive
6. Test overdrive adjustments affect sound
7. Verify instant responsiveness (no lag on key press)
8. Press Ctrl+C in the run.sh terminal - verify jackd is stopped
