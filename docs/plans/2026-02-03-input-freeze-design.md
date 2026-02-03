# Input Freeze Design

## Overview

Add a sustained tone freeze at the input stage, separate from the existing granular freeze. Captures and loops a short segment of audio with adjustable loop length.

## Signal Chain

```
Input → [Input Freeze] → Filter → Granular → Reverb/Delay → Output
                              ↓
                        [Granular Freeze] (existing)
```

## Components

### New SynthDef: `\chroma_input_freeze`

- **Inputs:** `inBus` (audio from hardware)
- **Outputs:** `outBus` (to filter effect)
- **Controls:**
  - `freeze` (0/1) - toggle freeze state
  - `loopLength` (0.05-0.5) - loop duration in seconds

**Behavior:**
- Continuously writes to circular buffer when not frozen
- On freeze: stop writing, loop from current position backward by loopLength
- Crossfade playback using two offset read heads for seamless looping
- On unfreeze: resume writing, output live signal

### Buffer

- `inputFreezeBuffer` - 0.5 seconds at server sample rate, mono
- Allocated at startup in `allocateResources`
- Freed in `cleanup`

### State Variables

- `inputFrozen` - Boolean, default false
- `inputFreezeLength` - Float, default 0.1 (100ms), range 0.05-0.5

### Methods

**New:**
- `toggleInputFreeze` - toggles input freeze state, sends to synth
- `setInputFreezeLength { |val| }` - sets loop length, clips to 0.05-0.5

**Renamed:**
- `toggleFreeze` → `toggleGranularFreeze` (existing granular freeze)

### GUI

Input freeze controls added to INPUT section:

```
┌─ INPUT ─────────────────────────────────────────┐
│  Gain [----●----]    Smoothing [--●------]      │
│  [ INPUT FREEZE ]    Loop [---●----] 100ms      │
└─────────────────────────────────────────────────┘
```

Existing granular FREEZE button unchanged in GRANULAR section.

## Implementation Tasks

1. Add `inputFreezeBuffer` allocation and cleanup
2. Add state variables `inputFrozen`, `inputFreezeLength`
3. Create `\chroma_input_freeze` SynthDef with crossfade looping
4. Add new bus for input freeze output
5. Wire synth into signal chain (before filter)
6. Add `toggleInputFreeze` and `setInputFreezeLength` methods
7. Rename `toggleFreeze` to `toggleGranularFreeze`
8. Update GUI with input freeze button and loop length slider
9. Update tests
