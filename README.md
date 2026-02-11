# Chroma

A SuperCollider-based audio effects engine providing real-time audio processing with multiple configurable effects, spectral analysis, and flexible parameter control via Open Sound Control (OSC).

## Features

### Audio Effects
- **Multi-mode Filter** - Cutoff, resonance, and amount control
- **Overdrive** - Saturation/distortion with drive, tone, and mix parameters  
- **Bitcrush** - Bit reduction and sample rate manipulation
- **Granular Processor** - Density, size, and scatter controls with freeze capability
- **Shimmer Reverb** - Algorithmic reverb with decay time control
- **Modulated Delay** - Delay time, decay, and modulation parameters
- **Input Freeze** - Adjustable loop length for sustained tones
- **Blend Modes** - Mirror, complement, and transform modes for effect combination

### Control Interface
- **OSC Control** - Full parameter control via OSC messages
- **Headless Operation** - No GUI dependency, perfect for embedded systems
- **Effects Chain Reordering** - Dynamic effects processing order
- **Real-time Parameters** - All audio parameters controllable in real-time

### Audio Processing
- **Sample Rate** - 44.1 kHz (configurable)
- **Buffer Management** - Efficient circular buffers for granular and freeze effects
- **Spectral Analysis** - 8-band spectrum analysis for visualization
- **Cross-platform** - Works on Linux, macOS, and Windows

## Installation

