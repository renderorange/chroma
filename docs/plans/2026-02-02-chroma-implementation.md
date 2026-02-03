# Chroma Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a spectral-reactive drone synthesizer that analyzes input audio and shapes drone textures with three blend modes.

**Architecture:** FFT chain with buses for loose coupling. Input → Analysis → Drone layers. All synths communicate via control buses. Single `Chroma` class orchestrates lifecycle.

**Tech Stack:** SuperCollider 3.x, Qt GUI framework, FFT UGens

---

## Task 1: Project Structure and Input Stage SynthDef

**Files:**
- Create: `Chroma.sc`

**Step 1: Create the Chroma class skeleton with input SynthDef**

```supercollider
Chroma {
    classvar <instance;

    var <server;
    var <config;
    var <fftBuffer;
    var <buses;
    var <synths;
    var <window;

    *new { |server|
        ^super.new.init(server);
    }

    init { |argServer|
        server = argServer ?? Server.default;
        config = (
            inputChannel: 0,
            fftSize: 2048,
            numBands: 8,
            smoothing: 0.1,
            rootNote: 36
        );
        buses = ();
        synths = ();
        ^this;
    }

    *initClass {
        instance = nil;
    }

    *start { |server|
        if(instance.notNil) {
            "Chroma already running".warn;
            ^instance;
        };
        instance = Chroma(server);
        instance.boot;
        ^instance;
    }

    *stop {
        if(instance.notNil) {
            instance.cleanup;
            instance = nil;
        };
    }

    boot {
        server.waitForBoot {
            this.allocateResources;
            this.loadSynthDefs;
            server.sync;
            this.createSynths;
            this.buildGUI;
            "Chroma ready".postln;
        };
    }

    allocateResources {
        // FFT buffer
        fftBuffer = Buffer.alloc(server, config[\fftSize]);

        // Control buses for spectral data
        buses[\bands] = Bus.control(server, config[\numBands]);
        buses[\centroid] = Bus.control(server, 1);
        buses[\spread] = Bus.control(server, 1);
        buses[\flatness] = Bus.control(server, 1);
        buses[\inputAmp] = Bus.control(server, 1);

        // Audio bus for analyzed signal
        buses[\inputAudio] = Bus.audio(server, 1);
    }

    loadSynthDefs {
        this.loadInputSynthDef;
    }

    loadInputSynthDef {
        SynthDef(\chroma_input, { |inChannel=0, gain=1, outBus, ampBus|
            var sig, amp;
            sig = SoundIn.ar(inChannel) * gain;
            amp = Amplitude.kr(sig, 0.01, 0.1);
            Out.kr(ampBus, amp);
            Out.ar(outBus, sig);
        }).add;
    }

    createSynths {
        synths[\input] = Synth(\chroma_input, [
            \inChannel, config[\inputChannel],
            \gain, 1,
            \outBus, buses[\inputAudio],
            \ampBus, buses[\inputAmp]
        ]);
    }

    buildGUI {
        // Placeholder - Task 6
    }

    cleanup {
        synths.do(_.free);
        buses.do(_.free);
        fftBuffer.free;
        if(window.notNil) { window.close };
        "Chroma stopped".postln;
    }
}
```

**Step 2: Verify the input stage works**

Create a test script to verify:

Run in SuperCollider IDE:
```supercollider
// Boot server first
s.boot;

// Wait for boot, then test
s.doWhenBooted {
    // Load the class (requires Chroma.sc in Extensions folder or current dir)
    Chroma.start;

    // After a few seconds, check that input synth exists
    s.queryAllNodes;

    // Listen to the input (should hear your mic/input)
    { In.ar(Chroma.instance.buses[\inputAudio]) }.play;
};
```

Expected: Node tree shows `chroma_input` synth. Audio passes through.

**Step 3: Commit**

```bash
git add Chroma.sc
git commit -m "feat: add Chroma class skeleton with input stage"
```

---

## Task 2: Spectral Analysis SynthDef

**Files:**
- Modify: `Chroma.sc`

**Step 1: Add the analysis SynthDef to loadSynthDefs**

