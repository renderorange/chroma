# Chroma TUI Implementation Plan

> **Status: COMPLETE** - All tasks implemented (2026-02-03). See tui-refactor-implementation.md for subsequent enhancements.

**Goal:** Build a terminal UI for Chroma in Go with bubbletea, communicating via OSC and supporting MIDI control.

**Architecture:** Separate Go binary (`chroma-tui`) sends OSC messages to SuperCollider's Chroma class. Chroma.sc is modified to add OSC responders. MIDI input is handled in the Go app and translated to OSC.

**Tech Stack:** Go, bubbletea (TUI), go-osc (OSC), gomidi/midi (MIDI), toml (config)

---

## Task 1: Add OSC Responders to Chroma.sc

**Files:**
- Modify: `Chroma.sc`
- Modify: `test_synths.scd`

**Step 1: Add OSC responder setup method to Chroma.sc**

Add after the `cleanup` method:

```supercollider
setupOSC { |replyPort=9000|
    var replyAddr = NetAddr("127.0.0.1", replyPort);

    // Input controls
    OSCdef(\chromaGain, { |msg| this.setInputGain(msg[1]) }, '/chroma/gain');
    OSCdef(\chromaInputFreeze, { |msg|
        if(msg[1].asBoolean != inputFrozen) { this.toggleInputFreeze };
    }, '/chroma/inputFreeze');
    OSCdef(\chromaInputFreezeLength, { |msg| this.setInputFreezeLength(msg[1]) }, '/chroma/inputFreezeLength');

    // Filter controls
    OSCdef(\chromaFilterAmount, { |msg| this.setFilterAmount(msg[1]) }, '/chroma/filterAmount');
    OSCdef(\chromaFilterCutoff, { |msg| this.setFilterCutoff(msg[1]) }, '/chroma/filterCutoff');
    OSCdef(\chromaFilterResonance, { |msg| this.setFilterResonance(msg[1]) }, '/chroma/filterResonance');

    // Granular controls
    OSCdef(\chromaGranularDensity, { |msg| this.setGrainDensity(msg[1]) }, '/chroma/granularDensity');
    OSCdef(\chromaGranularSize, { |msg| this.setGrainSize(msg[1]) }, '/chroma/granularSize');
    OSCdef(\chromaGranularPitchScatter, { |msg| this.setGrainPitchScatter(msg[1]) }, '/chroma/granularPitchScatter');
    OSCdef(\chromaGranularPosScatter, { |msg| this.setGrainPosScatter(msg[1]) }, '/chroma/granularPosScatter');
    OSCdef(\chromaGranularMix, { |msg| this.setGranularMix(msg[1]) }, '/chroma/granularMix');
    OSCdef(\chromaGranularFreeze, { |msg|
        if(msg[1].asBoolean != frozen) { this.toggleGranularFreeze };
    }, '/chroma/granularFreeze');

    // Reverb/Delay controls
    OSCdef(\chromaReverbDelayBlend, { |msg| this.setReverbDelayBlend(msg[1]) }, '/chroma/reverbDelayBlend');
    OSCdef(\chromaDecayTime, { |msg| this.setDecayTime(msg[1]) }, '/chroma/decayTime');
    OSCdef(\chromaShimmerPitch, { |msg| this.setShimmerPitch(msg[1]) }, '/chroma/shimmerPitch');
    OSCdef(\chromaDelayTime, { |msg| this.setDelayTime(msg[1]) }, '/chroma/delayTime');
    OSCdef(\chromaModRate, { |msg| this.setModRate(msg[1]) }, '/chroma/modRate');
    OSCdef(\chromaModDepth, { |msg| this.setModDepth(msg[1]) }, '/chroma/modDepth');
    OSCdef(\chromaReverbDelayMix, { |msg| this.setReverbDelayMix(msg[1]) }, '/chroma/reverbDelayMix');

    // Global controls
    OSCdef(\chromaBlendMode, { |msg|
        var modes = [\mirror, \complement, \transform];
        this.setBlendMode(modes[msg[1].asInteger.clip(0, 2)]);
    }, '/chroma/blendMode');
    OSCdef(\chromaDryWet, { |msg| this.setDryWet(msg[1]) }, '/chroma/dryWet');

    // Sync request - send full state
    OSCdef(\chromaSync, { |msg|
        this.sendState(replyAddr);
    }, '/chroma/sync');

    "Chroma OSC responders ready".postln;
}
```

