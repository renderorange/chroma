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
    }

    loadSynthDefs {
        this.loadInputSynthDef;
        this.loadAnalysisSynthDef;
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
        synths[\input] = Synth(\chroma_input, [
            \inChannel, config[\inputChannel],
            \gain, 1,
            \outBus, buses[\inputAudio],
            \ampBus, buses[\inputAmp]
        ]);

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