Add this method after `loadInputSynthDef`:

```supercollider
loadAnalysisSynthDef {
    SynthDef(\chroma_analysis, { |inBus, fftBuf, numBands=8, smoothing=0.1,
        bandsBus, centroidBus, spreadBus, flatnessBus|
        var sig, chain, bands, centroid, spread, flatness;
        var nyquist, bandFreqs, bandMags;

        sig = In.ar(inBus);
        chain = FFT(fftBuf, sig, hop: 0.5, wintype: 1);

        // Spectral features
        centroid = SpecCentroid.kr(chain);
        spread = SpecPcile.kr(chain, 0.9) - SpecPcile.kr(chain, 0.1);
        flatness = SpecFlatness.kr(chain);

        // Normalize centroid (0-20kHz -> 0-1)
        centroid = (centroid / 20000).clip(0, 1);
        // Normalize spread
        spread = (spread / 10000).clip(0, 1);
        // Flatness is already 0-1

        // Band magnitudes using FFTSubbandPower or manual calculation
        // Using 8 bands: sub, bass, low-mid, mid, upper-mid, presence, brilliance, air
        nyquist = SampleRate.ir / 2;
        bandFreqs = [0, 60, 250, 500, 2000, 4000, 6000, 12000, nyquist];

        // Calculate band magnitudes using SpecPcile approximation
        // More accurate would use PV_ChainUGen but this works for control signals
        bandMags = Array.fill(numBands, { |i|
            var lo, hi, mag;
            lo = bandFreqs[i] / nyquist;
            hi = bandFreqs[i + 1] / nyquist;
            // Use amplitude in frequency range as proxy
            mag = FFTPower.kr(chain, lo, hi);
            Lag.kr(mag.ampdb.linlin(-60, 0, 0, 1).clip(0, 1), smoothing);
        });

        // Smooth all outputs
        centroid = Lag.kr(centroid, smoothing);
        spread = Lag.kr(spread, smoothing);
        flatness = Lag.kr(flatness, smoothing);

        Out.kr(bandsBus, bandMags);
        Out.kr(centroidBus, centroid);
        Out.kr(spreadBus, spread);
        Out.kr(flatnessBus, flatness);
    }).add;
}
```

**Step 2: Update loadSynthDefs to call the new method**

```supercollider
loadSynthDefs {
    this.loadInputSynthDef;
    this.loadAnalysisSynthDef;
}
```

**Step 3: Update createSynths to instantiate analysis**

Add after the input synth creation:

```supercollider
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
```

**Step 4: Verify analysis works**

Run in SuperCollider IDE:
```supercollider
Chroma.stop;
Chroma.start;

// Poll the analysis buses
fork {
    loop {
        "Centroid: %".format(Chroma.instance.buses[\centroid].getSynchronous).postln;
        "Flatness: %".format(Chroma.instance.buses[\flatness].getSynchronous).postln;
        0.5.wait;
    }
};
```

Expected: Values change in response to input audio. Louder/brighter sounds show higher centroid.

**Step 5: Commit**

```bash
git add Chroma.sc
git commit -m "feat: add spectral analysis engine with band magnitudes and features"
```

---

## Task 3: Drone Layer SynthDefs

**Files:**
- Modify: `Chroma.sc`

**Step 1: Add Sub layer SynthDef**

```supercollider
loadSubSynthDef {
    SynthDef(\chroma_sub, { |out=0, freq=65.41, amp=0.3, gate=1|
        var sig, env;
        env = EnvGen.kr(Env.asr(2, 1, 2), gate, doneAction: 2);
        sig = SinOsc.ar([freq, freq * 0.5], 0, 0.5).sum;
        sig = sig + SinOsc.ar(freq * 2, 0, 0.2);
        sig = LPF.ar(sig, 200);
        Out.ar(out, sig * amp * env ! 2);
    }).add;
}
```

**Step 2: Add Pad layer SynthDef**

