# Chroma Effects Pedal Redesign

**Status:** IMPLEMENTED (2026-02-03)

## Overview

Transform Chroma from a spectral-reactive drone synthesizer into a spectral-reactive effects processor. Instead of generating drone sounds based on input analysis, the system processes the input audio directly through spectral filtering, granular processing, and reverb/delay - all modulated by spectral analysis.

## Architecture

### Signal Flow

```
Audio Input → Input Stage → Spectral Filter → Parallel Split
                                                    ↓
                                    ┌───────────────┴───────────────┐
                                    ↓                               ↓
                              Granular Engine                 Reverb/Delay
                                    ↓                               ↓
                                    └───────────────┬───────────────┘
                                                    ↓
                                              Output Mixer → Audio Output
```

### Analysis (unchanged)

Spectral analysis runs in parallel, extracting:
- 8 band magnitudes (sub-bass through brilliance)
- Centroid (brightness)
- Spread (spectral width)
- Flatness (noise vs. tonality)

These control effect parameters via blend modes.

### Blend Modes

| Mode | Behavior |
|------|----------|
| Mirror | Input characteristics amplified. Bright input → brighter filter, denser grains, more shimmer. |
| Complement | Input characteristics inverted. Bright input → darker filter, sparser grains, more delay warmth. |
| Transform | Cross-mapped. Flatness → grain density, centroid → filter cutoff, spread → reverb/delay blend. |

### Synth Execution Order

1. Input (captures audio, writes to bus)
2. Analysis (reads input bus, writes control buses)
3. Blend Control (reads analysis, writes effect parameter buses)
4. Spectral Filter (processes audio)
5. Granular + Reverb/Delay (parallel, both read filtered signal)
6. Output Mixer (combines and outputs)

## Components

### 1. Spectral Filter

8 parallel bandpass filters matching analysis bands. Each band's amplitude controlled by corresponding spectral band magnitude.

**Parameters:**

| Parameter | Range | Default | Description |
|-----------|-------|---------|-------------|
| `filterAmount` | 0-1 | 0.5 | How much spectral analysis affects filter |
| `baseCutoff` | 200-8000 Hz | 2000 | Center frequency for overall brightness |
| `resonance` | 0-1 | 0.3 | Q factor for all bands |

**Blend Mode Behavior:**

- **Mirror:** Band magnitudes boost corresponding filter bands. Emphasizes existing spectral peaks.
- **Complement:** Band magnitudes cut corresponding filter bands. Creates spectral "negative."
- **Transform:** Centroid shifts cutoff center. Spread controls resonance. Flatness controls filter amount.

### 2. Granular Processing

Live buffer granular synthesis with freeze capability.

**Buffer Setup:**
- 2-second mono recording buffer (continuously overwritten)
- Separate freeze buffer (copied on freeze trigger)
- When frozen, grains read from freeze buffer; otherwise from live buffer

**Parameters:**

| Parameter | Range | Default | Description |
|-----------|-------|---------|-------------|
| `grainDensity` | 1-50 Hz | 10 | Grains triggered per second |
| `grainSize` | 0.01-0.5 s | 0.1 | Duration of each grain |
| `grainIntensity` | subtle/pronounced | subtle | Overall granular effect strength |
| `pitchScatter` | 0-1 | 0.1 | Random pitch variation (±1 octave at max) |
| `posScatter` | 0-1 | 0.2 | How far back in buffer to read |
| `freeze` | 0/1 | 0 | Toggle freeze mode |
| `granularMix` | 0-1 | 0.3 | Dry/wet for granular stage |

**Blend Mode Behavior:**

- **Mirror:** High spectral energy → higher density, smaller grains.
- **Complement:** High spectral energy → lower density, larger grains.
- **Transform:** Flatness → density. Centroid → pitch scatter. Spread → position scatter.

### 3. Reverb/Delay

Crossfadeable shimmer reverb and modulated delay.

**Shimmer Reverb:**
- Feedback loop with pitch shifting
- Diffusion network for smooth decay
- High-frequency damping

