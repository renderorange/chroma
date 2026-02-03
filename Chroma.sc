Chroma {
    classvar <instance;

    var <server;
    var <config;
    var <fftBuffer;
    var <buses;
    var <synths;
    var <window;
    var <blendMode;
    var <layerAmps;
    var <droneLevel;
    var <dryWet;

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
        blendMode = \mirror;
        layerAmps = [0.3, 0.3, 0.2, 0.1];  // sub, pad, shimmer, noise defaults
        droneLevel = 0.8;
        dryWet = 0.5;
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
            server.sync;  // Ensure buffer is allocated before SynthDefs use it
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

        // Layer control buses
        buses[\subAmp] = Bus.control(server, 1);
        buses[\padAmp] = Bus.control(server, 1);
        buses[\shimmerAmp] = Bus.control(server, 1);
        buses[\noiseAmp] = Bus.control(server, 1);
        buses[\padCutoff] = Bus.control(server, 1);
        buses[\padDetune] = Bus.control(server, 1);
        buses[\rootFreq] = Bus.control(server, 1);
    }

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

    loadInputSynthDef {
        SynthDef(\chroma_input, { |inChannel=0, gain=1, outBus, ampBus|
            var sig, amp;
            sig = SoundIn.ar(inChannel) * gain;
            amp = Amplitude.kr(sig, 0.01, 0.1);
            Out.kr(ampBus, amp);
            Out.ar(outBus, sig);
        }).add;
    }

    loadAnalysisSynthDef {
        SynthDef(\chroma_analysis, { |inBus, fftBuf, numBands=8, smoothing=0.1,
            bandsBus, centroidBus, spreadBus, flatnessBus|
            var sig, chain, centroid, spread, flatness;
            var bandMags, bandFiltered;
            // Band center frequencies and reciprocal Q values for 8 bands
            // sub, bass, low-mid, mid, upper-mid, presence, brilliance, air
            var bandCenters = [30, 120, 375, 1000, 3000, 5000, 9000, 16000];
            var bandRQs = [1, 1, 1, 1, 1, 1, 1, 1];

            sig = In.ar(inBus);
            chain = FFT(fftBuf, sig, hop: 0.5, wintype: 1);

            // Spectral features from FFT
            centroid = SpecCentroid.kr(chain);
            spread = SpecPcile.kr(chain, 0.9) - SpecPcile.kr(chain, 0.1);
            flatness = SpecFlatness.kr(chain);

            // Normalize centroid (0-20kHz -> 0-1)
            centroid = (centroid / 20000).clip(0, 1);
            // Normalize spread
            spread = (spread / 10000).clip(0, 1);
            // Flatness is already 0-1

            // Band magnitudes using bandpass filters with amplitude followers
            // More portable than FFTSubbandPower (which requires sc3-plugins)
            bandFiltered = BPF.ar(sig, bandCenters, bandRQs);
            bandMags = Amplitude.kr(bandFiltered, 0.01, 0.1);
            bandMags = bandMags.collect({ |mag|
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
                bands[0..1].sum.clip(0, 1) * baseSubAmp,           // Mirror: low bands
                (1 - bands[0..1].sum.clip(0, 1)) * baseSubAmp,    // Complement: inverted
                (1 - flatness) * baseSubAmp                        // Transform: tonality -> sub
            ]);

            padAmp = Select.kr(mode, [
                bands[2..4].sum.clip(0, 1) * basePadAmp,           // Mirror: mid bands
                (1 - bands[2..4].sum.clip(0, 1)) * basePadAmp,    // Complement
                centroid.linlin(0, 1, 0.5, 1) * basePadAmp         // Transform
            ]);

            shimmerAmp = Select.kr(mode, [
                bands[5..7].sum.clip(0, 1) * baseShimmerAmp,       // Mirror: high bands
                (1 - bands[5..7].sum.clip(0, 1)) * baseShimmerAmp, // Complement
                centroid * baseShimmerAmp                          // Transform: brightness -> shimmer
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

    loadNoiseSynthDef {
        SynthDef(\chroma_noise, { |out=0, amp=0.1, cutoff=4000, gate=1|
            var sig, env;
            env = EnvGen.kr(Env.asr(2, 1, 2), gate, doneAction: 2);
            sig = PinkNoise.ar;
            sig = BPF.ar(sig, cutoff, 0.5);
            Out.ar(out, sig * amp * env ! 2);
        }).add;
    }

    loadOutputSynthDef {
        SynthDef(\chroma_output, { |inBus, droneLevel=0.8, dryWet=0.5, outBus=0|
            var dry, wet, sig;
            dry = In.ar(inBus);
            wet = In.ar(0, 2);  // Drone layers output to main bus
            sig = (dry ! 2 * (1 - dryWet)) + (wet * dryWet * droneLevel);
            ReplaceOut.ar(outBus, sig);
        }).add;
    }

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

        // Output mixer (at tail)
        synths[\output] = Synth(\chroma_output, [
            \inBus, buses[\inputAudio],
            \droneLevel, droneLevel,
            \dryWet, dryWet
        ], nil, \addToTail);
    }

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
                }, 1, layout: \horz).view,
                EZSlider(nil, 260@20, "Smoothing", [0.01, 0.5].asSpec, { |ez|
                    config[\smoothing] = ez.value;
                    if(synths[\analysis].notNil) {
                        synths[\analysis].set(\smoothing, ez.value);
                    };
                }, config[\smoothing], layout: \horz).view,
                StaticText().string_("Bands: " ++ config[\numBands])
            ).spacing_(10).margins_(5)
        );

        // Drone controls
        View(window, 280@150).layout_(
            VLayout(
                EZSlider(nil, 260@20, "Root", [24, 60, \lin, 1].asSpec, { |ez|
                    this.setRootNote(ez.value.asInteger);
                }, config[\rootNote], layout: \horz).view,
                EZSlider(nil, 260@20, "Dry/Wet", \unipolar.asSpec, { |ez|
                    this.setDryWet(ez.value)
                }, dryWet, layout: \horz).view,
                EZSlider(nil, 260@20, "Drone", \unipolar.asSpec, { |ez|
                    this.setDroneLevel(ez.value)
                }, droneLevel, layout: \horz).view
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
                }, layerAmps[0], layout: \horz).view,
                EZSlider(nil, 130@20, "Pad", \unipolar.asSpec, { |ez|
                    this.setLayerAmp(\pad, ez.value)
                }, layerAmps[1], layout: \horz).view,
                EZSlider(nil, 130@20, "Shimmer", \unipolar.asSpec, { |ez|
                    this.setLayerAmp(\shimmer, ez.value)
                }, layerAmps[2], layout: \horz).view,
                EZSlider(nil, 130@20, "Noise", \unipolar.asSpec, { |ez|
                    this.setLayerAmp(\noise, ez.value)
                }, layerAmps[3], layout: \horz).view
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

    cleanup {
        synths.do(_.free);
        buses.do(_.free);
        fftBuffer.free;
        if(window.notNil) { window.close };
        "Chroma stopped".postln;
    }

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
}