```supercollider
loadPadSynthDef {
    SynthDef(\chroma_pad, { |out=0, freq=65.41, amp=0.3, detune=0.01, cutoff=2000, gate=1|
        var sig, env, freqs;
        env = EnvGen.kr(Env.asr(3, 1, 3), gate, doneAction: 2);
        freqs = freq * [1, 1 + detune, 1 - detune, 2, 2 * (1 + detune)];
        sig = Saw.ar(freqs).sum * 0.2;
        sig = RLPF.ar(sig, cutoff, 0.5);
        Out.ar(out, sig * amp * env ! 2);
    }).add;
}
```

**Step 3: Add Shimmer layer SynthDef**

```supercollider
loadShimmerSynthDef {
    SynthDef(\chroma_shimmer, { |out=0, freq=65.41, amp=0.2, gate=1|
        var sig, env, freqs, amps;
        env = EnvGen.kr(Env.asr(4, 1, 4), gate, doneAction: 2);
        // High harmonics with slow LFO modulation
        freqs = freq * [4, 5, 6, 8, 10, 12];
        amps = [0.3, 0.25, 0.2, 0.15, 0.1, 0.05];
        sig = SinOsc.ar(
            freqs * LFNoise1.kr(0.1 ! 6).range(0.99, 1.01),
            0,
            amps * LFNoise1.kr(0.2 ! 6).range(0.5, 1)
        ).sum;
        sig = HPF.ar(sig, 2000);
        Out.ar(out, sig * amp * env ! 2);
    }).add;
}
```

**Step 4: Add Noise layer SynthDef**

```supercollider
loadNoiseSynthDef {
    SynthDef(\chroma_noise, { |out=0, amp=0.1, cutoff=4000, gate=1|
        var sig, env;
        env = EnvGen.kr(Env.asr(2, 1, 2), gate, doneAction: 2);
        sig = PinkNoise.ar;
        sig = BPF.ar(sig, cutoff, 0.5);
        Out.ar(out, sig * amp * env ! 2);
    }).add;
}
```

**Step 5: Update loadSynthDefs**

```supercollider
loadSynthDefs {
    this.loadInputSynthDef;
    this.loadAnalysisSynthDef;
    this.loadSubSynthDef;
    this.loadPadSynthDef;
    this.loadShimmerSynthDef;
    this.loadNoiseSynthDef;
}
```

**Step 6: Verify drone layers work independently**

Run in SuperCollider IDE:
```supercollider
s.boot;
s.doWhenBooted {
    // Load synthdefs
    Chroma.start;

    // Test each layer
    x = Synth(\chroma_sub, [\freq, 65.41, \amp, 0.3]);
    // Listen... then free
    x.set(\gate, 0);

    y = Synth(\chroma_pad, [\freq, 65.41, \amp, 0.3]);
    y.set(\gate, 0);
};
```

Expected: Each layer produces distinct sound character matching design.

**Step 7: Commit**

```bash
git add Chroma.sc
git commit -m "feat: add drone layer SynthDefs (sub, pad, shimmer, noise)"
```

---

## Task 4: Blend Mode Control and Drone Orchestration

**Files:**
- Modify: `Chroma.sc`

**Step 1: Add blend mode state and layer control**

Add instance variables after existing vars:

```supercollider
var <blendMode;  // \mirror, \complement, \transform
var <layerAmps;  // current layer amplitudes
```

Add initialization in `init` method:

```supercollider
blendMode = \mirror;
layerAmps = [0.3, 0.3, 0.2, 0.1];  // sub, pad, shimmer, noise defaults
```

**Step 2: Add blend mode control SynthDef**

This synth reads analysis buses and writes control signals based on blend mode:

