# Chroma TUI Design

**Status:** IMPLEMENTED (2026-02-03). See tui-refactor-design.md for subsequent enhancements.

## Overview

A terminal user interface for Chroma, built in Go with bubbletea. Communicates with SuperCollider via OSC. Includes built-in MIDI support for hardware/DAW control.

## Architecture

```
┌─────────────────┐         OSC          ┌─────────────────┐
│   chroma-tui    │ ──────────────────── │  SuperCollider  │
│   (Go binary)   │                      │    + Chroma     │
└─────────────────┘                      └─────────────────┘
        │
        │ MIDI
        ▼
┌─────────────────┐
│ Hardware/DAW    │
└─────────────────┘
```

**Components:**

1. **chroma-tui** - Go binary using bubbletea for TUI, sends/receives OSC to SuperCollider
2. **Chroma.sc** - Modified to add OSC responders for all parameters and send state updates back
3. **MIDI handler** - Built into chroma-tui, maps CC messages to OSC commands

**Communication:**
- TUI sends OSC messages like `/chroma/gain 0.8`, `/chroma/inputFreeze 1`
- SuperCollider responds with current state so TUI stays in sync
- MIDI CC values get translated to the same OSC messages

## TUI Layout

```
┌─ CHROMA ─────────────────────────────────────────────────────────────────┐
│                                                                          │
│  ┌─ INPUT ──────────┐  ┌─ FILTER ─────────┐  ┌─ GRANULAR ─────────────┐ │
│  │ Gain     [████──] │  │ Amount   [███───] │  │ Density   [████──────] │ │
│  │ Loop     [██────] │  │ Cutoff   [█████─] │  │ Size      [██────────] │ │
│  │ [INPUT FREEZE]    │  │ Resonance[██────] │  │ PitchScat [███───────] │ │
│  └───────────────────┘  └──────────────────┘  │ PosScat   [████──────] │ │
│                                               │ Mix       [███───────] │ │
│  ┌─ REVERB/DELAY ────────────────────────┐   │ [GRAIN FREEZE]         │ │
│  │ Rev<>Dly [████──]  Decay    [█████───] │   └────────────────────────┘ │
│  │ Shimmer  [███───]  DelayTime[██──────] │                              │
│  │ ModRate  [████──]  ModDepth [███─────] │   ┌─ GLOBAL ──────────────┐ │
│  │ Mix      [██────]                      │   │ Mode: [MIRROR]        │ │
│  └────────────────────────────────────────┘   │ Dry/Wet [█████───────] │ │
│                                               └────────────────────────┘ │
│  Status: Connected │ MIDI: Listening on port 1                           │
└──────────────────────────────────────────────────────────────────────────┘
```

**Keyboard interaction:**
- Arrow keys or Tab to navigate between controls
- Left/Right to adjust sliders
- Enter/Space to toggle freeze buttons
- Number keys 1-3 to switch blend modes
- `q` to quit

## OSC Protocol

SuperCollider listens on port 57120 (default). TUI listens on port 9000 for state updates.

### Messages from TUI to SuperCollider

| Path | Args | Description |
|------|------|-------------|
| `/chroma/gain` | float 0-2 | Input gain |
| `/chroma/inputFreeze` | int 0/1 | Toggle input freeze |
| `/chroma/inputFreezeLength` | float 0.05-0.5 | Loop length |
| `/chroma/filterAmount` | float 0-1 | Filter intensity |
| `/chroma/filterCutoff` | float 200-8000 | Filter cutoff Hz |
| `/chroma/filterResonance` | float 0-1 | Filter resonance |
| `/chroma/granularDensity` | float 1-50 | Grain density |
| `/chroma/granularSize` | float 0.01-0.5 | Grain size |
| `/chroma/granularPitchScatter` | float 0-1 | Pitch scatter |
| `/chroma/granularPosScatter` | float 0-1 | Position scatter |
| `/chroma/granularMix` | float 0-1 | Granular mix |
| `/chroma/granularFreeze` | int 0/1 | Toggle granular freeze |
| `/chroma/reverbDelayBlend` | float 0-1 | Reverb/delay balance |
| `/chroma/decayTime` | float 0.1-10 | Decay time |
| `/chroma/shimmerPitch` | float 0-24 | Shimmer pitch (semitones) |
| `/chroma/delayTime` | float 0.01-1 | Delay time |
| `/chroma/modRate` | float 0.1-10 | Mod rate Hz |
| `/chroma/modDepth` | float 0-1 | Mod depth |
| `/chroma/reverbDelayMix` | float 0-1 | Effect mix |
| `/chroma/blendMode` | int 0-2 | 0=mirror, 1=complement, 2=transform |
| `/chroma/dryWet` | float 0-1 | Dry/wet balance |
| `/chroma/sync` | - | Request full state |

