# Agents Session Index

**Read this file at the start of each session.**

## Project: Chroma

Spectral-reactive effects processor in SuperCollider. Analyzes input audio spectrum and uses it to shape spectral filtering, overdrive, granular processing, shimmer reverb, and modulated delay effects with switchable blend modes (Mirror, Complement, Transform).

## Project Files

| File | Description |
|------|-------------|
| `Chroma.sc` | Main class (headless, ~730 lines) |
| `run.sh` | Launcher with audio device selection |
| `startup.scd` | Boot script with server options |
| `test_synths.scd` | Headless synth test (sclang) |
| `test_integration.sh` | Integration test suite (bash) |
| `README.md` | User documentation |
| `docs/plans/2026-02-03-chroma-effects-design.md` | Effects pedal design specification |
| `docs/plans/2026-02-03-chroma-effects-implementation.md` | Effects implementation plan |
| `docs/plans/2026-02-03-audio-device-selection-design.md` | Audio device selection design |
| `docs/plans/2026-02-03-input-freeze-design.md` | Input freeze design |
| `docs/plans/2026-02-03-tui-design.md` | TUI and MIDI control design |
| `docs/plans/2026-02-03-tui-implementation.md` | TUI implementation plan |
| `docs/plans/2026-02-03-tui-refactor-implementation.md` | TUI refactor plan (full-width, overdrive) |
| `chroma-tui/` | Go TUI application with MIDI support |

## Current Status

**Spectrum visualizer implementation COMPLETE and tested**

See `docs/plans/2026-02-06-spectrum-visualizer-status.md` for detailed status.

### Recent Work: Spectrum Visualizer (Tasks 1-6) - ✅ COMPLETE
| Task | Description | Status |
|------|-------------|--------|
| 1 | Expose spectrum data from SuperCollider | Complete |
| 2 | Handle OSC spectrum messages in Python | Complete |
| 3 | Update TUI model with spectrum data | Complete |
| 4 | Implement spectrum visualizer component | Complete |
| 5 | Integrate visualizer into TUI | Complete |
| 6a | Update README.md with spectrum visualizer feature | Complete |
| 6b | Update visualizer design document status | Complete |
| 6c | Run integration tests | Complete |

### Resolved Issue
- **File**: `Chroma.sc` at line 731
- **Error**: Missing semicolon after if statement block
- **Resolution**: Fixed syntax error, all tests passing

### Previous Implementation (Tasks 1-16)
All core Chroma effects and TUI features are complete.

## Running Tests

```bash
# Run integration tests
./test_integration.sh

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
2026-02-06 (Spectrum visualizer implementation complete and tested)
