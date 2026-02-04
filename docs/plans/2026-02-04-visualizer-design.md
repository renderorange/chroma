# Audio Visualizer Design for Chroma TUI

## Overview

Add real-time audio visualization to the Chroma TUI showing processed audio output. Displays both spectrum analyzer (frequency bars) and waveform (oscilloscope) at the top of the interface.

## Requirements

- **Audio source**: System audio output (processed audio from Chroma via PulseAudio/Pipewire monitor)
- **Visualization**: Both spectrum analyzer and waveform
- **Placement**: Top of UI, above control sections
- **Sizing**: Adaptive based on terminal height, minimum compact size

## Architecture

```
┌─────────────────────────────────────────┐
│  audio/visualizer.go  - New package     │
│  ├── Capture audio via catnip input     │
│  ├── Process with catnip DSP (FFT)      │
│  └── Expose spectrum + waveform data    │
├─────────────────────────────────────────┤
│  tui/model.go - Extended                │
│  ├── Add visualizer state ([]float64)   │
│  └── Add tick subscription for updates  │
├─────────────────────────────────────────┤
│  tui/view.go - Extended                 │
│  ├── Render spectrum bars at top        │
│  └── Render waveform below spectrum     │
└─────────────────────────────────────────┘
```

**Data flow:**
1. catnip captures system audio (PulseAudio/Pipewire monitor)
2. DSP processes samples → FFT spectrum + raw waveform
3. Visualizer sends data via bubbletea message (~30fps)
4. View renders using lipgloss block characters

## Audio Capture

**Backend selection:**
- Primary: Pipewire (modern Linux default)
- Fallback: PulseAudio (broader compatibility)
- Detection: Try pipewire first, fall back to pulseaudio if unavailable

**Monitor source:**
- Capture from system's default output monitor (e.g., `alsa_output.*.monitor`)
- Automatically captures whatever SuperCollider outputs
- No configuration needed if using default audio device

**Configuration:**
```go
type VisualizerConfig struct {
    SampleRate   float64  // 44100 (match SC)
    SampleSize   int      // 1024 (good FFT resolution)
    FrameRate    int      // 30 fps (balance smoothness/CPU)
    SpectrumBins int      // 32-64 bins for display
}
```

**Graceful degradation:**
- If no audio backend available, visualizer section shows "No audio capture available"
- TUI remains fully functional for controls

## Rendering

**Spectrum analyzer:**
```
─── SPECTRUM ───────────────────────────────────────
 █       █
 █ █   █ █ █
 █ █ █ █ █ █ █   █
 █ █ █ █ █ █ █ █ █ █   █   █
▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
 Bass              Mid                    Treble
```

**Waveform:**
```
─── WAVEFORM ───────────────────────────────────────
         ╭──╮      ╭─╮
 ────╮  ╭╯  ╰╮ ╭──╯ ╰──────────────────────────────
     ╰──╯    ╰─╯
```

**Character set:**
- Spectrum bars: `▁▂▃▄▅▆▇█` (8 levels per character cell)
- Waveform: `─╭╮╯╰│` (smooth curves via box drawing)
- Stereo: Left channel top half, right channel bottom half (mirrored)

**Colors:**
- Match existing pink accent (`lipgloss.Color("205")`)
- Gradient option: bass=blue, mid=pink, treble=white

**Adaptive sizing:**
```go
func visualizerHeight(termHeight int) (spectrum, waveform int) {
    available := termHeight - controlsHeight - statusHeight
    if available < 8 {
        return 3, 3  // minimum compact
    }
    if available < 16 {
        return available/2, available/2
    }
    return 8, 8  // cap at comfortable max
}
```

## Integration

**New files:**
```
chroma-tui/
├── audio/
│   └── visualizer.go    # Catnip wrapper, FFT, data extraction
└── tui/
    └── visualizer.go    # Rendering functions for spectrum/waveform
```

**Changes to existing files:**

`main.go` - Start visualizer goroutine:
```go
// Start audio visualizer
vis := audio.NewVisualizer()
if err := vis.Start(); err != nil {
    fmt.Fprintf(os.Stderr, "Visualizer warning: %v\n", err)
}
defer vis.Stop()

// Forward visualizer frames to TUI
go func() {
    for frame := range vis.Frames() {
        p.Send(tui.VisualizerMsg(frame))
    }
}()
```

`tui/model.go` - Add visualizer state:
```go
type Model struct {
    // ... existing fields ...

    // Visualizer
    spectrum    []float64  // FFT bins (0-1 normalized)
    waveform    []float64  // Raw samples (-1 to 1)
    visEnabled  bool       // Whether capture is active
}
```

`tui/view.go` - Render at top:
```go
func (m Model) View() string {
    var sections []string

    // Visualizer at top (if enabled)
    if m.visEnabled {
        sections = append(sections, m.renderVisualizer(width))
    }

    // ... existing sections ...
}
```

## Dependencies

**New Go dependencies:**
```go
require (
    github.com/noriah/catnip v1.x.x  // Audio capture + DSP
)
```

**System dependencies:**
- Pipewire: `pw-cat`, `pw-link` (usually pre-installed on modern Linux)
- PulseAudio fallback: `parec` (part of pulseaudio-utils)
- No CGO required (pure Go with external binaries)

**Build tags:**
- Default build works with pipewire/pulseaudio via external commands
- Optional: `-tags portaudio` for PortAudio (requires CGO + libportaudio)
- Optional: `-tags fftw` for faster FFT (requires CGO + libfftw3)

**Error handling:**
- Missing `pw-cat`/`parec` → visualizer disabled, warning printed
- Audio device busy → retry with backoff, then disable
- No monitor device found → show "No audio monitor available"

## Status

- [ ] Implementation not started
