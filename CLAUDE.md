# Claude Session Index

**Read this file at the start of each session.**

## Project: Chroma

Spectral-reactive drone synthesizer in SuperCollider. Analyzes input audio spectrum and uses it to shape evolving drone/pad textures with switchable blend modes (Mirror, Complement, Transform).

## Project Files to Review
- `docs/plans/2026-02-02-chroma-design.md` - Full design specification
- `docs/plans/2026-02-02-chroma-implementation.md` - Implementation plan (8 tasks)

## Current Status
**Implementing using subagent-driven development.**

| Task | Description | Status |
|------|-------------|--------|
| 1 | Project structure + Input stage | ✅ Complete |
| 2 | Spectral analysis SynthDef | 🔄 Implementer done, needs spec review |
| 3 | Drone layer SynthDefs | Pending (blocked by 1) |
| 4 | Blend mode control | Pending (blocked by 2,3) |
| 5 | Output mixer | Pending (blocked by 4) |
| 6 | GUI dashboard | Pending (blocked by 5) |
| 7 | Startup script + docs | Pending (blocked by 6) |
| 8 | Integration testing | Pending (blocked by 7) |

**Resume point:** Dispatch spec compliance reviewer for Task 2, then code quality review if passes.

## Guidelines
- Never add Co-Authored-By to commit messages

## Last Updated
2026-02-02