**Modulated Delay:**
- Delay line with LFO-modulated read position
- Feedback with filtering
- Warm, analog-style character

**Parameters:**

| Parameter | Range | Default | Description |
|-----------|-------|---------|-------------|
| `reverbDelayBlend` | 0-1 | 0.5 | 0 = all reverb, 1 = all delay |
| `decayTime` | 0.5-10 s | 3 | Reverb decay / delay feedback time |
| `shimmerPitch` | 0, 5, 7, 12 | 12 | Pitch shift interval (semitones) |
| `delayTime` | 0.1-1 s | 0.3 | Base delay time |
| `modRate` | 0.1-5 Hz | 0.5 | Delay modulation speed |
| `modDepth` | 0-1 | 0.3 | Delay modulation amount |
| `reverbDelayMix` | 0-1 | 0.3 | Dry/wet for this stage |

**Blend Mode Behavior:**

- **Mirror:** High centroid → more shimmer. High spread → longer decay.
- **Complement:** High centroid → more delay. High spread → shorter decay.
- **Transform:** Flatness → reverb/delay blend. Centroid → shimmer pitch. Spread → mod depth.

## GUI Layout

```
┌─────────────────────────────────────────────────────────────┐
│                         CHROMA                              │
├─────────────────────────────────────────────────────────────┤
│  INPUT SPECTRUM              BLEND MODE                     │
│  [████ ██ █ ███]    [Mirror] [Complement] [Transform]       │
├───────────────────────┬─────────────────────────────────────┤
│  INPUT                │  SPECTRAL FILTER                    │
│  • Gain [----●--]     │  • Amount    [----●--]              │
│                       │  • Cutoff    [----●--]              │
│                       │  • Resonance [----●--]              │
├───────────────────────┼─────────────────────────────────────┤
│  GRANULAR             │  REVERB / DELAY                     │
│  • Density  [----●--] │  • Rev↔Dly   [----●--]              │
│  • Size     [----●--] │  • Decay     [----●--]              │
│  • Pitch    [----●--] │  • Shimmer   [--●----] (5,7,12)     │
│  • Position [----●--] │  • Delay Time[----●--]              │
│  • Mix      [----●--] │  • Mod Rate  [----●--]              │
│  [  FREEZE  ]         │  • Mod Depth [----●--]              │
│                       │  • Mix       [----●--]              │
├───────────────────────┴─────────────────────────────────────┤
│  OUTPUT                                                     │
│  • Dry/Wet [----●--]                                        │
└─────────────────────────────────────────────────────────────┘
```

## Implementation Changes

### Files to Modify

- `Chroma.sc` - Complete rewrite of synth architecture

### What Gets Removed

- Drone layer SynthDefs: `chroma_sub`, `chroma_pad`, `chroma_shimmer`, `chroma_noise`
- Drone-related buses: `subAmp`, `padAmp`, `shimmerAmp`, `noiseAmp`, `padCutoff`, `padDetune`, `rootFreq`
- Drone-related instance variables: `layerAmps`, `droneLevel`
- GUI drone controls section

### What Gets Added

| SynthDef | Purpose |
|----------|---------|
| `chroma_filter` | 8-band spectral filter with blend-controlled gains |
| `chroma_granular` | GrainBuf-based processor with freeze |
| `chroma_shimmer_reverb` | Pitch-shifted feedback reverb |
| `chroma_mod_delay` | Chorus-style modulated delay |
| `chroma_effect_mix` | Parallel mix of granular + reverb/delay |

### New Buses

- `filterGains` (8 channels) - per-band filter amplitudes
- `filteredAudio` - output of filter stage
- `granularAudio` - output of granular
- `reverbDelayAudio` - output of reverb/delay

### New Buffers

- `grainBuffer` - 2-second recording buffer
- `freezeBuffer` - 2-second freeze buffer

### New Instance Variables

- `filterParams` - dictionary for filter settings
- `granularParams` - dictionary for granular settings
- `reverbDelayParams` - dictionary for reverb/delay settings
- `frozen` - freeze state boolean

### Test Updates

Update `test_synths.scd` to test new SynthDefs instead of drone layers.
