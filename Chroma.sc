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
}