### Messages from SuperCollider to TUI

| Path | Args | Description |
|------|------|-------------|
| `/chroma/state` | ...all values | Full state dump on sync request |

## MIDI Mapping

### Default CC Assignments

| CC | Parameter | Range |
|----|-----------|-------|
| 1 | Gain | 0-127 → 0-2 |
| 2 | Input Freeze Length | 0-127 → 50-500ms |
| 3 | Filter Amount | 0-127 → 0-1 |
| 4 | Filter Cutoff | 0-127 → 200-8000 |
| 5 | Filter Resonance | 0-127 → 0-1 |
| 6 | Granular Density | 0-127 → 1-50 |
| 7 | Granular Size | 0-127 → 0.01-0.5 |
| 8 | Granular Mix | 0-127 → 0-1 |
| 9 | Reverb/Delay Blend | 0-127 → 0-1 |
| 10 | Decay Time | 0-127 → 0.1-10 |
| 11 | Dry/Wet | 0-127 → 0-1 |

### Toggle Buttons via Note-On

| Note | Action |
|------|--------|
| C3 (60) | Toggle Input Freeze |
| D3 (62) | Toggle Granular Freeze |
| E3 (64) | Blend Mode: Mirror |
| F3 (65) | Blend Mode: Complement |
| G3 (67) | Blend Mode: Transform |

### Config File

`~/.config/chroma/midi.toml`:

```toml
[cc]
gain = 1
filter_amount = 3
# ... etc

[notes]
input_freeze = 60
granular_freeze = 62
```

## Project Structure

```
chroma-tui/
├── main.go           # Entry point, CLI flags
├── tui/
│   ├── model.go      # Bubbletea model, state
│   ├── view.go       # Render the UI
│   ├── update.go     # Handle key/mouse events
│   └── components.go # Slider, button components
├── osc/
│   ├── client.go     # Send OSC to SuperCollider
│   └── server.go     # Receive state updates
├── midi/
│   ├── handler.go    # MIDI input processing
│   └── mapping.go    # CC/note to parameter mapping
├── config/
│   └── config.go     # Load ~/.config/chroma/midi.toml
└── go.mod
```

## Implementation Tasks

1. Add OSC responders to Chroma.sc
2. Scaffold Go project with bubbletea
3. Implement OSC client (send commands)
4. Implement OSC server (receive state)
5. Build TUI layout and components
6. Add keyboard navigation and control
7. Implement MIDI input handling
8. Add config file loading
9. Update documentation

## Future Consideration: Separate MIDI Bridge

An alternative architecture considered but deferred: running MIDI handling as a separate process.

**Advantages:**
- Run MIDI without TUI (headless, Raspberry Pi, SSH)
- Swap MIDI mappers without touching the TUI
- Smaller binaries (TUI-only users don't need MIDI dependencies)
- Easier independent testing

**Architecture:**
```
┌─────────────────┐         OSC          ┌─────────────────┐
│   chroma-tui    │ ──────────────────── │  SuperCollider  │
└─────────────────┘                      │    + Chroma     │
                                         └─────────────────┘
┌─────────────────┐         OSC                   ▲
│  chroma-midi    │ ──────────────────────────────┘
│    (bridge)     │
└─────────────────┘
        │
        │ MIDI
        ▼
┌─────────────────┐
│ Hardware/DAW    │
└─────────────────┘
```

This could be implemented later if headless MIDI control becomes a requirement.