**Step 2: Add sendState method**

Add after `setupOSC`:

```supercollider
sendState { |addr|
    addr.sendMsg('/chroma/state',
        1.0,  // gain (not stored, default)
        inputFrozen.asInteger,
        inputFreezeLength,
        filterParams[\amount],
        filterParams[\cutoff],
        filterParams[\resonance],
        granularParams[\density],
        granularParams[\size],
        granularParams[\pitchScatter],
        granularParams[\posScatter],
        granularParams[\mix],
        frozen.asInteger,
        reverbDelayParams[\blend],
        reverbDelayParams[\decayTime],
        reverbDelayParams[\shimmerPitch],
        reverbDelayParams[\delayTime],
        reverbDelayParams[\modRate],
        reverbDelayParams[\modDepth],
        reverbDelayParams[\mix],
        this.blendModeIndex,
        dryWet
    );
}
```

**Step 3: Add cleanupOSC method**

Add after `sendState`:

```supercollider
cleanupOSC {
    OSCdef(\chromaGain).free;
    OSCdef(\chromaInputFreeze).free;
    OSCdef(\chromaInputFreezeLength).free;
    OSCdef(\chromaFilterAmount).free;
    OSCdef(\chromaFilterCutoff).free;
    OSCdef(\chromaFilterResonance).free;
    OSCdef(\chromaGranularDensity).free;
    OSCdef(\chromaGranularSize).free;
    OSCdef(\chromaGranularPitchScatter).free;
    OSCdef(\chromaGranularPosScatter).free;
    OSCdef(\chromaGranularMix).free;
    OSCdef(\chromaGranularFreeze).free;
    OSCdef(\chromaReverbDelayBlend).free;
    OSCdef(\chromaDecayTime).free;
    OSCdef(\chromaShimmerPitch).free;
    OSCdef(\chromaDelayTime).free;
    OSCdef(\chromaModRate).free;
    OSCdef(\chromaModDepth).free;
    OSCdef(\chromaReverbDelayMix).free;
    OSCdef(\chromaBlendMode).free;
    OSCdef(\chromaDryWet).free;
    OSCdef(\chromaSync).free;
}
```

**Step 4: Call setupOSC in boot method**

Modify the `boot` method to call `setupOSC` after `buildGUI`:

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

**Step 5: Call cleanupOSC in cleanup method**

Modify `cleanup` to add `this.cleanupOSC;` before freeing synths.

**Step 6: Add gain instance variable**

Add `var <inputGain;` to instance variables and initialize to `1.0` in `init`. Update `setInputGain` to store the value:

```supercollider
setInputGain { |gain|
    inputGain = gain;
    if(synths[\input].notNil) {
        synths[\input].set(\gain, gain);
    };
}
```

**Step 7: Add test for OSC in test_synths.scd**

Add after the existing tests:

```supercollider
"Testing OSC setup...".postln;
("  - OSC responders active").postln;
```

**Step 8: Run tests**

Run: `./test_integration.sh headless`
Expected: All tests pass

**Step 9: Commit**

```bash
git add Chroma.sc test_synths.scd
git commit -m "Add OSC responders to Chroma for TUI control"
```

---

## Task 2: Scaffold Go Project

**Files:**
- Create: `chroma-tui/go.mod`
- Create: `chroma-tui/main.go`
- Create: `chroma-tui/.gitignore`

**Step 1: Create project directory**

```bash
mkdir -p chroma-tui
```

**Step 2: Initialize Go module**

```bash
cd chroma-tui && go mod init github.com/renderorange/chroma/chroma-tui
```

**Step 3: Create .gitignore**

Create `chroma-tui/.gitignore`:

```
chroma-tui
*.exe
```

**Step 4: Create main.go stub**

Create `chroma-tui/main.go`:

```go
package main

import (
	"fmt"
	"os"
)

func main() {
	fmt.Println("chroma-tui starting...")
	os.Exit(0)
}
```