### Prerequisites
- SuperCollider 3.11+ with sc3-plugins
- JACK Audio Connection Kit (Linux) or CoreAudio (macOS)
- OSC client for control (see [chroma-tui](https://github.com/renderorange/chroma-tui))

### Install SuperCollider

**Ubuntu/Debian:**
```bash
sudo apt-get update
sudo apt-get install supercollider sc3-plugins jackd2
```

**macOS (Homebrew):**
```bash
brew install supercollider
```

**Windows:**
Download from [SuperCollider website](https://supercollider.github.io/)

### Setup Chroma

1. Clone this repository:
```bash
git clone https://github.com/renderorange/chroma.git
cd chroma
```

2. Copy Chroma.sc to SuperCollider Extensions:
```bash
# Linux
sudo cp Chroma.sc /usr/share/SuperCollider/Extensions/

# macOS  
sudo cp Chroma.sc ~/Library/Application\ Support/SuperCollider/Extensions/

# Or copy to user extensions directory:
mkdir -p ~/.local/share/SuperCollider/Extensions/
cp Chroma.sc ~/.local/share/SuperCollider/Extensions/
```

3. Verify installation:
```bash
./test_compilation.sh
```

## Usage

### Starting Chroma

**Basic Startup:**
```bash
./run.sh
```

**With Specific Audio Device:**
```bash
./run.sh --device 1
./run.sh --list  # List available devices
```

**Manual SuperCollider Startup:**
```supercollider
sclang startup.scd
```

### OSC Control

Chroma listens for OSC messages on port 57120. All parameters can be controlled via OSC:

**Basic Control Examples:**
```supercollider
// Connect to Chroma
n = NetAddr("127.0.0.1", 57120);

// Control parameters
n.sendMsg("/chroma/gain", 0.8);
n.sendMsg("/chroma/filterEnabled", 1);
n.sendMsg("/chroma/filterCutoff", 2000);
n.sendMsg("/chroma/overdriveDrive", 0.5);
n.sendMsg("/chroma/dryWet", 0.7);
```

**Complete OSC Parameter Reference:**

| Parameter | OSC Path | Range | Description |
|-----------|-----------|--------|-------------|
| Input Gain | `/chroma/gain` | 0.0-2.0 | Master input gain |
| Input Freeze | `/chroma/inputFreeze` | 0/1 | Enable input freeze |
| Freeze Length | `/chroma/inputFreezeLength` | 0.05-0.5 | Freeze loop length (seconds) |
| Filter Enable | `/chroma/filterEnabled` | 0/1 | Enable filter effect |
| Filter Amount | `/chroma/filterAmount` | 0.0-1.0 | Filter effect amount |
| Filter Cutoff | `/chroma/filterCutoff` | 200-8000 | Filter frequency (Hz) |
| Filter Resonance | `/chroma/filterResonance` | 0.0-1.0 | Filter resonance |
| Overdrive Enable | `/chroma/overdriveEnabled` | 0/1 | Enable overdrive |
| Overdrive Drive | `/chroma/overdriveDrive` | 0.0-1.0 | Overdrive intensity |
| Overdrive Tone | `/chroma/overdriveTone` | 0.0-1.0 | Overdrive tone control |
| Overdrive Mix | `/chroma/overdriveMix` | 0.0-1.0 | Overdrive wet/dry mix |
| Granular Enable | `/chroma/granularEnabled` | 0/1 | Enable granular effect |
| Granular Density | `/chroma/granularDensity` | 1-50 | Grains per second |
| Granular Size | `/chroma/granularSize` | 0.05-2.0 | Grain size (seconds) |
| Granular Pitch Scatter | `/chroma/granularPitchScatter` | 0.0-1.0 | Pitch variation |
| Granular Position Scatter | `/chroma/granularPosScatter` | 0.0-1.0 | Position variation |
| Granular Mix | `/chroma/granularMix` | 0.0-1.0 | Granular wet/dry mix |
| Granular Freeze | `/chroma/granularFreeze` | 0/1 | Freeze granular buffer |
| Grain Intensity | `/chroma/grainIntensity` | subtle/pronounced | Granular intensity mode |
| Bitcrush Enable | `/chroma/bitcrushEnabled` | 0/1 | Enable bitcrush |
| Bit Depth | `/chroma/bitDepth` | 4-16 | Bit reduction depth |
| Sample Rate | `/chroma/bitcrushSampleRate` | 1000-44100 | Sample rate reduction |
| Bitcrush Drive | `/chroma/bitcrushDrive` | 0.0-1.0 | Bitcrush intensity |
| Bitcrush Mix | `/chroma/bitcrushMix` | 0.0-1.0 | Bitcrush wet/dry mix |
| Reverb Enable | `/chroma/reverbEnabled` | 0/1 | Enable reverb |
| Reverb Decay | `/chroma/reverbDecayTime` | 0.1-10.0 | Reverb decay (seconds) |
| Reverb Mix | `/chroma/reverbMix` | 0.0-1.0 | Reverb wet/dry mix |
| Delay Enable | `/chroma/delayEnabled` | 0/1 | Enable delay |
| Delay Time | `/chroma/delayTime` | 0.01-2.0 | Delay time (seconds) |
| Delay Decay | `/chroma/delayDecayTime` | 0.1-5.0 | Delay feedback |
| Modulation Rate | `/chroma/modRate` | 0.1-10.0 | Modulation frequency (Hz) |
| Modulation Depth | `/chroma/modDepth` | 0.0-1.0 | Modulation amount |
| Delay Mix | `/chroma/delayMix` | 0.0-1.0 | Delay wet/dry mix |
| Blend Mode | `/chroma/blendMode` | 0-2 | Blend mode (0=mirror, 1=complement, 2=transform) |
| Dry/Wet Mix | `/chroma/dryWet` | 0.0-1.0 | Overall effects mix |

**Effects Ordering:**
```supercollider
// Set effects processing order
n.sendMsg("/chroma/effectsOrder", "filter", "overdrive", "granular", "bitcrush", "reverb", "delay");

// Get current effects order
n.sendMsg("/chroma/getEffectsOrder");
```

## Testing

### Run Test Suite

**All Tests:**
```bash
./test_compilation.sh          # Syntax and compilation
./test_headless.sh            # Headless functionality
./test_synths.scd             # SynthDef testing (run with sclang)
./functional_full_workflow.sh   # Complete workflow test
```

**Individual Tests:**
```bash
./test_syntax.sh              # Syntax validation
./test_effects_order_osc.scd  # OSC effects order testing
./test_pronounced_mode.scd    # Granular pronounced mode
```

### Manual Testing

Start SuperCollider and test basic functionality:

```supercollider
// Start Chroma
Chroma.start;

// Test basic parameters
Chroma.setGain(0.8);
Chroma.setFilterEnabled(true);
Chroma.setFilterCutoff(1000);

// Test effects
Chroma.setOverdriveEnabled(true);
Chroma.setGranularEnabled(true);
Chroma.setReverbEnabled(true);

// Test freeze functionality
Chroma.setInputFreeze(true);
Chroma.setGranularFreeze(true);

// Stop Chroma
Chroma.stop;
```

## Audio Routing

Chroma processes audio from the default input device and outputs to the default output device. With JACK, you can route audio flexibly:

```bash
# List JACK ports
jack_lsp -c

# Connect inputs
jack_connect system:capture_1 Chroma:in
jack_connect system:capture_2 Chroma:in

# Connect outputs  
jack_connect Chroma:out system:playback_1
jack_connect Chroma:out system:playback_2
```

## TUI Control

For a complete terminal interface see [chroma-tui](https://github.com/renderorange/chroma-tui).

## Architecture

Chroma follows a modular architecture:

```
Audio Input → Analysis → Effects Chain → Audio Output
                 ↓
            Parameter Control (OSC)
                 ↓
            Real-time Processing
```

**Effects Chain Order (configurable):**
1. Input Analysis & Freeze
2. Multi-mode Filter
3. Overdrive/Saturation
4. Granular Processing
5. Bitcrush
6. Shimmer Reverb
7. Modulated Delay
8. Output Mixer

## Development

### Project Structure
```
chroma/
├── Chroma.sc                 # Main SuperCollider class
├── startup.scd               # SuperCollider startup script
├── run.sh                   # Launch script with JACK setup
├── test_*.sh                # Test scripts
├── test_*.scd               # SuperCollider test files
├── functional_full_workflow.sh # Integration tests
└── README.md               # This file
```

### Modifying Chroma

**Adding New Effects:**
1. Create SynthDef in `Chroma.sc`
2. Add parameters to appropriate parameter dictionary
3. Add OSC responders in `setupOSC` method
4. Update `createSynths` and `connectSynths` methods
5. Add test cases

**Modifying OSC Interface:**
- OSC responders are defined in the `setupOSC` method
- Each parameter has a corresponding OSCdef
- All OSC messages use the `/chroma/` namespace

## Troubleshooting

### Common Issues

**SuperCollider Can't Find Chroma Class:**
- Ensure `Chroma.sc` is in the Extensions directory
- Restart SuperCollider after copying files
- Check Extensions path in SuperCollider preferences

**Audio Not Working:**
- Verify JACK is running: `ps aux | grep jackd`
- Check audio device permissions: `sudo usermod -a -G audio $USER`
- Try specific device: `./run.sh --device 0`

**OSC Control Not Responding:**
- Verify SuperCollider server is running: `Server.local.boot`
- Check port 57120 is available: `netstat -an | grep 57120`
- Test OSC connection with simple message

**Performance Issues:**
- Reduce granular density for CPU relief
- Check JACK buffer size settings
- Monitor CPU usage in SuperCollider: `s.avgCPU`

### Getting Help

- **Issues**: [GitHub Issues](https://github.com/renderorange/chroma/issues)
- **TUI Project**: [chroma-tui](https://github.com/renderorange/chroma-tui)
- **SuperCollider**: [SuperCollider Documentation](https://doc.sccode.org/)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