```supercollider
loadBlendControlSynthDef {
    SynthDef(\chroma_blend, { |mode=0, bandsBus, centroidBus, spreadBus, flatnessBus,
        subAmpBus, padAmpBus, shimmerAmpBus, noiseAmpBus,
        padCutoffBus, padDetuneBus, rootFreqBus,
        baseSubAmp=0.3, basePadAmp=0.3, baseShimmerAmp=0.2, baseNoiseAmp=0.1,
        baseFreq=65.41|

        var bands, centroid, spread, flatness;
        var subAmp, padAmp, shimmerAmp, noiseAmp;
        var padCutoff, padDetune, rootFreq;

        bands = In.kr(bandsBus, 8);
        centroid = In.kr(centroidBus);
        spread = In.kr(spreadBus);
        flatness = In.kr(flatnessBus);

        // Mode 0: Mirror - direct mapping
        // Mode 1: Complement - inverted mapping
        // Mode 2: Transform - feature-based mapping

        subAmp = Select.kr(mode, [
            bands[0..1].sum * baseSubAmp,           // Mirror: low bands
            (1 - bands[0..1].sum) * baseSubAmp,    // Complement: inverted
            (1 - flatness) * baseSubAmp             // Transform: tonality -> sub
        ]);

        padAmp = Select.kr(mode, [
            bands[2..4].sum * basePadAmp,           // Mirror: mid bands
            (1 - bands[2..4].sum) * basePadAmp,    // Complement
            centroid.linlin(0, 1, 0.5, 1) * basePadAmp  // Transform
        ]);

        shimmerAmp = Select.kr(mode, [
            bands[5..7].sum * baseShimmerAmp,       // Mirror: high bands
            (1 - bands[5..7].sum) * baseShimmerAmp, // Complement
            centroid * baseShimmerAmp               // Transform: brightness -> shimmer
        ]);

        noiseAmp = Select.kr(mode, [
            flatness * baseNoiseAmp,                // Mirror: flatness -> noise
            (1 - flatness) * baseNoiseAmp,         // Complement
            flatness * baseNoiseAmp * 2             // Transform: more noise
        ]);

        padCutoff = Select.kr(mode, [
            centroid.linexp(0, 1, 500, 8000),       // Mirror: follows centroid
            centroid.linexp(0, 1, 8000, 500),       // Complement: inverted
            spread.linexp(0, 1, 1000, 6000)         // Transform: spread controls
        ]);

        padDetune = Select.kr(mode, [
            spread.linlin(0, 1, 0.001, 0.02),       // Mirror: spread -> detune
            0.008,                                   // Complement: fixed
            centroid.linlin(0, 1, 0.005, 0.015)     // Transform: centroid -> detune
        ]);

        rootFreq = Select.kr(mode, [
            baseFreq,                               // Mirror: fixed
            baseFreq,                               // Complement: fixed
            baseFreq * centroid.linexp(0, 1, 0.5, 2) // Transform: +-1 octave
        ]);

        Out.kr(subAmpBus, subAmp.lag(0.1));
        Out.kr(padAmpBus, padAmp.lag(0.1));
        Out.kr(shimmerAmpBus, shimmerAmp.lag(0.1));
        Out.kr(noiseAmpBus, noiseAmp.lag(0.1));
        Out.kr(padCutoffBus, padCutoff.lag(0.1));
        Out.kr(padDetuneBus, padDetune.lag(0.1));
        Out.kr(rootFreqBus, rootFreq.lag(0.2));
    }).add;
}
```

**Step 3: Update allocateResources for blend control buses**

Add to `allocateResources`:

```supercollider
// Layer control buses
buses[\subAmp] = Bus.control(server, 1);
buses[\padAmp] = Bus.control(server, 1);
buses[\shimmerAmp] = Bus.control(server, 1);
buses[\noiseAmp] = Bus.control(server, 1);
buses[\padCutoff] = Bus.control(server, 1);
buses[\padDetune] = Bus.control(server, 1);
buses[\rootFreq] = Bus.control(server, 1);
```

**Step 4: Update loadSynthDefs**

```supercollider
loadSynthDefs {
    this.loadInputSynthDef;
    this.loadAnalysisSynthDef;
    this.loadBlendControlSynthDef;
    this.loadSubSynthDef;
    this.loadPadSynthDef;
    this.loadShimmerSynthDef;
    this.loadNoiseSynthDef;
}
```

**Step 5: Update createSynths to create full synth chain**

Replace `createSynths`:

```supercollider
createSynths {
    var rootFreq = config[\rootNote].midicps;

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
        \subAmpBus, buses[\subAmp],
        \padAmpBus, buses[\padAmp],
        \shimmerAmpBus, buses[\shimmerAmp],
        \noiseAmpBus, buses[\noiseAmp],
        \padCutoffBus, buses[\padCutoff],
        \padDetuneBus, buses[\padDetune],
        \rootFreqBus, buses[\rootFreq],
        \baseSubAmp, layerAmps[0],
        \basePadAmp, layerAmps[1],
        \baseShimmerAmp, layerAmps[2],
        \baseNoiseAmp, layerAmps[3],
        \baseFreq, rootFreq
    ], synths[\analysis], \addAfter);

    // Drone layers - map to control buses
    synths[\sub] = Synth(\chroma_sub, [
        \freq, rootFreq,
        \amp, layerAmps[0]
    ], synths[\blend], \addAfter);
    synths[\sub].map(\amp, buses[\subAmp]);
    synths[\sub].map(\freq, buses[\rootFreq]);

    synths[\pad] = Synth(\chroma_pad, [
        \freq, rootFreq,
        \amp, layerAmps[1],
        \cutoff, 2000,
        \detune, 0.01
    ], synths[\sub], \addAfter);
    synths[\pad].map(\amp, buses[\padAmp]);
    synths[\pad].map(\freq, buses[\rootFreq]);
    synths[\pad].map(\cutoff, buses[\padCutoff]);
    synths[\pad].map(\detune, buses[\padDetune]);

    synths[\shimmer] = Synth(\chroma_shimmer, [
        \freq, rootFreq,
        \amp, layerAmps[2]
    ], synths[\pad], \addAfter);
    synths[\shimmer].map(\amp, buses[\shimmerAmp]);
    synths[\shimmer].map(\freq, buses[\rootFreq]);

    synths[\noise] = Synth(\chroma_noise, [
        \amp, layerAmps[3],
        \cutoff, 4000
    ], synths[\shimmer], \addAfter);
    synths[\noise].map(\amp, buses[\noiseAmp]);
}
```

**Step 6: Add blend mode methods**

```supercollider
blendModeIndex {
    ^[\mirror, \complement, \transform].indexOf(blendMode) ?? 0;
}

setBlendMode { |mode|
    blendMode = mode;
    if(synths[\blend].notNil) {
        synths[\blend].set(\mode, this.blendModeIndex);
    };
}

setLayerAmp { |layer, amp|
    var index = [\sub, \pad, \shimmer, \noise].indexOf(layer);
    if(index.notNil) {
        layerAmps[index] = amp;
        if(synths[\blend].notNil) {
            synths[\blend].set(
                [\baseSubAmp, \basePadAmp, \baseShimmerAmp, \baseNoiseAmp][index],
                amp
            );
        };
    };
}

setRootNote { |midiNote|
    config[\rootNote] = midiNote;
    if(synths[\blend].notNil) {
        synths[\blend].set(\baseFreq, midiNote.midicps);
    };
}
```

**Step 7: Verify blend modes work**

Run in SuperCollider IDE:
```supercollider
Chroma.stop;
Chroma.start;

// Test blend mode switching
Chroma.instance.setBlendMode(\mirror);
// Make sound into mic - drone should react

Chroma.instance.setBlendMode(\complement);
// Drone should react inversely

Chroma.instance.setBlendMode(\transform);
// Drone should transpose with brightness
```

Expected: Each blend mode produces distinctly different reactive behavior.

**Step 8: Commit**

```bash
git add Chroma.sc
git commit -m "feat: add blend mode control and drone orchestration"
```

---

## Task 5: Dry/Wet Mix and Output Control

**Files:**
- Modify: `Chroma.sc`

**Step 1: Add output mixer SynthDef**

```supercollider
loadOutputSynthDef {
    SynthDef(\chroma_output, { |inBus, droneLevel=0.8, dryWet=0.5, outBus=0|
        var dry, wet, sig;
        dry = In.ar(inBus);
        wet = In.ar(0, 2);  // Drone layers output to main bus
        sig = (dry ! 2 * (1 - dryWet)) + (wet * dryWet * droneLevel);
        ReplaceOut.ar(outBus, sig);
    }).add;
}
```