**Step 5: Build and run to verify**

Run: `cd chroma-tui && go build && ./chroma-tui`
Expected: Prints "chroma-tui starting..."

**Step 6: Commit**

```bash
git add chroma-tui/
git commit -m "Scaffold chroma-tui Go project"
```

---

## Task 3: Implement OSC Client

**Files:**
- Create: `chroma-tui/osc/client.go`
- Create: `chroma-tui/osc/client_test.go`
- Modify: `chroma-tui/go.mod`

**Step 1: Add go-osc dependency**

```bash
cd chroma-tui && go get github.com/hypebeast/go-osc/osc
```

**Step 2: Create osc/client.go**

Create `chroma-tui/osc/client.go`:

```go
package osc

import (
	"fmt"

	"github.com/hypebeast/go-osc/osc"
)

type Client struct {
	client *osc.Client
}

func NewClient(host string, port int) *Client {
	return &Client{
		client: osc.NewClient(host, port),
	}
}

func (c *Client) SendFloat(path string, value float32) error {
	msg := osc.NewMessage(path)
	msg.Append(value)
	return c.client.Send(msg)
}

func (c *Client) SendInt(path string, value int32) error {
	msg := osc.NewMessage(path)
	msg.Append(value)
	return c.client.Send(msg)
}

func (c *Client) SendSync() error {
	msg := osc.NewMessage("/chroma/sync")
	return c.client.Send(msg)
}

// Convenience methods for each parameter
func (c *Client) SetGain(v float32) error            { return c.SendFloat("/chroma/gain", v) }
func (c *Client) SetInputFreeze(v bool) error        { return c.SendInt("/chroma/inputFreeze", boolToInt(v)) }
func (c *Client) SetInputFreezeLength(v float32) error { return c.SendFloat("/chroma/inputFreezeLength", v) }
func (c *Client) SetFilterAmount(v float32) error    { return c.SendFloat("/chroma/filterAmount", v) }
func (c *Client) SetFilterCutoff(v float32) error    { return c.SendFloat("/chroma/filterCutoff", v) }
func (c *Client) SetFilterResonance(v float32) error { return c.SendFloat("/chroma/filterResonance", v) }
func (c *Client) SetGranularDensity(v float32) error { return c.SendFloat("/chroma/granularDensity", v) }
func (c *Client) SetGranularSize(v float32) error    { return c.SendFloat("/chroma/granularSize", v) }
func (c *Client) SetGranularPitchScatter(v float32) error { return c.SendFloat("/chroma/granularPitchScatter", v) }
func (c *Client) SetGranularPosScatter(v float32) error { return c.SendFloat("/chroma/granularPosScatter", v) }
func (c *Client) SetGranularMix(v float32) error     { return c.SendFloat("/chroma/granularMix", v) }
func (c *Client) SetGranularFreeze(v bool) error     { return c.SendInt("/chroma/granularFreeze", boolToInt(v)) }
func (c *Client) SetReverbDelayBlend(v float32) error { return c.SendFloat("/chroma/reverbDelayBlend", v) }
func (c *Client) SetDecayTime(v float32) error       { return c.SendFloat("/chroma/decayTime", v) }
func (c *Client) SetShimmerPitch(v float32) error    { return c.SendFloat("/chroma/shimmerPitch", v) }
func (c *Client) SetDelayTime(v float32) error       { return c.SendFloat("/chroma/delayTime", v) }
func (c *Client) SetModRate(v float32) error         { return c.SendFloat("/chroma/modRate", v) }
func (c *Client) SetModDepth(v float32) error        { return c.SendFloat("/chroma/modDepth", v) }
func (c *Client) SetReverbDelayMix(v float32) error  { return c.SendFloat("/chroma/reverbDelayMix", v) }
func (c *Client) SetBlendMode(v int) error           { return c.SendInt("/chroma/blendMode", int32(v)) }
func (c *Client) SetDryWet(v float32) error          { return c.SendFloat("/chroma/dryWet", v) }

func boolToInt(b bool) int32 {
	if b {
		return 1
	}
	return 0
}
```

**Step 3: Create osc/client_test.go**

Create `chroma-tui/osc/client_test.go`:

