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
| 2 | Spectral analysis SynthDef | ✅ Complete (reviewed) |
| 3 | Drone layer SynthDefs | ✅ Complete (reviewed) |
| 4 | Blend mode control | ✅ Complete (reviewed) |
| 5 | Output mixer | ✅ Complete (reviewed) |
| 6 | GUI dashboard | ✅ Complete (reviewed) |
| 7 | Startup script + docs | ✅ Complete (reviewed) |
| 8 | Integration testing | ✅ Complete (headless test passed) |

**Status:** Implementation complete. All tests passing.

**Test command:** `sclang test_synths.scd`

## Integration Testing Checklist (Task 8)

To complete the implementation, run these tests manually in SuperCollider:

```supercollider
// 1. Boot and run
Chroma.start;

// 2. Check node tree (should show chroma_input, chroma_analysis, chroma_blend,
//    chroma_sub, chroma_pad, chroma_shimmer, chroma_noise, chroma_output)
s.queryAllNodes;

// 3. Test blend modes
Chroma.instance.setBlendMode(\mirror);
Chroma.instance.setBlendMode(\complement);
Chroma.instance.setBlendMode(\transform);

// 4. Test controls
Chroma.instance.setDryWet(0);  // All dry
Chroma.instance.setDryWet(1);  // All wet
Chroma.instance.setDryWet(0.5);  // Mixed
Chroma.instance.setRootNote(48);  // C3

// 5. Clean shutdown
Chroma.stop;
```

**Checklist:**
- [ ] Server boots without errors
- [ ] All synths appear in node tree
- [ ] Input spectrum visualization responds to audio
- [ ] All three blend modes produce different behaviors
- [ ] Dry/wet smoothly transitions
- [ ] Root pitch changes affect all layers
- [ ] Each layer slider affects its respective layer
- [ ] Closing window stops Chroma cleanly
- [ ] Cmd+. doesn't leave orphan synths
- [ ] Chroma.stop frees all resources

## Guidelines
- Never add Co-Authored-By to commit messages

## Last Updated
2026-02-02
