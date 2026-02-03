# Audio Device Selection Design

## Problem

Chroma currently uses JACK which defaults to the built-in audio device (hw:0). Users need to select their preferred audio device (e.g., USB audio interface) without hardcoding device names.

## Solution

A launcher script with config file persistence:

1. **`run.sh`** - Launcher script that:
   - Accepts `--device "name"` or `-d "name"` argument
   - Accepts `--list` or `-l` to show available audio devices
   - Reads from `~/.config/chroma/config` when no argument given
   - Saves device to config when specified via argument
   - Passes device to SuperCollider via environment variable

2. **`~/.config/chroma/config`** - Simple key=value format:
   ```
   device=UMC202HD 192k: USB Audio (hw:1,0)
   ```

3. **`startup.scd`** - Modified to:
   - Read `CHROMA_DEVICE` environment variable
   - Disable JACK, use PortAudio
   - Configure server with specified device (or system default if not set)

## Usage

```bash
./run.sh --list                                        # show available devices
./run.sh --device "UMC202HD 192k: USB Audio (hw:1,0)"  # use and save
./run.sh                                               # use saved device
```

## Implementation Tasks

1. Create `run.sh` launcher script
2. Update `startup.scd` to read environment variable and configure PortAudio
3. Update README.md with new usage instructions