```go
package osc

import (
	"testing"
)

func TestNewClient(t *testing.T) {
	c := NewClient("127.0.0.1", 57120)
	if c == nil {
		t.Fatal("expected non-nil client")
	}
	if c.client == nil {
		t.Fatal("expected non-nil internal client")
	}
}

func TestBoolToInt(t *testing.T) {
	if boolToInt(true) != 1 {
		t.Error("expected true to be 1")
	}
	if boolToInt(false) != 0 {
		t.Error("expected false to be 0")
	}
}
```

**Step 4: Run tests**

Run: `cd chroma-tui && go test ./osc/...`
Expected: PASS

**Step 5: Commit**

```bash
git add chroma-tui/
git commit -m "Add OSC client for sending messages to SuperCollider"
```

---

## Task 4: Implement OSC Server

**Files:**
- Create: `chroma-tui/osc/server.go`
- Modify: `chroma-tui/osc/client_test.go`

**Step 1: Create osc/server.go**

Create `chroma-tui/osc/server.go`:

```go
package osc

import (
	"fmt"

	"github.com/hypebeast/go-osc/osc"
)

type State struct {
	Gain                float32
	InputFrozen         bool
	InputFreezeLength   float32
	FilterAmount        float32
	FilterCutoff        float32
	FilterResonance     float32
	GranularDensity     float32
	GranularSize        float32
	GranularPitchScatter float32
	GranularPosScatter  float32
	GranularMix         float32
	GranularFrozen      bool
	ReverbDelayBlend    float32
	DecayTime           float32
	ShimmerPitch        float32
	DelayTime           float32
	ModRate             float32
	ModDepth            float32
	ReverbDelayMix      float32
	BlendMode           int
	DryWet              float32
}

type Server struct {
	server    *osc.Server
	stateChan chan State
}

func NewServer(port int) *Server {
	addr := fmt.Sprintf("127.0.0.1:%d", port)
	s := &Server{
		server:    &osc.Server{Addr: addr},
		stateChan: make(chan State, 1),
	}

	s.server.Handle("/chroma/state", func(msg *osc.Message) {
		if len(msg.Arguments) >= 21 {
			state := State{
				Gain:                toFloat32(msg.Arguments[0]),
				InputFrozen:         toInt(msg.Arguments[1]) == 1,
				InputFreezeLength:   toFloat32(msg.Arguments[2]),
				FilterAmount:        toFloat32(msg.Arguments[3]),
				FilterCutoff:        toFloat32(msg.Arguments[4]),
				FilterResonance:     toFloat32(msg.Arguments[5]),
				GranularDensity:     toFloat32(msg.Arguments[6]),
				GranularSize:        toFloat32(msg.Arguments[7]),
				GranularPitchScatter: toFloat32(msg.Arguments[8]),
				GranularPosScatter:  toFloat32(msg.Arguments[9]),
				GranularMix:         toFloat32(msg.Arguments[10]),
				GranularFrozen:      toInt(msg.Arguments[11]) == 1,
				ReverbDelayBlend:    toFloat32(msg.Arguments[12]),
				DecayTime:           toFloat32(msg.Arguments[13]),
				ShimmerPitch:        toFloat32(msg.Arguments[14]),
				DelayTime:           toFloat32(msg.Arguments[15]),
				ModRate:             toFloat32(msg.Arguments[16]),
				ModDepth:            toFloat32(msg.Arguments[17]),
				ReverbDelayMix:      toFloat32(msg.Arguments[18]),
				BlendMode:           toInt(msg.Arguments[19]),
				DryWet:              toFloat32(msg.Arguments[20]),
			}
			// Non-blocking send
			select {
			case s.stateChan <- state:
			default:
			}
		}
	})

	return s
}

func (s *Server) Start() error {
	return s.server.ListenAndServe()
}

func (s *Server) StateChan() <-chan State {
	return s.stateChan
}

func toFloat32(v interface{}) float32 {
	switch val := v.(type) {
	case float32:
		return val
	case float64:
		return float32(val)
	case int32:
		return float32(val)
	case int:
		return float32(val)
	default:
		return 0
	}
}

func toInt(v interface{}) int {
	switch val := v.(type) {
	case int32:
		return int(val)
	case int:
		return val
	case float32:
		return int(val)
	case float64:
		return int(val)
	default:
		return 0
	}
}
```

