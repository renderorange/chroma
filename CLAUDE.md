# Claude Session Index

**Read this file at the start of each session.**

## Project: Chroma

Spectral-reactive effects processor in SuperCollider. Analyzes input audio spectrum and uses it to shape spectral filtering, granular processing, shimmer reverb, and modulated delay effects with switchable blend modes (Mirror, Complement, Transform).

## Project Files

| File | Description |
|------|-------------|
| `Chroma.sc` | Main class (~525 lines) |
| `run.sh` | Launcher with audio device selection |
| `startup.scd` | Boot script with server options |
| `run_gui.scd` | Simple GUI launcher |
| `test_synths.scd` | Headless synth test (sclang) |
| `test_integration.sh` | Full integration test suite (bash) |
| `README.md` | User documentation |
| `docs/plans/2026-02-03-chroma-effects-design.md` | Effects pedal design specification |
| `docs/plans/2026-02-03-chroma-effects-implementation.md` | Effects implementation plan |
| `docs/plans/2026-02-03-audio-device-selection-design.md` | Audio device selection design |
| `docs/plans/2026-02-03-input-freeze-design.md` | Input freeze design |

## Current Status

**Implementation complete. All tests passing.**

| Task | Description | Status |
|------|-------------|--------|
| 1 | Remove drone layer code | Complete |
| 2 | Add effect buses and buffers | Complete |
| 3 | Spectral filter SynthDef | Complete |
| 4 | Blend control for effects | Complete |
| 5 | Granular SynthDef with freeze | Complete |
| 6 | Shimmer reverb SynthDef | Complete |
| 7 | Modulated delay SynthDef | Complete |
| 8 | Output mixer | Complete |
| 9 | Wire up synth creation | Complete |
| 10 | Rebuild GUI | Complete |
| 11 | Update tests | Complete |
| 12 | Update documentation | Complete |
| 13 | Integration testing | Complete |

## Running Tests

```bash
# Full integration test (headless + GUI)
./test_integration.sh

# Headless only (faster)
./test_integration.sh headless

# GUI only
./test_integration.sh gui

# Or run SuperCollider tests directly
sclang test_synths.scd
```

## Quick Start

```bash
# Install class
cp Chroma.sc ~/.local/share/SuperCollider/Extensions/

# List audio devices and select one
./run.sh --list
./run.sh -d 1    # Use card 1 (saved to ~/.config/chroma/config)
./run.sh         # Use saved device

# Or in SuperCollider IDE
Chroma.start;
```

## Guidelines
- Never add Co-Authored-By to commit messages
- Update all documentation (README.md, CLAUDE.md, design docs) after any code changes

## Last Updated
2026-02-03
