# Chroma: Spectral-Reactive Drone Synthesizer

**Status:** SUPERSEDED by chroma-effects-design.md (redesigned as effects processor)

## Overview

Chroma is a real-time audio processing application built in SuperCollider. It analyzes the spectral content of incoming audio and uses that analysis to shape evolving drone and pad textures. Multiple "blend modes" define different relationships between input spectrum and output sound.

## Architecture

The system uses an FFT Chain Architecture with four main components communicating through SuperCollider's buses:

```
Audio Input → FFT Analysis → Spectral Feature Extraction → Control Buses
                                                               ↓
                                            Drone Synthesis Engine
                                                               ↓
                                                         Audio Output
```

## Components

### 1. Input Stage

- Audio input from external interface (configurable channel)
- Input gain control with level metering
- FFT analysis using `FFT` UGen
  - Window size: 2048 samples (configurable: 1024, 2048, 4096)
  - Hop size: 0.5 (50% overlap)
  - Window type: Hanning

### 2. Spectral Analysis Engine

Extracts features from FFT data and writes to control buses:

**Band Magnitudes**
- Spectrum divided into 8 bands (configurable: 8 or 16)
- Sub-bass through brilliance
- Summed magnitudes per band

**Spectral Features**
- Centroid: weighted average frequency (brightness)
- Spread: variance around centroid (width)
- Flatness: noise vs. tonality ratio

**Processing**
- All values normalized to 0-1 range
- Smoothed with `Lag` UGen (default 100ms, adjustable)
- Written to control buses for drone engine to read

### 3. Drone Synthesis Engine

**Oscillator Layers**

| Layer | Source | Character |
|-------|--------|-----------|
| Sub | Low sine oscillators (1-2 oct below root) | Warmth, weight |
| Pad | Detuned saws through LPF | Main body |
| Shimmer | High sine clusters with slow LFOs | Movement, air |
| Noise | Filtered pink noise | Texture, breath |

**Blend Modes**

| Parameter | Mirror | Complement | Transform |
|-----------|--------|------------|-----------|
| Layer amplitudes | Band magnitudes directly | Inverted band magnitudes | Flatness → noise, centroid → shimmer |
| Filter cutoff | Follows centroid | Inverse of centroid | Spread controls resonance |
| Detune amount | Spread value | Fixed | Centroid shifts detune |
| Root pitch | Fixed | Fixed | Centroid transposes ±1 octave |

**Mix Controls**
- Dry/wet: Raw input vs. drone output
- Drone level: Overall amplitude
- Layer mix: Relative balance of four layers

### 4. GUI Dashboard

```
┌─────────────────────────────────────────────────┐
│  INPUT SPECTRUM    │    OUTPUT SPECTRUM         │
│  [visual display]  │    [visual display]        │
├─────────────────────────────────────────────────┤
│  BLEND MODE: [Mirror] [Complement] [Transform]  │
├────────────────────┬────────────────────────────┤
│  INPUT             │  DRONE                     │
│  • Gain            │  • Root Pitch              │
│  • Level [meter]   │  • Dry/Wet                 │
│                    │  • Drone Level             │
│  ANALYSIS          │  • Sub                     │
│  • Smoothing       │  • Pad                     │
│  • Bands: 8/16     │  • Shimmer                 │
│                    │  • Noise                   │
│                    │  • Output [meter]          │
└────────────────────┴────────────────────────────┘
```

**Spectrum Displays**
- 8 vertical bars representing band magnitudes
- Color-coded: input (blue), output (amber)
- ~30fps update rate

**Controls**
- `EZSlider` for label + slider + value
- Blend mode buttons with active highlight
- Direct `.set` messages to synths

## Configuration

Stored in dictionary, adjustable at runtime:

| Option | Default | Description |
|--------|---------|-------------|
| `inputChannel` | 0 | Audio interface input |
| `fftSize` | 2048 | FFT window size |
| `numBands` | 8 | Spectral analysis bands |
| `smoothing` | 0.1 | Analysis smoothing (seconds) |
| `rootNote` | 36 (C2) | Drone root pitch (MIDI) |

## Startup Sequence

1. Boot server with appropriate settings
2. Allocate FFT buffer and control buses
3. Load SynthDefs (input, analysis, drone layers)
4. Instantiate synths in order: input → analysis → drone
5. Build and display GUI
6. Connect GUI controls to synth parameters

## Error Handling

- Verify server is booted before creating synths
- Validate audio input exists; fail gracefully if not
- GUI close triggers cleanup: free synths, release buses/buffers
- Handle Cmd+period (stop-all) cleanly

## File Structure

```
chroma/
├── Chroma.sc          # Main class file
├── startup.scd        # Boot script
└── README.md          # Usage instructions
```

## Implementation Notes

- All synths communicate via buses (loose coupling)
- Single `Chroma` class with `.start` and `.stop` methods
- GUI built with SuperCollider's Qt framework
- Latency: ~21ms at 48kHz with 2048 FFT (imperceptible for ambient textures)