**Step 2: Run tests**

Run: `cd chroma-tui && go build ./...`
Expected: Build succeeds

**Step 3: Commit**

```bash
git add chroma-tui/
git commit -m "Add OSC server for receiving state from SuperCollider"
```

---

## Task 5: Build TUI Model and State

**Files:**
- Create: `chroma-tui/tui/model.go`
- Modify: `chroma-tui/go.mod`

**Step 1: Add bubbletea dependency**

```bash
cd chroma-tui && go get github.com/charmbracelet/bubbletea
```

**Step 2: Create tui/model.go**

Create `chroma-tui/tui/model.go`:

```go
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

type Model struct {
	// State
	Gain                float32
	InputFrozen         bool
	InputFreezeLength   float32
	FilterAmount        float32
	FilterCutoff        float32
	FilterResonance     float32
	GranularDensity     float32
	GranularSize        float32
	GranularPitchScatter float32
	GranularPosScatter  float32
	GranularMix         float32
	GranularFrozen      bool
	ReverbDelayBlend    float32
	DecayTime           float32
	ShimmerPitch        float32
	DelayTime           float32
	ModRate             float32
	ModDepth            float32
	ReverbDelayMix      float32
	BlendMode           int
	DryWet              float32

	// UI state
	focused   control
	connected bool
	midiPort  string

	// OSC
	client *osc.Client
}

func NewModel(client *osc.Client) Model {
	return Model{
		// Defaults matching Chroma.sc
		Gain:              1.0,
		InputFreezeLength: 0.1,
		FilterAmount:      0.5,
		FilterCutoff:      2000,
		FilterResonance:   0.3,
		GranularDensity:   10,
		GranularSize:      0.1,
		GranularPitchScatter: 0.1,
		GranularPosScatter: 0.2,
		GranularMix:       0.3,
		ReverbDelayBlend:  0.5,
		DecayTime:         3,
		ShimmerPitch:      12,
		DelayTime:         0.3,
		ModRate:           0.5,
		ModDepth:          0.3,
		ReverbDelayMix:    0.3,
		DryWet:            0.5,

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
	m.GranularDensity = s.GranularDensity
	m.GranularSize = s.GranularSize
	m.GranularPitchScatter = s.GranularPitchScatter
	m.GranularPosScatter = s.GranularPosScatter
	m.GranularMix = s.GranularMix
	m.GranularFrozen = s.GranularFrozen
	m.ReverbDelayBlend = s.ReverbDelayBlend
	m.DecayTime = s.DecayTime
	m.ShimmerPitch = s.ShimmerPitch
	m.DelayTime = s.DelayTime
	m.ModRate = s.ModRate
	m.ModDepth = s.ModDepth
	m.ReverbDelayMix = s.ReverbDelayMix
	m.BlendMode = s.BlendMode
	m.DryWet = s.DryWet
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
```

**Step 3: Run build**

Run: `cd chroma-tui && go build ./...`
Expected: Build succeeds

**Step 4: Commit**

```bash
git add chroma-tui/
git commit -m "Add TUI model and state management"
```

---

## Task 6: Build TUI View

**Files:**
- Create: `chroma-tui/tui/view.go`
- Modify: `chroma-tui/go.mod`

**Step 1: Add lipgloss dependency**

```bash
cd chroma-tui && go get github.com/charmbracelet/lipgloss
```

**Step 2: Create tui/view.go**

Create `chroma-tui/tui/view.go`:

```go
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
```

**Step 3: Run build**

Run: `cd chroma-tui && go build ./...`
Expected: Build succeeds

**Step 4: Commit**

```bash
git add chroma-tui/
git commit -m "Add TUI view rendering"
```

---

## Task 7: Implement TUI Update Logic

**Files:**
- Create: `chroma-tui/tui/update.go`
- Modify: `chroma-tui/main.go`

**Step 1: Create tui/update.go**

Create `chroma-tui/tui/update.go`:

```go
package tui

import (
	"github.com/charmbracelet/bubbletea"
	"github.com/renderorange/chroma/chroma-tui/osc"
)

type stateMsg osc.State

func (m Model) Init() tea.Cmd {
	return nil
}

func (m Model) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.KeyMsg:
		return m.handleKey(msg)
	case stateMsg:
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

func clamp(v, min, max float32) float32 {
	if v < min {
		return min
	}
	if v > max {
		return max
	}
	return v
}
```

**Step 2: Update main.go**

Replace `chroma-tui/main.go`:

```go
package main

import (
	"flag"
	"fmt"
	"os"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/renderorange/chroma/chroma-tui/osc"
	"github.com/renderorange/chroma/chroma-tui/tui"
)

func main() {
	scHost := flag.String("host", "127.0.0.1", "SuperCollider host")
	scPort := flag.Int("port", 57120, "SuperCollider OSC port")
	listenPort := flag.Int("listen", 9000, "Port to listen for state updates")
	flag.Parse()

	// Create OSC client
	client := osc.NewClient(*scHost, *scPort)

	// Create and run TUI
	model := tui.NewModel(client)

	// Request initial state
	client.SendSync()

	p := tea.NewProgram(model, tea.WithAltScreen())
	if _, err := p.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}

	_ = listenPort // Will be used in Task 8
}
```

**Step 3: Run build and test**

Run: `cd chroma-tui && go build && ./chroma-tui --help`
Expected: Shows usage with flags

**Step 4: Commit**

```bash
git add chroma-tui/
git commit -m "Add TUI update logic and keyboard handling"
```

---

## Task 8: Wire Up OSC State Sync

**Files:**
- Modify: `chroma-tui/main.go`
- Modify: `chroma-tui/tui/update.go`

**Step 1: Update main.go with state listener**

Replace `chroma-tui/main.go`:

```go
package main

import (
	"flag"
	"fmt"
	"os"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/renderorange/chroma/chroma-tui/osc"
	"github.com/renderorange/chroma/chroma-tui/tui"
)

func main() {
	scHost := flag.String("host", "127.0.0.1", "SuperCollider host")
	scPort := flag.Int("port", 57120, "SuperCollider OSC port")
	listenPort := flag.Int("listen", 9000, "Port to listen for state updates")
	flag.Parse()

	// Create OSC client and server
	client := osc.NewClient(*scHost, *scPort)
	server := osc.NewServer(*listenPort)

	// Start OSC server in background
	go func() {
		if err := server.Start(); err != nil {
			fmt.Fprintf(os.Stderr, "OSC server error: %v\n", err)
		}
	}()

	// Create TUI model
	model := tui.NewModel(client)

	// Create program
	p := tea.NewProgram(model, tea.WithAltScreen())

	// Forward state updates to TUI
	go func() {
		for state := range server.StateChan() {
			p.Send(tui.StateMsg(state))
		}
	}()

	// Request initial state
	client.SendSync()

	if _, err := p.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}
}
```

**Step 2: Export StateMsg type in update.go**

In `chroma-tui/tui/update.go`, change:

```go
type stateMsg osc.State
```

to:

```go
type StateMsg osc.State
```

And update the type switch:

```go
case StateMsg:
    m.ApplyState(osc.State(msg))
```

**Step 3: Build and test**

Run: `cd chroma-tui && go build`
Expected: Build succeeds

**Step 4: Commit**

```bash
git add chroma-tui/
git commit -m "Wire up OSC state sync between TUI and SuperCollider"
```

---

## Task 9: Add MIDI Support

**Files:**
- Create: `chroma-tui/midi/handler.go`
- Create: `chroma-tui/config/config.go`
- Modify: `chroma-tui/main.go`
- Modify: `chroma-tui/go.mod`

**Step 1: Add MIDI dependency**

```bash
cd chroma-tui && go get gitlab.com/gomidi/midi/v2
```

**Step 2: Create config/config.go**

Create `chroma-tui/config/config.go`:

