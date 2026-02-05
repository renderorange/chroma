# Enhanced Granular Effect with User-Selectable Intensity Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add user-selectable grain intensity mode that expands granular effect ranges by 3x while preserving spectral modulation relationships.

**Architecture:** Add grainIntensity parameter to Chroma class, modify blend SynthDef to apply intensity multiplier to granular parameters, integrate with TUI for intensity toggle control.

**Tech Stack:** SuperCollider SynthDefs, Go TUI application, OSC communication between TUI and SuperCollider.

---

### Task 1: Add grainIntensity Parameter to Chroma Class

**Files:**
- Modify: `Chroma.sc` (add parameter around line 950 where other params are)

**Step 1: Add grainIntensity instance variable**

Add to Chroma class initialization:
```supercollider
var <>grainIntensity = \subtle;
```

**Step 2: Add setter method**

```supercollider
setGrainIntensity { |mode|
    grainIntensity = mode;
    if(synths[\blend].notNil) { 
        synths[\blend].set(\grainIntensityMultiplier, mode == \pronounced ? 3.0 : 1.0);
    }
}
```

**Step 3: Update documentation**

Add to README.md parameters table:
| Grain Intensity | subtle/pronounced | Overall granular effect strength |

**Step 4: Commit**

```bash
git add Chroma.sc README.md
git commit -m "feat: add grainIntensity parameter to Chroma class"
```

### Task 2: Modify Blend SynthDef to Support Intensity Multiplier

**Files:**
- Modify: `Chroma.sc` (loadBlendSynthDef method around line 250)

**Step 1: Add grainIntensityMultiplier argument**

Modify SynthDef arguments:
```supercollider
SynthDef(\chroma_blend, { |inBus, outBus, 
    filterGainsBus, overdriveCtrlBus, granularCtrlBus, reverbDelayCtrlBus,
    baseGrainDensity=10, baseGrainSize=0.1,
    grainIntensityMultiplier=1.0, // New parameter
    // ... rest of existing arguments
```

**Step 2: Apply intensity multiplier to granular parameters**

Modify grain density and size calculations (around lines 295-310):
```supercollider
grainDensity = Select.kr(mode, [
    baseGrainDensity * grainIntensityMultiplier,
    bands.sum.linlin(0, 4, baseGrainDensity * 0.5 * grainIntensityMultiplier, baseGrainDensity * 2 * grainIntensityMultiplier),
    bands.sum.linlin(0, 4, baseGrainDensity * 2 * grainIntensityMultiplier, baseGrainDensity * 0.5 * grainIntensityMultiplier),
    flatness.linlin(0, 1, baseGrainDensity * 0.3 * grainIntensityMultiplier, baseGrainDensity * 3 * grainIntensityMultiplier)
]);

grainSize = Select.kr(mode, [
    baseGrainSize * grainIntensityMultiplier,
    bands.sum.linlin(0, 4, baseGrainSize * 2 * grainIntensityMultiplier, baseGrainSize * 0.5 * grainIntensityMultiplier),
    bands.sum.linlin(0, 4, baseGrainSize * 0.5 * grainIntensityMultiplier, baseGrainSize * 2 * grainIntensityMultiplier),
    spread.linlin(0, 1, baseGrainSize * 0.5 * grainIntensityMultiplier, baseGrainSize * 2 * grainIntensityMultiplier)
]);
```

**Step 3: Update synth creation call**

Modify blend synth creation (around line 635):
```supercollider
synths[\blend] = Synth(\chroma_blend, [
    // ... existing parameters ...
    \grainIntensityMultiplier, grainIntensity == \pronounced ? 3.0 : 1.0,
    // ... rest of existing parameters
```

**Step 4: Commit**

```bash
git add Chroma.sc
git commit -m "feat: add intensity multiplier to blend SynthDef"
```

### Task 3: Update Granular SynthDef Mix Range

**Files:**
- Modify: `Chroma.sc` (loadGranularSynthDef method around line 433)

**Step 1: Check current mix limit**

Verify current mix parameter is handled correctly (should accept values up to 0.9 for pronounced mode).

