# Claude Session Index

**Read this file at the start of each session.**

## Project: Chroma

Spectral-reactive effects processor in SuperCollider. Analyzes input audio spectrum and uses it to shape spectral filtering, granular processing, shimmer reverb, and modulated delay effects with switchable blend modes (Mirror, Complement, Transform).

## Project Files

| File | Description |
|------|-------------|
| `Chroma.sc` | Main class (~525 lines) |
| `startup.scd` | Boot script with server options |
| `run_gui.scd` | Simple GUI launcher |
| `test_synths.scd` | Headless synth test (sclang) |
| `test_integration.sh` | Full integration test suite (bash) |
| `README.md` | User documentation |
| `docs/plans/2026-02-02-chroma-design.md` | Design specification |
| `docs/plans/2026-02-02-chroma-implementation.md` | Implementation plan |

## Current Status

**Implementation complete. All tests passing.**

| Task | Description | Status |
|------|-------------|--------|
| 1 | Project structure + Input stage | Complete |
| 2 | Spectral analysis SynthDef | Complete |
| 3 | Effects SynthDefs | Complete |
| 4 | Blend mode control | Complete |
| 5 | Output mixer | Complete |
| 6 | GUI dashboard | Complete |
| 7 | Startup script + docs | Complete |
| 8 | Integration testing | Complete |

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

# Run with GUI
sclang run_gui.scd

# Or in SuperCollider IDE
Chroma.start;
```

## Guidelines
- Never add Co-Authored-By to commit messages

## Last Updated
2026-02-03
