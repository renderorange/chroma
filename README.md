# Chroma

Spectral-reactive drone synthesizer for SuperCollider.

## Overview

Chroma analyzes the spectral content of incoming audio and uses that analysis to shape evolving drone and pad textures. Three blend modes define different relationships between input spectrum and output sound.

## Requirements

- SuperCollider 3.10+
- Audio interface with input

## Installation

1. Copy `Chroma.sc` to your SuperCollider Extensions folder:
   - macOS: `~/Library/Application Support/SuperCollider/Extensions/`
   - Linux: `~/.local/share/SuperCollider/Extensions/`
   - Windows: `%LOCALAPPDATA%\SuperCollider\Extensions\`

2. Recompile class library: `Language > Recompile Class Library` or Cmd+Shift+L

## Usage

### Quick Start

```supercollider
Chroma.start;  // Launch with default settings
Chroma.stop;   // Stop and cleanup
```

### Or run the startup script

```supercollider
"path/to/startup.scd".load;
```

## Controls

### Blend Modes

- **Mirror**: Input spectrum directly shapes drone (loud bass = loud sub layer)
- **Complement**: Inverted relationship (loud bass = quiet sub layer)
- **Transform**: Spectral features map creatively (brightness shifts pitch)

### Parameters

| Control | Range | Description |
|---------|-------|-------------|
| Gain | 0-2 | Input amplification |
| Smoothing | 0.01-0.5s | Analysis response time |
| Root | C1-C4 (MIDI 24-60) | Drone root pitch |
| Dry/Wet | 0-1 | Input vs. drone balance |
| Drone | 0-1 | Overall drone level |
| Sub/Pad/Shimmer/Noise | 0-1 | Layer mix |

## Configuration

```supercollider
// Access running instance
Chroma.instance.setBlendMode(\transform);
Chroma.instance.setRootNote(48);  // C3
Chroma.instance.setDryWet(0.7);
```

## Architecture

```
Audio Input -> FFT Analysis -> Feature Extraction -> Control Buses
                                                          |
                                       Drone Layers (Sub/Pad/Shimmer/Noise)
                                                          |
                                                    Output Mixer
```

## License and Copyright

`Chroma` is Copyright (c) 2026 Blaine Motsinger under the MIT license.