**Step 2: Add instance variables**

```supercollider
var <droneLevel;
var <dryWet;
```

**Step 3: Initialize in init method**

```supercollider
droneLevel = 0.8;
dryWet = 0.5;
```

**Step 4: Update loadSynthDefs**

```supercollider
loadSynthDefs {
    this.loadInputSynthDef;
    this.loadAnalysisSynthDef;
    this.loadBlendControlSynthDef;
    this.loadSubSynthDef;
    this.loadPadSynthDef;
    this.loadShimmerSynthDef;
    this.loadNoiseSynthDef;
    this.loadOutputSynthDef;
}
```

**Step 5: Update createSynths to add output mixer**

Add at end of `createSynths`:

```supercollider
// Output mixer (at tail)
synths[\output] = Synth(\chroma_output, [
    \inBus, buses[\inputAudio],
    \droneLevel, droneLevel,
    \dryWet, dryWet
], nil, \addToTail);
```

**Step 6: Add control methods**

```supercollider
setDroneLevel { |level|
    droneLevel = level.clip(0, 1);
    if(synths[\output].notNil) {
        synths[\output].set(\droneLevel, droneLevel);
    };
}

setDryWet { |mix|
    dryWet = mix.clip(0, 1);
    if(synths[\output].notNil) {
        synths[\output].set(\dryWet, dryWet);
    };
}

setInputGain { |gain|
    if(synths[\input].notNil) {
        synths[\input].set(\gain, gain);
    };
}
```

**Step 7: Verify output control**

```supercollider
Chroma.stop;
Chroma.start;

// Test dry/wet
Chroma.instance.setDryWet(0);  // All dry
Chroma.instance.setDryWet(1);  // All wet
Chroma.instance.setDryWet(0.5);  // Mixed

// Test drone level
Chroma.instance.setDroneLevel(0.2);  // Quiet drone
Chroma.instance.setDroneLevel(1);    // Full drone
```

Expected: Smooth transitions between dry/wet and drone level changes.

**Step 8: Commit**

```bash
git add Chroma.sc
git commit -m "feat: add dry/wet mix and output level control"
```

---

## Task 6: GUI Dashboard

**Files:**
- Modify: `Chroma.sc`

**Step 1: Implement buildGUI method**

Replace the placeholder `buildGUI`:

