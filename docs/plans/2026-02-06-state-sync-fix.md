# TUI-Server State Synchronization Fix

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ensure TUI controls accurately reflect server state by having the server send state updates after processing parameter changes.

**Architecture:** The SuperCollider server will call `sendState()` after each OSC parameter handler updates internal state. The TUI's existing `pendingChanges` mechanism will prevent rubber-banding during rapid user adjustments, while ensuring eventual consistency with server state.

**Tech Stack:** SuperCollider (Chroma.sc), Go (chroma-tui)

---

## Root Cause

The SuperCollider server only sends state to the TUI in two scenarios:
1. On `/chroma/sync` request (once at startup)
2. Spectrum data via `/chroma/spectrum` (continuous, but only spectrum)

When parameters are changed via OSC (e.g., `/chroma/gain`), the server updates internally but never confirms back to the TUI. This causes state drift.

---

### Task 1: Add State Broadcast After Parameter Changes

**Files:**
- Modify: `Chroma.sc:741-791` (setupOSC method)

**Step 1: Modify each OSC handler to call sendState after processing**

In `setupOSC`, the `replyAddr` variable is already defined. Each handler needs to call `this.sendState(replyAddr)` after processing the parameter change.

Update each handler from this pattern:
```supercollider
OSCdef(\chromaGain, { |msg| this.setInputGain(msg[1]) }, '/chroma/gain');
```

To this pattern:
```supercollider
OSCdef(\chromaGain, { |msg|
    this.setInputGain(msg[1]);
    this.sendState(replyAddr);
}, '/chroma/gain');
```

Apply this change to ALL parameter handlers:
- `\chromaGain`
- `\chromaInputFreeze`
- `\chromaInputFreezeLength`
- `\chromaFilterAmount`
- `\chromaFilterCutoff`
- `\chromaFilterResonance`
- `\chromaOverdriveDrive`
- `\chromaOverdriveTone`
- `\chromaOverdriveMix`
- `\chromaGranularDensity`
- `\chromaGranularSize`
- `\chromaGranularPitchScatter`
- `\chromaGranularPosScatter`
- `\chromaGranularMix`
- `\chromaGranularFreeze`
- `\chromaGrainIntensity`
- `\chromaReverbDelayBlend`
- `\chromaDecayTime`
- `\chromaShimmerPitch`
- `\chromaDelayTime`
- `\chromaModRate`
- `\chromaModDepth`
- `\chromaReverbDelayMix`
- `\chromaBlendMode`
- `\chromaDryWet`

**Step 2: Verify the changes compile**

In SuperCollider, recompile the class library (Cmd+Shift+L or Language > Recompile Class Library).

Expected: No compilation errors.

**Step 3: Manual test**

1. Start Chroma: `c = Chroma.new`
2. Start TUI: `./chroma-tui`
3. Adjust a control in the TUI (e.g., gain)
4. Observe that the TUI shows the server's authoritative value after the 2-second pending window

**Step 4: Commit**

```bash
git add Chroma.sc
git commit -m "fix: send state updates after parameter changes

The server now broadcasts full state after each parameter change,
ensuring TUI controls stay synchronized with server values.
The TUI's pendingChanges mechanism prevents rubber-banding during
rapid adjustments."
```

---

### Task 2: Reduce pendingChanges timeout (optional optimization)

**Files:**
- Modify: `chroma-tui/tui/model.go:236-244`

**Context:** With state now flowing from server, the 2-second timeout may be too long. Consider reducing to 500ms for faster sync.

**Step 1: Update the timeout**

Change from:
```go
func (m *Model) cleanupStalePendingChanges() {
	// Remove pending changes older than 2 seconds
	cutoff := time.Now().Add(-2 * time.Second)
```

To:
```go
func (m *Model) cleanupStalePendingChanges() {
	// Remove pending changes older than 500ms
	cutoff := time.Now().Add(-500 * time.Millisecond)
```

**Step 2: Run existing tests**

```bash
cd chroma-tui && go test ./...
```

Expected: All tests pass.

**Step 3: Manual test**

1. Restart TUI
2. Adjust controls rapidly - should feel responsive without rubber-banding
3. After ~500ms pause, state should sync with server

**Step 4: Commit**

```bash
git add chroma-tui/tui/model.go
git commit -m "feat: reduce pending change timeout to 500ms

With server state updates now flowing, 500ms provides faster
eventual consistency while still preventing rubber-banding
during rapid adjustments."
```

---

## Summary

Task 1 is the critical fix. Task 2 is an optimization that can be adjusted based on user feel.

After implementation:
1. TUI sends parameter change to server
2. Server processes change and immediately sends full state back
3. TUI receives state within ~10-50ms
4. If within pendingChanges window, TUI keeps its local value (prevents rubber-banding)
5. After window expires, TUI accepts server's authoritative state

This ensures eventual consistency with responsive user interaction.