**Step 2: Commit if changes needed**

```bash
git add Chroma.sc
git commit -m "feat: ensure granular mix supports pronounced mode range"
```

### Task 4: Add OSC Message Handler for Grain Intensity

**Files:**
- Modify: `Chroma.sc` (add to OSC handling section)

**Step 1: Add OSC handler**

```supercollider
oscFunc { |msg, time, addr, recvPort|
    var cmd = msg[1].asString;
    var val = msg[2];
    
    switch(cmd,
        \grainIntensity, { this.setGrainIntensity(val.asSymbol) },
        // ... existing handlers
    );
}
```

**Step 2: Commit**

```bash
git add Chroma.sc
git commit -m "feat: add OSC handler for grain intensity"
```

### Task 5: Update TUI with Grain Intensity Control

**Files:**
- Modify: `chroma-tui/main.go` or appropriate TUI file
- Modify: `chroma-tui/osc.go` for OSC communication

**Step 1: Add intensity field to UI state**

Add to TUI state struct:
```go
GrainIntensity string // "subtle" or "pronounced"
```

**Step 2: Add OSC message function**

```go
func SetGrainIntensity(intensity string) error {
    return oscClient.Send("/chroma", "grainIntensity", intensity)
}
```

**Step 3: Add UI control for intensity toggle**

Add to granular section layout with key binding (e.g., 'i' key).

**Step 4: Update TUI build and test**

```bash
cd chroma-tui
go build
./chroma-tui --help
```

**Step 5: Commit**

```bash
git add chroma-tui/main.go chroma-tui/osc.go
git commit -m "feat: add grain intensity control to TUI"
```

### Task 6: Update Integration Tests

**Files:**
- Modify: `test_integration.sh`
- Modify: `test_synths.scd` if needed

**Step 1: Add grain intensity test**

Add to integration test:
```bash
echo "Testing grain intensity..."
# Test subtle mode
sclang -c "Chroma.start; Chroma.instance.setGrainIntensity(\subtle); 1.wait; Chroma.stop;"
# Test pronounced mode  
sclang -c "Chroma.start; Chroma.instance.setGrainIntensity(\pronounced); 1.wait; Chroma.stop;"
```

**Step 2: Run tests**

```bash
./test_integration.sh
```

**Step 3: Commit**

```bash
git add test_integration.sh test_synths.scd
git commit -m "test: add grain intensity to integration tests"
```

### Task 7: Update Documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/plans/2026-02-03-chroma-effects-design.md`

**Step 1: Update README parameters table**

Add grain intensity to the controls table with description and range.

**Step 2: Update design documentation**

Add grain intensity feature to the effects design document.

**Step 3: Commit**

```bash
git add README.md docs/plans/2026-02-03-chroma-effects-design.md
git commit -m "docs: update documentation for grain intensity feature"
```

### Task 8: Final Testing and Verification

**Files:**
- Test: All files

**Step 1: Run full integration test suite**

```bash
./test_integration.sh
```

**Step 2: Test TUI integration manually**

```bash
cd chroma-tui
go build
./chroma-tui
# Test intensity toggle with 'i' key
```

**Step 3: Test SuperCollider directly**

```bash
sclang
Chroma.start;
Chroma.instance.setGrainIntensity(\pronounced);
Chroma.instance.setDryWet(0.5);
// Test audio output
Chroma.stop;
```

**Step 4: Final commit if needed**

```bash
git add .
git commit -m "feat: complete enhanced granular effect with user-selectable intensity"
```

---

## Testing Strategy

- **Unit Tests**: Test individual parameter changes in SuperCollider
- **Integration Tests**: Test OSC communication between TUI and SuperCollider  
- **Manual Tests**: Verify audio behavior in both subtle and pronounced modes
- **UI Tests**: Ensure TUI intensity toggle works correctly

## Success Criteria

1. Grain intensity can be set via OSC and TUI
2. Pronounced mode provides 3x stronger granular effect
3. Spectral modulation relationships preserved
4. TUI correctly displays current intensity mode
5. All existing tests pass
6. Documentation accurately describes new feature