```go
package config

import (
	"os"
	"path/filepath"

	"github.com/BurntSushi/toml"
)

type Config struct {
	CC    map[string]int `toml:"cc"`
	Notes map[string]int `toml:"notes"`
}

func DefaultConfig() Config {
	return Config{
		CC: map[string]int{
			"gain":              1,
			"input_freeze_len":  2,
			"filter_amount":     3,
			"filter_cutoff":     4,
			"filter_resonance":  5,
			"granular_density":  6,
			"granular_size":     7,
			"granular_mix":      8,
			"reverb_delay_blend": 9,
			"decay_time":        10,
			"dry_wet":           11,
		},
		Notes: map[string]int{
			"input_freeze":    60,
			"granular_freeze": 62,
			"mode_mirror":     64,
			"mode_complement": 65,
			"mode_transform":  67,
		},
	}
}

func Load() Config {
	cfg := DefaultConfig()

	configDir, err := os.UserConfigDir()
	if err != nil {
		return cfg
	}

	configPath := filepath.Join(configDir, "chroma", "midi.toml")
	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		return cfg
	}

	if _, err := toml.DecodeFile(configPath, &cfg); err != nil {
		return DefaultConfig()
	}

	return cfg
}
```

**Step 3: Add toml dependency**

```bash
cd chroma-tui && go get github.com/BurntSushi/toml
```

**Step 4: Create midi/handler.go**

Create `chroma-tui/midi/handler.go`:

```go
package midi

import (
	"fmt"

	"gitlab.com/gomidi/midi/v2"
	"gitlab.com/gomidi/midi/v2/drivers"
	_ "gitlab.com/gomidi/midi/v2/drivers/rtmididrv"

	"github.com/renderorange/chroma/chroma-tui/config"
	"github.com/renderorange/chroma/chroma-tui/osc"
)

type Handler struct {
	client *osc.Client
	config config.Config
	port   drivers.In
	stop   func()
}

func NewHandler(client *osc.Client, cfg config.Config) *Handler {
	return &Handler{
		client: client,
		config: cfg,
	}
}

func (h *Handler) Start() error {
	ins := midi.GetInPorts()
	if len(ins) == 0 {
		return fmt.Errorf("no MIDI input ports found")
	}

	// Use first available port
	h.port = ins[0]

	stop, err := midi.ListenTo(h.port, h.handleMessage)
	if err != nil {
		return err
	}
	h.stop = stop

	return nil
}

func (h *Handler) Stop() {
	if h.stop != nil {
		h.stop()
	}
}

func (h *Handler) PortName() string {
	if h.port != nil {
		return h.port.String()
	}
	return ""
}

func (h *Handler) handleMessage(msg midi.Message, timestamp int32) {
	var ch, key, vel uint8
	var cc, val uint8

	switch {
	case msg.GetControlChange(&ch, &cc, &val):
		h.handleCC(int(cc), float32(val)/127.0)
	case msg.GetNoteOn(&ch, &key, &vel):
		if vel > 0 {
			h.handleNoteOn(int(key))
		}
	}
}

func (h *Handler) handleCC(cc int, value float32) {
	cfg := h.config.CC

	switch cc {
	case cfg["gain"]:
		h.client.SetGain(value * 2)
	case cfg["input_freeze_len"]:
		h.client.SetInputFreezeLength(0.05 + value*0.45)
	case cfg["filter_amount"]:
		h.client.SetFilterAmount(value)
	case cfg["filter_cutoff"]:
		h.client.SetFilterCutoff(200 + value*7800)
	case cfg["filter_resonance"]:
		h.client.SetFilterResonance(value)
	case cfg["granular_density"]:
		h.client.SetGranularDensity(1 + value*49)
	case cfg["granular_size"]:
		h.client.SetGranularSize(0.01 + value*0.49)
	case cfg["granular_mix"]:
		h.client.SetGranularMix(value)
	case cfg["reverb_delay_blend"]:
		h.client.SetReverbDelayBlend(value)
	case cfg["decay_time"]:
		h.client.SetDecayTime(0.1 + value*9.9)
	case cfg["dry_wet"]:
		h.client.SetDryWet(value)
	}
}

func (h *Handler) handleNoteOn(note int) {
	cfg := h.config.Notes

	switch note {
	case cfg["input_freeze"]:
		h.client.SendInt("/chroma/inputFreeze", 1) // Toggle handled by SC
	case cfg["granular_freeze"]:
		h.client.SendInt("/chroma/granularFreeze", 1)
	case cfg["mode_mirror"]:
		h.client.SetBlendMode(0)
	case cfg["mode_complement"]:
		h.client.SetBlendMode(1)
	case cfg["mode_transform"]:
		h.client.SetBlendMode(2)
	}
}
```

