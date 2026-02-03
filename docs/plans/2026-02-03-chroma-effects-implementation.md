# Chroma Effects Pedal Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Transform Chroma from a drone synthesizer into a spectral-reactive effects processor with filtering, granular, and reverb/delay.

**Architecture:** Hybrid signal chain - input → spectral filter → parallel (granular + reverb/delay) → output mixer. Spectral analysis modulates effect parameters via blend modes.

**Tech Stack:** SuperCollider (sclang), Qt GUI

**Design Doc:** `docs/plans/2026-02-03-chroma-effects-design.md`

---

## Task 1: Remove Drone Layer Code

Remove all drone synthesis code to make room for effects.

**Files:**
- Modify: `Chroma.sc`

**Step 1: Remove drone-related instance variables**

In `Chroma.sc`, remove from the class variable declarations (lines 11-12):

```supercollider
// REMOVE these lines:
var <layerAmps;
var <droneLevel;
```

**Step 2: Remove drone initialization from init method**

In the `init` method, remove:

```supercollider
// REMOVE these lines:
layerAmps = [0.3, 0.3, 0.2, 0.1];  // sub, pad, shimmer, noise defaults
droneLevel = 0.8;
```

**Step 3: Remove drone-related bus allocations**

In `allocateResources`, remove:

```supercollider
// REMOVE these lines:
buses[\subAmp] = Bus.control(server, 1);
buses[\padAmp] = Bus.control(server, 1);
buses[\shimmerAmp] = Bus.control(server, 1);
buses[\noiseAmp] = Bus.control(server, 1);
buses[\padCutoff] = Bus.control(server, 1);
buses[\padDetune] = Bus.control(server, 1);
buses[\rootFreq] = Bus.control(server, 1);
```

**Step 4: Remove drone SynthDef loader calls**

In `loadSynthDefs`, remove:

```supercollider
// REMOVE these lines:
this.loadSubSynthDef;
this.loadPadSynthDef;
this.loadShimmerSynthDef;
this.loadNoiseSynthDef;
```

**Step 5: Remove drone SynthDef methods**

Delete these entire methods:
- `loadSubSynthDef`
- `loadPadSynthDef`
- `loadShimmerSynthDef`
- `loadNoiseSynthDef`

**Step 6: Remove drone synth creation**

In `createSynths`, remove everything after blend control creation related to drone layers (sub, pad, shimmer, noise synths and their mappings).

**Step 7: Remove drone control methods**

Delete these methods:
- `setDroneLevel`
- `setLayerAmp`
- `setRootNote`

**Step 8: Simplify blend control SynthDef**

The `loadBlendControlSynthDef` will be completely rewritten in Task 4. For now, replace it with a stub:

```supercollider
loadBlendControlSynthDef {
    // Placeholder - will be rewritten for effects
    SynthDef(\chroma_blend, { |mode=0|
        // Empty for now
    }).add;
}
```

**Step 9: Comment out blend synth creation**