```supercollider
buildGUI {
    var width = 600, height = 500;
    var spectrumViews, updateRoutine;
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

    // Spectrum displays
    window.view.decorator.nextLine;
    StaticText(window, 280@20).string_("INPUT SPECTRUM").align_(\center);
    StaticText(window, 280@20).string_("BLEND MODE").align_(\center);

    window.view.decorator.nextLine;

    // Input spectrum view
    spectrumViews = ();
    spectrumViews[\input] = UserView(window, 280@100)
        .background_(Color.gray(0.2))
        .drawFunc_({ |view|
            var bounds = view.bounds;
            var barWidth = bounds.width / config[\numBands];
            Pen.fillColor = Color.new255(100, 149, 237);  // Cornflower blue
            config[\numBands].do { |i|
                var h = bandData[i] * bounds.height;
                Pen.fillRect(Rect(i * barWidth + 2, bounds.height - h, barWidth - 4, h));
            };
        });

    // Blend mode buttons
    View(window, 280@100).layout_(
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

    // Store button refs for highlighting
    window.view.children.last.children[0].children.do { |btn, i|
        [\mirrorBtn, \complementBtn, \transformBtn][i].envirPut(btn);
    };

    window.view.decorator.nextLine;

    // Controls section
    StaticText(window, 280@20).string_("INPUT").font_(Font("Helvetica", 14, true));
    StaticText(window, 280@20).string_("DRONE").font_(Font("Helvetica", 14, true));

    window.view.decorator.nextLine;

    // Input controls
    View(window, 280@150).layout_(
        VLayout(
            EZSlider(nil, 260@20, "Gain", [0, 2].asSpec, { |ez|
                this.setInputGain(ez.value)
            }, 1, layout: \horz),
            EZSlider(nil, 260@20, "Smoothing", [0.01, 0.5].asSpec, { |ez|
                config[\smoothing] = ez.value;
                if(synths[\analysis].notNil) {
                    synths[\analysis].set(\smoothing, ez.value);
                };
            }, config[\smoothing], layout: \horz),
            StaticText().string_("Bands: " ++ config[\numBands])
        ).spacing_(10).margins_(5)
    );

    // Drone controls
    View(window, 280@150).layout_(
        VLayout(
            EZSlider(nil, 260@20, "Root", [24, 60, \lin, 1].asSpec, { |ez|
                this.setRootNote(ez.value.asInteger);
            }, config[\rootNote], layout: \horz),
            EZSlider(nil, 260@20, "Dry/Wet", \unipolar.asSpec, { |ez|
                this.setDryWet(ez.value)
            }, dryWet, layout: \horz),
            EZSlider(nil, 260@20, "Drone", \unipolar.asSpec, { |ez|
                this.setDroneLevel(ez.value)
            }, droneLevel, layout: \horz)
        ).spacing_(10).margins_(5)
    );

    window.view.decorator.nextLine;

    // Layer mix
    StaticText(window, (width - 20)@20).string_("LAYER MIX").font_(Font("Helvetica", 14, true));
    window.view.decorator.nextLine;

    View(window, (width - 20)@30).layout_(
        HLayout(
            EZSlider(nil, 130@20, "Sub", \unipolar.asSpec, { |ez|
                this.setLayerAmp(\sub, ez.value)
            }, layerAmps[0], layout: \horz),
            EZSlider(nil, 130@20, "Pad", \unipolar.asSpec, { |ez|
                this.setLayerAmp(\pad, ez.value)
            }, layerAmps[1], layout: \horz),
            EZSlider(nil, 130@20, "Shimmer", \unipolar.asSpec, { |ez|
                this.setLayerAmp(\shimmer, ez.value)
            }, layerAmps[2], layout: \horz),
            EZSlider(nil, 130@20, "Noise", \unipolar.asSpec, { |ez|
                this.setLayerAmp(\noise, ez.value)
            }, layerAmps[3], layout: \horz)
        ).spacing_(5)
    );

    // Spectrum update routine
    updateRoutine = Routine({
        loop {
            buses[\bands].getn(config[\numBands], { |vals|
                bandData = vals;
                { spectrumViews[\input].refresh }.defer;
            });
            0.033.wait;  // ~30fps
        }
    }).play(AppClock);

    window.onClose = window.onClose.addFunc({
        updateRoutine.stop;
    });
}

updateBlendButtons {
    var buttons = [\mirrorBtn, \complementBtn, \transformBtn];
    var modes = [\mirror, \complement, \transform];
    buttons.do { |key, i|
        var btn = key.envirGet;
        if(btn.notNil) {
            if(modes[i] == blendMode) {
                btn.states_([[btn.states[0][0], Color.black, Color.green]]);
            } {
                btn.states_([[btn.states[0][0], Color.black, Color.white]]);
            };
        };
    };
}
```

**Step 2: Verify GUI works**

```supercollider
Chroma.stop;
Chroma.start;
```

Expected: GUI window appears with:
- Input spectrum visualization updating in real-time
- Blend mode buttons (Mirror highlighted)
- Input gain and smoothing sliders
- Drone controls (root, dry/wet, level)
- Layer mix sliders

**Step 3: Commit**

```bash
git add Chroma.sc
git commit -m "feat: add GUI dashboard with spectrum display and controls"
```

---

## Task 7: Startup Script and Documentation

**Files:**
- Create: `startup.scd`
- Create: `README.md`

**Step 1: Create startup script**

```supercollider
// startup.scd - Chroma startup script
// Run this file to launch Chroma

(
s.options.sampleRate = 48000;
s.options.blockSize = 64;
s.options.memSize = 8192 * 4;

s.waitForBoot {
    "Loading Chroma...".postln;

    // Load the Chroma class if not already in extensions
    // (thisProcess.nowExecutingPath.dirname +/+ "Chroma.sc").load;

    Chroma.start;
};
)
```