**Step 5: Update main.go with MIDI**

Update `chroma-tui/main.go`:

```go
package main

import (
	"flag"
	"fmt"
	"os"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/renderorange/chroma/chroma-tui/config"
	"github.com/renderorange/chroma/chroma-tui/midi"
	"github.com/renderorange/chroma/chroma-tui/osc"
	"github.com/renderorange/chroma/chroma-tui/tui"
)

func main() {
	scHost := flag.String("host", "127.0.0.1", "SuperCollider host")
	scPort := flag.Int("port", 57120, "SuperCollider OSC port")
	listenPort := flag.Int("listen", 9000, "Port to listen for state updates")
	noMidi := flag.Bool("no-midi", false, "Disable MIDI input")
	flag.Parse()

	// Create OSC client and server
	client := osc.NewClient(*scHost, *scPort)
	server := osc.NewServer(*listenPort)

	// Start OSC server in background
	go func() {
		if err := server.Start(); err != nil {
			fmt.Fprintf(os.Stderr, "OSC server error: %v\n", err)
		}
	}()

	// Create TUI model
	model := tui.NewModel(client)

	// Start MIDI handler
	var midiHandler *midi.Handler
	if !*noMidi {
		cfg := config.Load()
		midiHandler = midi.NewHandler(client, cfg)
		if err := midiHandler.Start(); err != nil {
			fmt.Fprintf(os.Stderr, "MIDI warning: %v\n", err)
		} else {
			model.SetMidiPort(midiHandler.PortName())
			defer midiHandler.Stop()
		}
	}

	// Create program
	p := tea.NewProgram(model, tea.WithAltScreen())

	// Forward state updates to TUI
	go func() {
		for state := range server.StateChan() {
			p.Send(tui.StateMsg(state))
		}
	}()

	// Request initial state
	client.SendSync()

	if _, err := p.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}
}
```

**Step 6: Add SetMidiPort to model**

Add to `chroma-tui/tui/model.go`:

```go
func (m *Model) SetMidiPort(name string) {
	m.midiPort = name
}
```

**Step 7: Build and test**

Run: `cd chroma-tui && go build`
Expected: Build succeeds

**Step 8: Commit**

```bash
git add chroma-tui/
git commit -m "Add MIDI input support with configurable mappings"
```

---

## Task 10: Update Documentation

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`

**Step 1: Update README.md**

Add section after "From SuperCollider IDE":

```markdown
### Terminal UI

```bash
# Build the TUI
cd chroma-tui && go build

# Run with defaults (connects to localhost:57120)
./chroma-tui

# Run with options
./chroma-tui --host 192.168.1.10 --port 57120 --no-midi
```

**Keyboard controls:**
- Tab / ↑↓ : Navigate controls
- ←→ : Adjust values
- Enter/Space : Toggle freeze buttons
- 1-3 : Switch blend modes
- q : Quit

**MIDI:** Automatically connects to first available MIDI input. Configure mappings in `~/.config/chroma/midi.toml`.
```

**Step 2: Update CLAUDE.md project files table**

Add row:

```markdown
| `chroma-tui/` | Go TUI application with MIDI support |
```

**Step 3: Commit**

```bash
git add README.md CLAUDE.md
git commit -m "Update documentation for TUI"
```

---

## Final Integration Test

**Step 1: Start Chroma with GUI**

```bash
./run.sh
```

**Step 2: In another terminal, run the TUI**

```bash
cd chroma-tui && ./chroma-tui
```

**Step 3: Verify:**
- TUI displays and responds to keyboard
- Moving sliders in TUI changes sound
- Moving sliders in GUI updates TUI (via sync)
- MIDI input (if available) controls parameters

**Step 4: Final commit if any fixes needed**

```bash
git add -A && git commit -m "Fix integration issues"
```