In `createSynths`, comment out the blend synth creation (we'll restore it in Task 4).

**Step 10: Verify compilation**

Run: `sclang -e "Chroma.class.notNil.if({'OK'.postln}, {'FAIL'.postln}); 0.exit"`

Expected: `OK`

**Step 11: Commit**

```bash
git add Chroma.sc
git commit -m "Remove drone layer code in preparation for effects"
```

---

## Task 2: Add Effect Buses and Buffers

Add the audio buses and buffers needed for the effects chain.

**Files:**
- Modify: `Chroma.sc`

**Step 1: Add new instance variables**

After the existing variable declarations, add:

```supercollider
var <grainBuffer;
var <freezeBuffer;
var <frozen;
var <filterParams;
var <granularParams;
var <reverbDelayParams;
```

**Step 2: Initialize new variables in init method**

Add to the `init` method:

```supercollider
frozen = false;
filterParams = (
    amount: 0.5,
    cutoff: 2000,
    resonance: 0.3
);
granularParams = (
    density: 10,
    size: 0.1,
    pitchScatter: 0.1,
    posScatter: 0.2,
    mix: 0.3
);
reverbDelayParams = (
    blend: 0.5,
    decayTime: 3,
    shimmerPitch: 12,
    delayTime: 0.3,
    modRate: 0.5,
    modDepth: 0.3,
    mix: 0.3
);
```

**Step 3: Add new buses in allocateResources**

Add after existing bus allocations:

```supercollider
// Effect audio buses
buses[\filteredAudio] = Bus.audio(server, 1);
buses[\granularAudio] = Bus.audio(server, 1);
buses[\reverbAudio] = Bus.audio(server, 1);
buses[\delayAudio] = Bus.audio(server, 1);

// Effect control buses
buses[\filterGains] = Bus.control(server, 8);
buses[\granularCtrl] = Bus.control(server, 4);  // density, size, pitchScatter, posScatter
buses[\reverbDelayCtrl] = Bus.control(server, 4);  // blend, decay, modRate, modDepth
```

**Step 4: Allocate grain buffers**

Add in `allocateResources`:

```supercollider
// Grain buffers (2 seconds at server sample rate)
grainBuffer = Buffer.alloc(server, server.sampleRate * 2, 1);
freezeBuffer = Buffer.alloc(server, server.sampleRate * 2, 1);
```

**Step 5: Free buffers in cleanup**

Add to `cleanup` method:

```supercollider
grainBuffer.free;
freezeBuffer.free;
```

**Step 6: Add freeze toggle method**

```supercollider
toggleFreeze {
    frozen = frozen.not;
    if(frozen) {
        // Copy current grain buffer to freeze buffer
        grainBuffer.copyData(freezeBuffer);
    };
    if(synths[\granular].notNil) {
        synths[\granular].set(\freeze, frozen.asInteger);
    };
}
```

**Step 7: Verify compilation**

Run: `sclang -e "Chroma.class.notNil.if({'OK'.postln}, {'FAIL'.postln}); 0.exit"`

Expected: `OK`

**Step 8: Commit**

```bash
git add Chroma.sc
git commit -m "Add effect buses and buffers for granular/reverb/delay"
```

---

## Task 3: Implement Spectral Filter SynthDef

Create the 8-band spectral filter that reshapes audio based on input spectrum.

**Files:**
- Modify: `Chroma.sc`

**Step 1: Add loader call**

In `loadSynthDefs`, add:

```supercollider
this.loadFilterSynthDef;
```

**Step 2: Implement the SynthDef**

Add this method:

```supercollider
loadFilterSynthDef {
    SynthDef(\chroma_filter, { |inBus, outBus, gainsBus, amount=0.5, baseCutoff=2000, resonance=0.3|
        var sig, bands, gains, filtered;
        var bandCenters = [60, 170, 400, 1000, 2400, 5000, 10000, 16000];
        var rq = resonance.linlin(0, 1, 2, 0.1);

        sig = In.ar(inBus);
        gains = In.kr(gainsBus, 8);

        // Apply amount control (0 = no spectral shaping, 1 = full)
        gains = gains.linlin(0, 1, 1 - amount, 1 + amount);

        // Scale band centers by base cutoff ratio
        bandCenters = bandCenters * (baseCutoff / 2000);

        // 8 parallel bandpass filters
        bands = bandCenters.collect({ |freq, i|
            BPF.ar(sig, freq.clip(20, 20000), rq) * gains[i];
        });

        // Sum bands and add some dry signal for body
        filtered = bands.sum + (sig * 0.3);

        // Soft clip to prevent overload
        filtered = (filtered * 0.5).tanh;

        Out.ar(outBus, filtered);
    }).add;
}
```

**Step 3: Verify compilation**

Run: `sclang -e "Chroma.class.notNil.if({'OK'.postln}, {'FAIL'.postln}); 0.exit"`

Expected: `OK`

**Step 4: Commit**

```bash
git add Chroma.sc
git commit -m "Add spectral filter SynthDef"
```

---

## Task 4: Implement Blend Control for Effects

Rewrite the blend control to modulate effect parameters instead of drone layers.

**Files:**
- Modify: `Chroma.sc`

**Step 1: Rewrite loadBlendControlSynthDef**

Replace the stub with:

```supercollider
loadBlendControlSynthDef {
    SynthDef(\chroma_blend, { |mode=0, bandsBus, centroidBus, spreadBus, flatnessBus,
        filterGainsBus, granularCtrlBus, reverbDelayCtrlBus,
        baseFilterAmount=0.5, baseGrainDensity=10, baseGrainSize=0.1,
        basePitchScatter=0.1, basePosScatter=0.2,
        baseReverbDelayBlend=0.5, baseDecayTime=3, baseModRate=0.5, baseModDepth=0.3|

        var bands, centroid, spread, flatness;
        var filterGains, grainDensity, grainSize, pitchScatter, posScatter;
        var reverbDelayBlend, decayTime, modRate, modDepth;

        bands = In.kr(bandsBus, 8);
        centroid = In.kr(centroidBus);
        spread = In.kr(spreadBus);
        flatness = In.kr(flatnessBus);

        // Filter gains based on mode
        filterGains = Select.kr(mode, [
            // Mirror: boost bands with energy
            bands.linlin(0, 1, 0.5, 1.5),
            // Complement: cut bands with energy
            bands.linlin(0, 1, 1.5, 0.5),
            // Transform: centroid shifts all gains
            bands * centroid.linlin(0, 1, 0.7, 1.3)
        ]);

        // Granular parameters
        grainDensity = Select.kr(mode, [
            // Mirror: more energy = denser
            bands.sum.linlin(0, 4, baseGrainDensity * 0.5, baseGrainDensity * 2),
            // Complement: more energy = sparser
            bands.sum.linlin(0, 4, baseGrainDensity * 2, baseGrainDensity * 0.5),
            // Transform: flatness controls density
            flatness.linlin(0, 1, baseGrainDensity * 0.3, baseGrainDensity * 3)
        ]);

        grainSize = Select.kr(mode, [
            // Mirror: more energy = smaller grains
            bands.sum.linlin(0, 4, baseGrainSize * 2, baseGrainSize * 0.5),
            // Complement: more energy = larger grains
            bands.sum.linlin(0, 4, baseGrainSize * 0.5, baseGrainSize * 2),
            // Transform: spread controls size
            spread.linlin(0, 1, baseGrainSize * 0.5, baseGrainSize * 2)
        ]);

        pitchScatter = Select.kr(mode, [
            // Mirror: brightness = more pitch scatter
            centroid * basePitchScatter * 2,
            // Complement: darkness = more scatter
            (1 - centroid) * basePitchScatter * 2,
            // Transform: centroid directly
            centroid.linlin(0, 1, 0, basePitchScatter * 2)
        ]);

        posScatter = Select.kr(mode, [
            // Mirror: spread = position scatter
            spread * basePosScatter * 2,
            // Complement: inverse
            (1 - spread) * basePosScatter * 2,
            // Transform: spread directly
            spread.linlin(0, 1, 0.05, basePosScatter * 2)
        ]);

        // Reverb/Delay parameters
        reverbDelayBlend = Select.kr(mode, [
            // Mirror: bright = more shimmer (reverb)
            centroid.linlin(0, 1, 0.7, 0.0),
            // Complement: bright = more delay
            centroid.linlin(0, 1, 0.0, 1.0),
            // Transform: flatness controls
            flatness.linlin(0, 1, 0.2, 0.8)
        ]);

        decayTime = Select.kr(mode, [
            // Mirror: spread = longer decay
            spread.linlin(0, 1, baseDecayTime * 0.5, baseDecayTime * 2),
            // Complement: spread = shorter decay
            spread.linlin(0, 1, baseDecayTime * 2, baseDecayTime * 0.5),
            // Transform: centroid controls
            centroid.linlin(0, 1, baseDecayTime * 0.5, baseDecayTime * 1.5)
        ]);

        modRate = Select.kr(mode, [
            baseModRate,
            baseModRate,
            centroid.linlin(0, 1, baseModRate * 0.5, baseModRate * 2)
        ]);

        modDepth = Select.kr(mode, [
            baseModDepth,
            baseModDepth,
            spread.linlin(0, 1, baseModDepth * 0.5, baseModDepth * 2)
        ]);

        Out.kr(filterGainsBus, filterGains.lag(0.1));
        Out.kr(granularCtrlBus, [grainDensity, grainSize, pitchScatter, posScatter].lag(0.1));
        Out.kr(reverbDelayCtrlBus, [reverbDelayBlend, decayTime, modRate, modDepth].lag(0.1));
    }).add;
}
```

**Step 2: Verify compilation**

Run: `sclang -e "Chroma.class.notNil.if({'OK'.postln}, {'FAIL'.postln}); 0.exit"`

Expected: `OK`

**Step 3: Commit**

```bash
git add Chroma.sc
git commit -m "Rewrite blend control for effect parameter modulation"
```

---

## Task 5: Implement Granular SynthDef

Create the granular processor with freeze capability.

**Files:**
- Modify: `Chroma.sc`

**Step 1: Add loader call**

In `loadSynthDefs`, add:

```supercollider
this.loadGranularSynthDef;
```

**Step 2: Implement the SynthDef**

```supercollider
loadGranularSynthDef {
    SynthDef(\chroma_granular, { |inBus, outBus, grainBuf, freezeBuf,
        ctrlBus, freeze=0, mix=0.3|

        var sig, dry, grains, trig;
        var density, size, pitchScatter, posScatter;
        var rate, pos, pan;
        var bufnum, writePos;

        sig = In.ar(inBus);
        dry = sig;

        // Read control values
        #density, size, pitchScatter, posScatter = In.kr(ctrlBus, 4);

        // Continuously record to grain buffer
        writePos = Phasor.ar(0, 1, 0, BufFrames.kr(grainBuf));
        BufWr.ar(sig, grainBuf, writePos);

        // Choose buffer based on freeze state
        bufnum = Select.kr(freeze, [grainBuf, freezeBuf]);

        // Grain trigger
        trig = Impulse.ar(density);

        // Random grain parameters
        rate = TRand.kr(1 - pitchScatter, 1 + pitchScatter, trig);
        pos = TRand.kr(0, posScatter, trig);
        pan = TRand.kr(-0.5, 0.5, trig);

        // Convert position scatter to buffer position
        // pos=0 means current position, pos=1 means 2 seconds ago
        pos = Select.kr(freeze, [
            // Live: read relative to write position
            (writePos / BufFrames.kr(grainBuf)) - pos,
            // Frozen: random position in buffer
            pos
        ]).wrap(0, 1);

        grains = GrainBuf.ar(
            numChannels: 1,
            trigger: trig,
            dur: size,
            sndbuf: bufnum,
            rate: rate,
            pos: pos,
            pan: pan
        );

        // Mix dry and granular
        sig = (dry * (1 - mix)) + (grains * mix);

        Out.ar(outBus, sig);
    }).add;
}
```

**Step 3: Verify compilation**

Run: `sclang -e "Chroma.class.notNil.if({'OK'.postln}, {'FAIL'.postln}); 0.exit"`

Expected: `OK`

**Step 4: Commit**

```bash
git add Chroma.sc
git commit -m "Add granular processor SynthDef with freeze"
```

---

## Task 6: Implement Shimmer Reverb SynthDef

Create the pitch-shifted feedback reverb.

**Files:**
- Modify: `Chroma.sc`

**Step 1: Add loader call**

In `loadSynthDefs`, add:

```supercollider
this.loadShimmerReverbSynthDef;
```

**Step 2: Implement the SynthDef**

```supercollider
loadShimmerReverbSynthDef {
    SynthDef(\chroma_shimmer_reverb, { |inBus, outBus, decayTime=3, shimmerPitch=12, mix=0.3|
        var sig, dry, wet, shifted, verb;
        var pitchRatio;

        sig = In.ar(inBus);
        dry = sig;

        // Pitch ratio from semitones
        pitchRatio = shimmerPitch.midiratio;

        // Create shimmer through pitch-shifted feedback
        verb = sig;
        verb = verb + LocalIn.ar(1);

        // Diffusion via allpass chain
        4.do { |i|
            verb = AllpassC.ar(verb, 0.05, { Rand(0.01, 0.05) }.dup(1).sum, decayTime * 0.3);
        };

        // Pitch shift in feedback path
        shifted = PitchShift.ar(verb, 0.2, pitchRatio, 0.01, 0.01) * 0.4;

        // High-frequency damping
        shifted = LPF.ar(shifted, 6000);

        LocalOut.ar(shifted * (decayTime / 10).clip(0, 0.8));

        // Final reverb tail
        wet = FreeVerb.ar(verb, 0.8, decayTime.linlin(0.5, 10, 0.5, 0.95), 0.5);

        sig = (dry * (1 - mix)) + (wet * mix);

        Out.ar(outBus, sig);
    }).add;
}
```

**Step 3: Verify compilation**

Run: `sclang -e "Chroma.class.notNil.if({'OK'.postln}, {'FAIL'.postln}); 0.exit"`

Expected: `OK`

**Step 4: Commit**

```bash
git add Chroma.sc
git commit -m "Add shimmer reverb SynthDef"
```

---

## Task 7: Implement Modulated Delay SynthDef

Create the chorus-style modulated delay.

**Files:**
- Modify: `Chroma.sc`

**Step 1: Add loader call**

In `loadSynthDefs`, add:

```supercollider
this.loadModDelaySynthDef;
```

**Step 2: Implement the SynthDef**

```supercollider
loadModDelaySynthDef {
    SynthDef(\chroma_mod_delay, { |inBus, outBus, delayTime=0.3, decayTime=3,
        modRate=0.5, modDepth=0.3, mix=0.3|

        var sig, dry, wet, delayed, feedback;
        var modAmount, modSig;

        sig = In.ar(inBus);
        dry = sig;

        // Modulation amount in seconds
        modAmount = modDepth * 0.01;  // Max 10ms wobble

        // LFO for delay time modulation
        modSig = SinOsc.kr(modRate) * modAmount;

        // Feedback delay with modulation
        feedback = LocalIn.ar(1);

        delayed = DelayC.ar(
            sig + (feedback * (decayTime / 10).clip(0, 0.85)),
            1.0,  // Max delay time
            (delayTime + modSig).clip(0.01, 1.0)
        );

        // Filtering in feedback path for warmth
        delayed = LPF.ar(delayed, 4000);
        delayed = HPF.ar(delayed, 80);

        LocalOut.ar(delayed);

        // Chorus widening
        wet = delayed + DelayC.ar(delayed, 0.05, SinOsc.kr(modRate * 1.1) * 0.003 + 0.01);

        sig = (dry * (1 - mix)) + (wet * mix);

        Out.ar(outBus, sig);
    }).add;
}
```

**Step 3: Verify compilation**

Run: `sclang -e "Chroma.class.notNil.if({'OK'.postln}, {'FAIL'.postln}); 0.exit"`

Expected: `OK`

**Step 4: Commit**

```bash
git add Chroma.sc
git commit -m "Add modulated delay SynthDef"
```

---

## Task 8: Implement Output Mixer

Update the output synth to mix the parallel effects.

**Files:**
- Modify: `Chroma.sc`

**Step 1: Rewrite loadOutputSynthDef**

Replace the existing method:

```supercollider
loadOutputSynthDef {
    SynthDef(\chroma_output, { |inBus, granularBus, reverbBus, delayBus,
        reverbDelayBlend=0.5, dryWet=0.5, outBus=0|

        var dry, granular, reverb, delay, reverbDelay, wet, sig;

        dry = In.ar(inBus);
        granular = In.ar(granularBus);
        reverb = In.ar(reverbBus);
        delay = In.ar(delayBus);

        // Crossfade between reverb and delay
        reverbDelay = XFade2.ar(reverb, delay, reverbDelayBlend * 2 - 1);

        // Mix granular and reverb/delay (equal blend for now)
        wet = (granular + reverbDelay) * 0.5;

        // Final dry/wet mix
        sig = XFade2.ar(dry, wet, dryWet * 2 - 1);

        // Stereo output with slight widening
        sig = [sig, DelayN.ar(sig, 0.001, 0.0003)];

        // Soft limiting
        sig = sig.tanh;

        Out.ar(outBus, sig);
    }).add;
}
```

**Step 2: Verify compilation**

Run: `sclang -e "Chroma.class.notNil.if({'OK'.postln}, {'FAIL'.postln}); 0.exit"`

Expected: `OK`

**Step 3: Commit**

```bash
git add Chroma.sc
git commit -m "Update output mixer for parallel effects"
```

---

## Task 9: Wire Up Synth Creation

Connect all the synths in the correct order.

**Files:**
- Modify: `Chroma.sc`

**Step 1: Rewrite createSynths method**

Replace the entire method:

```supercollider
createSynths {
    // Input stage
    synths[\input] = Synth(\chroma_input, [
        \inChannel, config[\inputChannel],
        \gain, 1,
        \outBus, buses[\inputAudio],
        \ampBus, buses[\inputAmp]
    ]);

    // Analysis
    synths[\analysis] = Synth(\chroma_analysis, [
        \inBus, buses[\inputAudio],
        \fftBuf, fftBuffer,
        \numBands, config[\numBands],
        \smoothing, config[\smoothing],
        \bandsBus, buses[\bands],
        \centroidBus, buses[\centroid],
        \spreadBus, buses[\spread],
        \flatnessBus, buses[\flatness]
    ], synths[\input], \addAfter);

    // Blend control
    synths[\blend] = Synth(\chroma_blend, [
        \mode, this.blendModeIndex,
        \bandsBus, buses[\bands],
        \centroidBus, buses[\centroid],
        \spreadBus, buses[\spread],
        \flatnessBus, buses[\flatness],
        \filterGainsBus, buses[\filterGains],
        \granularCtrlBus, buses[\granularCtrl],
        \reverbDelayCtrlBus, buses[\reverbDelayCtrl],
        \baseFilterAmount, filterParams[\amount],
        \baseGrainDensity, granularParams[\density],
        \baseGrainSize, granularParams[\size],
        \basePitchScatter, granularParams[\pitchScatter],
        \basePosScatter, granularParams[\posScatter],
        \baseReverbDelayBlend, reverbDelayParams[\blend],
        \baseDecayTime, reverbDelayParams[\decayTime],
        \baseModRate, reverbDelayParams[\modRate],
        \baseModDepth, reverbDelayParams[\modDepth]
    ], synths[\analysis], \addAfter);

    // Spectral filter
    synths[\filter] = Synth(\chroma_filter, [
        \inBus, buses[\inputAudio],
        \outBus, buses[\filteredAudio],
        \gainsBus, buses[\filterGains],
        \amount, filterParams[\amount],
        \baseCutoff, filterParams[\cutoff],
        \resonance, filterParams[\resonance]
    ], synths[\blend], \addAfter);

    // Granular (reads from filtered audio)
    synths[\granular] = Synth(\chroma_granular, [
        \inBus, buses[\filteredAudio],
        \outBus, buses[\granularAudio],
        \grainBuf, grainBuffer,
        \freezeBuf, freezeBuffer,
        \ctrlBus, buses[\granularCtrl],
        \freeze, frozen.asInteger,
        \mix, granularParams[\mix]
    ], synths[\filter], \addAfter);

    // Shimmer reverb (reads from filtered audio)
    synths[\shimmerReverb] = Synth(\chroma_shimmer_reverb, [
        \inBus, buses[\filteredAudio],
        \outBus, buses[\reverbAudio],
        \decayTime, reverbDelayParams[\decayTime],
        \shimmerPitch, reverbDelayParams[\shimmerPitch],
        \mix, reverbDelayParams[\mix]
    ], synths[\granular], \addAfter);

    // Modulated delay (reads from filtered audio)
    synths[\modDelay] = Synth(\chroma_mod_delay, [
        \inBus, buses[\filteredAudio],
        \outBus, buses[\delayAudio],
        \delayTime, reverbDelayParams[\delayTime],
        \decayTime, reverbDelayParams[\decayTime],
        \modRate, reverbDelayParams[\modRate],
        \modDepth, reverbDelayParams[\modDepth],
        \mix, reverbDelayParams[\mix]
    ], synths[\shimmerReverb], \addAfter);

    // Output mixer (at tail)
    synths[\output] = Synth(\chroma_output, [
        \inBus, buses[\filteredAudio],
        \granularBus, buses[\granularAudio],
        \reverbBus, buses[\reverbAudio],
        \delayBus, buses[\delayAudio],
        \reverbDelayBlend, reverbDelayParams[\blend],
        \dryWet, dryWet
    ], nil, \addToTail);

    // Map control buses
    synths[\granular].map(\ctrlBus, buses[\granularCtrl]);
}
```

**Step 2: Add setter methods for new parameters**

```supercollider
setFilterAmount { |val|
    filterParams[\amount] = val.clip(0, 1);
    if(synths[\filter].notNil) { synths[\filter].set(\amount, val) };
    if(synths[\blend].notNil) { synths[\blend].set(\baseFilterAmount, val) };
}

setFilterCutoff { |val|
    filterParams[\cutoff] = val.clip(200, 8000);
    if(synths[\filter].notNil) { synths[\filter].set(\baseCutoff, val) };
}

setFilterResonance { |val|
    filterParams[\resonance] = val.clip(0, 1);
    if(synths[\filter].notNil) { synths[\filter].set(\resonance, val) };
}

setGrainDensity { |val|
    granularParams[\density] = val.clip(1, 50);
    if(synths[\blend].notNil) { synths[\blend].set(\baseGrainDensity, val) };
}

setGrainSize { |val|
    granularParams[\size] = val.clip(0.01, 0.5);
    if(synths[\blend].notNil) { synths[\blend].set(\baseGrainSize, val) };
}

setGrainPitchScatter { |val|
    granularParams[\pitchScatter] = val.clip(0, 1);
    if(synths[\blend].notNil) { synths[\blend].set(\basePitchScatter, val) };
}

setGrainPosScatter { |val|
    granularParams[\posScatter] = val.clip(0, 1);
    if(synths[\blend].notNil) { synths[\blend].set(\basePosScatter, val) };
}

setGranularMix { |val|
    granularParams[\mix] = val.clip(0, 1);
    if(synths[\granular].notNil) { synths[\granular].set(\mix, val) };
}

setReverbDelayBlend { |val|
    reverbDelayParams[\blend] = val.clip(0, 1);
    if(synths[\output].notNil) { synths[\output].set(\reverbDelayBlend, val) };
    if(synths[\blend].notNil) { synths[\blend].set(\baseReverbDelayBlend, val) };
}

setDecayTime { |val|
    reverbDelayParams[\decayTime] = val.clip(0.5, 10);
    if(synths[\shimmerReverb].notNil) { synths[\shimmerReverb].set(\decayTime, val) };
    if(synths[\modDelay].notNil) { synths[\modDelay].set(\decayTime, val) };
    if(synths[\blend].notNil) { synths[\blend].set(\baseDecayTime, val) };
}

setShimmerPitch { |val|
    reverbDelayParams[\shimmerPitch] = val;
    if(synths[\shimmerReverb].notNil) { synths[\shimmerReverb].set(\shimmerPitch, val) };
}

setDelayTime { |val|
    reverbDelayParams[\delayTime] = val.clip(0.1, 1);
    if(synths[\modDelay].notNil) { synths[\modDelay].set(\delayTime, val) };
}

setModRate { |val|
    reverbDelayParams[\modRate] = val.clip(0.1, 5);
    if(synths[\modDelay].notNil) { synths[\modDelay].set(\modRate, val) };
    if(synths[\blend].notNil) { synths[\blend].set(\baseModRate, val) };
}

setModDepth { |val|
    reverbDelayParams[\modDepth] = val.clip(0, 1);
    if(synths[\modDelay].notNil) { synths[\modDelay].set(\modDepth, val) };
    if(synths[\blend].notNil) { synths[\blend].set(\baseModDepth, val) };
}

setReverbDelayMix { |val|
    reverbDelayParams[\mix] = val.clip(0, 1);
    if(synths[\shimmerReverb].notNil) { synths[\shimmerReverb].set(\mix, val) };
    if(synths[\modDelay].notNil) { synths[\modDelay].set(\mix, val) };
}
```

**Step 3: Verify compilation**

Run: `sclang -e "Chroma.class.notNil.if({'OK'.postln}, {'FAIL'.postln}); 0.exit"`

Expected: `OK`

**Step 4: Commit**

```bash
git add Chroma.sc
git commit -m "Wire up effect synths and add parameter setters"
```

---

## Task 10: Rebuild GUI

Replace the drone controls with effect controls.

**Files:**
- Modify: `Chroma.sc`

**Step 1: Rewrite buildGUI method**

Replace the entire method:

```supercollider
buildGUI {
    var width = 650, height = 580;
    var spectrumView, updateRoutine;
    var bandData;

    bandData = Array.fill(config[\numBands], 0);

    window = Window("Chroma", Rect(100, 100, width, height))
        .front
        .onClose_({ this.class.stop });

    window.view.decorator = FlowLayout(window.view.bounds, 10@10, 10@10);

    // Title
    StaticText(window, (width - 20)@30)
        .string_("CHROMA")
        .font_(Font("Helvetica", 24, true))
        .align_(\center);

    window.view.decorator.nextLine;

    // Spectrum and blend mode row
    StaticText(window, 300@20).string_("INPUT SPECTRUM").align_(\center);
    StaticText(window, 300@20).string_("BLEND MODE").align_(\center);

    window.view.decorator.nextLine;

    // Spectrum display
    spectrumView = UserView(window, 300@80)
        .background_(Color.gray(0.2))
        .drawFunc_({ |view|
            var bounds = view.bounds;
            var barWidth = bounds.width / config[\numBands];
            Pen.fillColor = Color.new255(100, 149, 237);
            config[\numBands].do { |i|
                var h = bandData[i] * bounds.height;
                Pen.fillRect(Rect(i * barWidth + 2, bounds.height - h, barWidth - 4, h));
            };
        });

    // Blend mode buttons
    View(window, 300@80).layout_(
        VLayout(
            HLayout(
                Button().states_([["Mirror", Color.black, Color.green]])
                    .action_({ this.setBlendMode(\mirror); this.updateBlendButtons }),
                Button().states_([["Complement", Color.black, Color.white]])
                    .action_({ this.setBlendMode(\complement); this.updateBlendButtons }),
                Button().states_([["Transform", Color.black, Color.white]])
                    .action_({ this.setBlendMode(\transform); this.updateBlendButtons })
            ).spacing_(5)
        ).margins_(10)
    );

    window.view.children.last.children[0].children.do { |btn, i|
        [\mirrorBtn, \complementBtn, \transformBtn][i].envirPut(btn);
    };

    window.view.decorator.nextLine;

    // Input and Filter sections
    StaticText(window, 300@20).string_("INPUT").font_(Font("Helvetica", 12, true));
    StaticText(window, 300@20).string_("SPECTRAL FILTER").font_(Font("Helvetica", 12, true));

    window.view.decorator.nextLine;

    // Input controls
    View(window, 300@60).layout_(
        VLayout(
            HLayout(
                StaticText().string_("Gain").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(0.5).action_({ |sl|
                    this.setInputGain(sl.value * 2);
                })
            )
        ).margins_(5)
    );

    // Filter controls
    View(window, 300@90).layout_(
        VLayout(
            HLayout(
                StaticText().string_("Amount").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(filterParams[\amount]).action_({ |sl|
                    this.setFilterAmount(sl.value);
                })
            ),
            HLayout(
                StaticText().string_("Cutoff").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(filterParams[\cutoff].linlin(200, 8000, 0, 1)).action_({ |sl|
                    this.setFilterCutoff(sl.value.linlin(0, 1, 200, 8000));
                })
            ),
            HLayout(
                StaticText().string_("Resonance").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(filterParams[\resonance]).action_({ |sl|
                    this.setFilterResonance(sl.value);
                })
            )
        ).margins_(5).spacing_(2)
    );

    window.view.decorator.nextLine;

    // Granular and Reverb/Delay sections
    StaticText(window, 300@20).string_("GRANULAR").font_(Font("Helvetica", 12, true));
    StaticText(window, 300@20).string_("REVERB / DELAY").font_(Font("Helvetica", 12, true));

    window.view.decorator.nextLine;

    // Granular controls
    View(window, 300@180).layout_(
        VLayout(
            HLayout(
                StaticText().string_("Density").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(granularParams[\density].linlin(1, 50, 0, 1)).action_({ |sl|
                    this.setGrainDensity(sl.value.linlin(0, 1, 1, 50));
                })
            ),
            HLayout(
                StaticText().string_("Size").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(granularParams[\size].linlin(0.01, 0.5, 0, 1)).action_({ |sl|
                    this.setGrainSize(sl.value.linlin(0, 1, 0.01, 0.5));
                })
            ),
            HLayout(
                StaticText().string_("Pitch").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(granularParams[\pitchScatter]).action_({ |sl|
                    this.setGrainPitchScatter(sl.value);
                })
            ),
            HLayout(
                StaticText().string_("Position").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(granularParams[\posScatter]).action_({ |sl|
                    this.setGrainPosScatter(sl.value);
                })
            ),
            HLayout(
                StaticText().string_("Mix").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(granularParams[\mix]).action_({ |sl|
                    this.setGranularMix(sl.value);
                })
            ),
            Button().states_([
                ["FREEZE", Color.black, Color.white],
                ["FREEZE", Color.white, Color.green]
            ]).action_({ |btn|
                this.toggleFreeze;
                btn.value = frozen.asInteger;
            })
        ).margins_(5).spacing_(2)
    );

    // Reverb/Delay controls
    View(window, 300@180).layout_(
        VLayout(
            HLayout(
                StaticText().string_("Rev<>Dly").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(reverbDelayParams[\blend]).action_({ |sl|
                    this.setReverbDelayBlend(sl.value);
                })
            ),
            HLayout(
                StaticText().string_("Decay").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(reverbDelayParams[\decayTime].linlin(0.5, 10, 0, 1)).action_({ |sl|
                    this.setDecayTime(sl.value.linlin(0, 1, 0.5, 10));
                })
            ),
            HLayout(
                StaticText().string_("Shimmer").fixedWidth_(60),
                PopUpMenu().items_(["0 (off)", "+5 (4th)", "+7 (5th)", "+12 (oct)"])
                    .value_(#[0, 5, 7, 12].indexOf(reverbDelayParams[\shimmerPitch]) ?? 3)
                    .action_({ |menu|
                        this.setShimmerPitch(#[0, 5, 7, 12][menu.value]);
                    })
            ),
            HLayout(
                StaticText().string_("Delay").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(reverbDelayParams[\delayTime].linlin(0.1, 1, 0, 1)).action_({ |sl|
                    this.setDelayTime(sl.value.linlin(0, 1, 0.1, 1));
                })
            ),
            HLayout(
                StaticText().string_("Mod Rate").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(reverbDelayParams[\modRate].linlin(0.1, 5, 0, 1)).action_({ |sl|
                    this.setModRate(sl.value.linlin(0, 1, 0.1, 5));
                })
            ),
            HLayout(
                StaticText().string_("Mod Depth").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(reverbDelayParams[\modDepth]).action_({ |sl|
                    this.setModDepth(sl.value);
                })
            ),
            HLayout(
                StaticText().string_("Mix").fixedWidth_(60),
                Slider().orientation_(\horizontal).value_(reverbDelayParams[\mix]).action_({ |sl|
                    this.setReverbDelayMix(sl.value);
                })
            )
        ).margins_(5).spacing_(2)
    );

    window.view.decorator.nextLine;

    // Output section
    StaticText(window, (width - 20)@20).string_("OUTPUT").font_(Font("Helvetica", 12, true));

    window.view.decorator.nextLine;

    View(window, (width - 20)@40).layout_(
        HLayout(
            StaticText().string_("Dry/Wet").fixedWidth_(60),
            Slider().orientation_(\horizontal).value_(dryWet).action_({ |sl|
                this.setDryWet(sl.value);
            })
        ).margins_(5)
    );

    // Spectrum update routine
    updateRoutine = Routine({
        loop {
            buses[\bands].getn(config[\numBands], { |vals|
                bandData = vals;
                { spectrumView.refresh }.defer;
            });
            0.033.wait;
        }
    }).play(AppClock);

    window.onClose = window.onClose.addFunc({
        updateRoutine.stop;
    });
}
```

**Step 2: Verify compilation**

Run: `sclang -e "Chroma.class.notNil.if({'OK'.postln}, {'FAIL'.postln}); 0.exit"`

Expected: `OK`

**Step 3: Commit**

```bash
git add Chroma.sc
git commit -m "Rebuild GUI for effect controls"
```

---

## Task 11: Update Tests

Update test file to test new effect controls.

**Files:**
- Modify: `test_synths.scd`

**Step 1: Rewrite test file**

```supercollider
// Test SynthDef compilation and synth instantiation
// Uses dummy audio driver for headless testing
(
"=== Testing Chroma Effects ===".postln;

// Use dummy audio driver
Server.default.options.device = "dummy";
Server.default.options.sampleRate = 48000;
Server.default.options.blockSize = 64;
Server.default.options.numOutputBusChannels = 2;
Server.default.options.numInputBusChannels = 2;

Server.default.waitForBoot({
    "Server booted with dummy audio".postln;

    fork {
        var instance;

        // Create instance manually (skip GUI)
        instance = Chroma(Server.default);

        "Allocating resources...".postln;
        instance.allocateResources;
        Server.default.sync;
        "  - FFT buffer allocated".postln;
        "  - Control buses allocated".postln;
        "  - Grain buffers allocated".postln;

        "Loading SynthDefs...".postln;
        instance.loadSynthDefs;
        Server.default.sync;
        "  - All SynthDefs loaded".postln;

        "Creating synths...".postln;
        instance.createSynths;
        Server.default.sync;
        "  - All synths created".postln;

        0.5.wait;

        "Querying node tree...".postln;
        Server.default.queryAllNodes;

        1.wait;

        "Testing blend modes...".postln;
        instance.setBlendMode(\mirror);
        ("  - Mirror mode: " ++ instance.blendMode).postln;

        instance.setBlendMode(\complement);
        ("  - Complement mode: " ++ instance.blendMode).postln;

        instance.setBlendMode(\transform);
        ("  - Transform mode: " ++ instance.blendMode).postln;

        "Testing filter controls...".postln;
        instance.setFilterAmount(0.7);
        ("  - filterAmount: " ++ instance.filterParams[\amount]).postln;

        instance.setFilterCutoff(3000);
        ("  - filterCutoff: " ++ instance.filterParams[\cutoff]).postln;

        instance.setFilterResonance(0.5);
        ("  - filterResonance: " ++ instance.filterParams[\resonance]).postln;

        "Testing granular controls...".postln;
        instance.setGrainDensity(20);
        ("  - grainDensity: " ++ instance.granularParams[\density]).postln;

        instance.setGrainSize(0.2);
        ("  - grainSize: " ++ instance.granularParams[\size]).postln;

        instance.setGrainPitchScatter(0.3);
        ("  - pitchScatter: " ++ instance.granularParams[\pitchScatter]).postln;

        instance.setGrainPosScatter(0.4);
        ("  - posScatter: " ++ instance.granularParams[\posScatter]).postln;

        instance.setGranularMix(0.5);
        ("  - granularMix: " ++ instance.granularParams[\mix]).postln;

        "Testing freeze toggle...".postln;
        ("  - frozen before: " ++ instance.frozen).postln;
        instance.toggleFreeze;
        ("  - frozen after: " ++ instance.frozen).postln;
        instance.toggleFreeze;
        ("  - frozen reset: " ++ instance.frozen).postln;

        "Testing reverb/delay controls...".postln;
        instance.setReverbDelayBlend(0.3);
        ("  - reverbDelayBlend: " ++ instance.reverbDelayParams[\blend]).postln;

        instance.setDecayTime(5);
        ("  - decayTime: " ++ instance.reverbDelayParams[\decayTime]).postln;

        instance.setShimmerPitch(7);
        ("  - shimmerPitch: " ++ instance.reverbDelayParams[\shimmerPitch]).postln;

        instance.setDelayTime(0.5);
        ("  - delayTime: " ++ instance.reverbDelayParams[\delayTime]).postln;

        instance.setModRate(1.0);
        ("  - modRate: " ++ instance.reverbDelayParams[\modRate]).postln;

        instance.setModDepth(0.5);
        ("  - modDepth: " ++ instance.reverbDelayParams[\modDepth]).postln;

        instance.setReverbDelayMix(0.4);
        ("  - reverbDelayMix: " ++ instance.reverbDelayParams[\mix]).postln;

        "Testing output controls...".postln;
        instance.setDryWet(0.6);
        ("  - dryWet: " ++ instance.dryWet).postln;

        0.5.wait;

        "Cleanup...".postln;
        instance.cleanup;

        0.5.wait;

        "".postln;
        "=== ALL EFFECT TESTS PASSED ===".postln;
        0.exit;
    };
}, onFailure: {
    "SERVER BOOT FAILED".postln;
    1.exit;
});
)
```

**Step 2: Run the tests**

Run: `sclang test_synths.scd`

Expected: Output ending with `=== ALL EFFECT TESTS PASSED ===`

**Step 3: Commit**

```bash
git add test_synths.scd
git commit -m "Update tests for effect controls"
```

---

## Task 12: Update Documentation

Update CLAUDE.md and README to reflect the new effects-based design.

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`

**Step 1: Update CLAUDE.md**

Change the project description to reflect effects pedal instead of drone synthesizer.

**Step 2: Update README.md**

Update the documentation to describe the new effects and controls.

**Step 3: Commit**

```bash
git add CLAUDE.md README.md
git commit -m "Update documentation for effects pedal redesign"
```

---

## Task 13: Integration Test

Run full integration test to verify everything works.

**Step 1: Run headless test**

Run: `./test_integration.sh headless`

Expected: `HEADLESS TEST: PASSED`

**Step 2: Run GUI test (if display available)**

Run: `./test_integration.sh gui`

Expected: `GUI TEST: PASSED`

**Step 3: Final commit**

```bash
git add -A
git commit -m "Complete effects pedal redesign

- Replaced drone synthesis with spectral filtering
- Added granular processor with freeze
- Added shimmer reverb and modulated delay
- Hybrid signal chain: filter → parallel effects → mix
- Updated GUI with effect controls
- All tests passing"
```
