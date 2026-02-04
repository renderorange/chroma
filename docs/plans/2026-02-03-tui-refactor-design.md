# TUI Refactor Design

**Date:** 2026-02-03

## Overview

Refactor the Chroma TUI to be the primary interface, removing the SuperCollider GUI entirely. The TUI will display full terminal width with stacked sections and provide instant responsiveness through local-first state updates.

## Changes

### SuperCollider (Chroma.sc)

Remove all GUI code to make the app headless:

- Delete `var <window;` instance variable
- Delete `buildGUI` method (~250 lines)
- Delete `updateBlendButtons` method
- Remove `this.buildGUI;` call from `boot` method
- Remove window close handler from `cleanup`

The app will run headless with OSC as the only control interface.

### TUI Layout

Full terminal width with 6 stacked sections, one control per line:

```
┌─────────────────────────── INPUT ───────────────────────────────┐
│ Gain         [████████████████████────────────────────────] 0.80│
│ Loop         [████────────────────────────────────────────] 0.12│
│ [INPUT FREEZE]                                                  │
└─────────────────────────────────────────────────────────────────┘
┌─────────────────────────── FILTER ──────────────────────────────┐
│ Amount       [██████████████████████──────────────────────] 0.50│
│ Cutoff       [██████████████────────────────────────────] 2000  │
│ Resonance    [██████████████────────────────────────────] 0.30  │
└─────────────────────────────────────────────────────────────────┘
┌────────────────────────── OVERDRIVE ────────────────────────────┐
│ Drive        [██████████████████████──────────────────────] 0.50│
│ Tone         [████████████████████████████────────────────] 0.70│
│ Mix          [────────────────────────────────────────────] 0.00│
└─────────────────────────────────────────────────────────────────┘
┌────────────────────────── GRANULAR ─────────────────────────────┐
│ Density      [████────────────────────────────────────────] 20  │
│ Size         [████────────────────────────────────────────] 0.15│
│ PitchScat    [████────────────────────────────────────────] 0.20│
│ PosScat      [██████──────────────────────────────────────] 0.30│
│ Mix          [██████████████████████──────────────────────] 0.50│
│ [GRAIN FREEZE]                                                  │
└─────────────────────────────────────────────────────────────────┘
┌───────────────────────── REVERB/DELAY ──────────────────────────┐
│ Rev<>Dly     [██████████████████████──────────────────────] 0.50│
│ Decay        [██████████████────────────────────────────────] 3.0│
│ Shimmer      [████████████████████████────────────────────] 12  │
│ DelayTime    [██████────────────────────────────────────────] 0.30│
│ ModRate      [██████████────────────────────────────────────] 0.50│
│ ModDepth     [██████────────────────────────────────────────] 0.30│
│ Mix          [██████────────────────────────────────────────] 0.30│
└─────────────────────────────────────────────────────────────────┘
┌─────────────────────────── GLOBAL ──────────────────────────────┐
│ Mode         [MIRROR] [COMPLEMENT] [TRANSFORM]                  │
│ Dry/Wet      [██████████████████████──────────────────────] 0.50│
└─────────────────────────────────────────────────────────────────┘
```

**Layout details:**
- Fixed-width label column (12 chars)
- Slider bar expands to fill remaining terminal width
- Value displayed at end of slider
- Toggle buttons on their own line, left-aligned
- Mode selector shows all three options inline (only one active)

### TUI Responsiveness

Change from waiting for OSC confirmation to local-first updates:

**Current behavior (causes lag):**
1. User presses key
2. TUI sends OSC to SC
3. SC updates state
4. SC sends `/chroma/state`
5. TUI updates display

**New behavior (instant feedback):**
1. User presses key
2. TUI updates local state immediately
3. TUI re-renders
4. TUI sends OSC to SC in background
5. SC state sync still happens but only corrects drift

**Implementation:**
- `adjustSelected()` modifies local state directly
- `sendParamUpdate()` sends only the changed parameter via OSC (non-blocking)
- No waiting for SC confirmation to update display
- `StateMsg` still processed but typically matches local state
- If SC state differs (e.g., MIDI changed a value), SC state wins

### Protocol

No changes to OSC protocol. Existing messages remain:

- `/chroma/sync` - Request state from SC
- `/chroma/state` - Full state from SC (24 parameters including overdrive)
- `/chroma/[param]` - Individual parameter updates to SC

Overdrive OSC messages (already in Chroma.sc):
- `/chroma/overdriveDrive` - float 0-1
- `/chroma/overdriveTone` - float 0-1
- `/chroma/overdriveMix` - float 0-1

## Future Investigations

| Item | Description | Priority |
|------|-------------|----------|
| Input spectrum display | Add 8-band spectrum visualization at top of TUI via `/chroma/spectrum` OSC | Medium |
| ASCII waveform style | Alternative spectrum display using block characters (▁▂▃▄▅▆▇█) | Low |
| Background refresh timer | Periodic poll in addition to local updates, catches missed state changes | Low |

## Files to Modify

| File | Changes |
|------|---------|
| `Chroma.sc` | Remove GUI code, make headless (already done) |
| `chroma-tui/tui/view.go` | Full-width layout, stacked sections, one control per line, add OVERDRIVE section |
| `chroma-tui/tui/model.go` | Track terminal width, add overdrive state fields and controls |
| `chroma-tui/tui/update.go` | Local-first state updates on key press, add overdrive key handlers |
| `chroma-tui/osc/client.go` | Add overdrive convenience methods (SetOverdriveDrive, SetOverdriveTone, SetOverdriveMix) |
| `chroma-tui/osc/server.go` | Add overdrive fields to State struct, update parsing for 24 parameters |