**Step 2: Create README**

```markdown
# Chroma

Spectral-reactive drone synthesizer for SuperCollider.

## Overview

Chroma analyzes the spectral content of incoming audio and uses that analysis to shape evolving drone and pad textures. Three blend modes define different relationships between input spectrum and output sound.

## Requirements

- SuperCollider 3.10+
- Audio interface with input

## Installation

1. Copy `Chroma.sc` to your SuperCollider Extensions folder:
   - macOS: `~/Library/Application Support/SuperCollider/Extensions/`
   - Linux: `~/.local/share/SuperCollider/Extensions/`
   - Windows: `%LOCALAPPDATA%\SuperCollider\Extensions\`

2. Recompile class library: `Language > Recompile Class Library` or Cmd+Shift+L

## Usage

### Quick Start

```supercollider
Chroma.start;  // Launch with default settings
Chroma.stop;   // Stop and cleanup
```

### Or run the startup script

```supercollider
"path/to/startup.scd".load;
```

## Controls

### Blend Modes

- **Mirror**: Input spectrum directly shapes drone (loud bass = loud sub layer)
- **Complement**: Inverted relationship (loud bass = quiet sub layer)
- **Transform**: Spectral features map creatively (brightness shifts pitch)

### Parameters

| Control | Range | Description |
|---------|-------|-------------|
| Gain | 0-2 | Input amplification |
| Smoothing | 0.01-0.5s | Analysis response time |
| Root | C1-C4 (MIDI 24-60) | Drone root pitch |
| Dry/Wet | 0-1 | Input vs. drone balance |
| Drone | 0-1 | Overall drone level |
| Sub/Pad/Shimmer/Noise | 0-1 | Layer mix |

## Configuration

```supercollider
// Access running instance
Chroma.instance.setBlendMode(\transform);
Chroma.instance.setRootNote(48);  // C3
Chroma.instance.setDryWet(0.7);
```

## Architecture

```
Audio Input → FFT Analysis → Feature Extraction → Control Buses
                                                       ↓
                                      Drone Layers (Sub/Pad/Shimmer/Noise)
                                                       ↓
                                                 Output Mixer
```

## License

MIT
```

**Step 3: Verify startup script works**

Open SuperCollider IDE, open `startup.scd`, and run it.

Expected: Server boots, Chroma loads, GUI appears.

**Step 4: Commit**

```bash
git add startup.scd README.md
git commit -m "docs: add startup script and README"
```

---

## Task 8: Final Integration Testing

**Files:**
- None (testing only)

**Step 1: Full system test checklist**

Run through this checklist manually:

1. [ ] Server boots without errors
2. [ ] All synths appear in node tree (`s.queryAllNodes`)
3. [ ] Input spectrum visualization responds to audio
4. [ ] All three blend modes produce different behaviors
5. [ ] Dry/wet smoothly transitions
6. [ ] Root pitch changes affect all layers
7. [ ] Each layer slider affects its respective layer
8. [ ] Closing window stops Chroma cleanly
9. [ ] Cmd+. (stop all) doesn't leave orphan synths
10. [ ] `Chroma.stop` frees all resources

**Step 2: Document any issues found**

If issues found, create bug fix commits for each.

**Step 3: Final commit**

```bash
git add -A
git commit -m "chore: complete Chroma implementation"
```

---

## Summary

| Task | Component | Files |
|------|-----------|-------|
| 1 | Project structure + Input stage | `Chroma.sc` |
| 2 | Spectral analysis | `Chroma.sc` |
| 3 | Drone layer SynthDefs | `Chroma.sc` |
| 4 | Blend mode control | `Chroma.sc` |
| 5 | Output mixer | `Chroma.sc` |
| 6 | GUI dashboard | `Chroma.sc` |
| 7 | Startup + docs | `startup.scd`, `README.md` |
| 8 | Integration testing | (none) |
