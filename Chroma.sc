Chroma {
    classvar <instance;

    var <server;
    var <config;
    var <fftBuffer;
    var <buses;
    var <synths;
    var <window;
    var <blendMode;
    var <dryWet;
    var <grainBuffer;
    var <freezeBuffer;
    var <frozen;
    var <filterParams;
    var <granularParams;
    var <reverbDelayParams;

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
        dryWet = 0.5;
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

        // Effect audio buses
        buses[\filteredAudio] = Bus.audio(server, 1);
        buses[\granularAudio] = Bus.audio(server, 1);
        buses[\reverbAudio] = Bus.audio(server, 1);
        buses[\delayAudio] = Bus.audio(server, 1);

        // Effect control buses
        buses[\filterGains] = Bus.control(server, 8);
        buses[\granularCtrl] = Bus.control(server, 4);  // density, size, pitchScatter, posScatter
        buses[\reverbDelayCtrl] = Bus.control(server, 4);  // blend, decay, modRate, modDepth

        // Grain buffers (2 seconds at server sample rate)
        grainBuffer = Buffer.alloc(server, server.sampleRate * 2, 1);
        freezeBuffer = Buffer.alloc(server, server.sampleRate * 2, 1);
    }

    loadSynthDefs {
        this.loadInputSynthDef;
        this.loadAnalysisSynthDef;
        this.loadBlendControlSynthDef;
        this.loadFilterSynthDef;
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
        // Placeholder - will be rewritten for effects
        SynthDef(\chroma_blend, { |mode=0|
            // Empty for now
        }).add;
    }

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

        // Blend control (commented out - will be restored in Task 4)
        // synths[\blend] = Synth(\chroma_blend, [
        //     \mode, this.blendModeIndex
        // ], synths[\analysis], \addAfter);

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
                HLayout(
                    [StaticText().string_("Gain").align_(\left), stretch: 0],
                    Slider().orientation_(\horizontal).value_(0.5).action_({ |sl|
                        this.setInputGain(sl.value * 2);  // 0-2 range
                    }),
                    [NumberBox().value_(1).decimals_(2).maxWidth_(50).action_({ |nb|
                        this.setInputGain(nb.value);
                    }), stretch: 0]
                ),
                HLayout(
                    [StaticText().string_("Smooth").align_(\left), stretch: 0],
                    Slider().orientation_(\horizontal).value_(config[\smoothing] * 2).action_({ |sl|
                        var val = sl.value * 0.5;  // 0-0.5 range
                        config[\smoothing] = val;
                        if(synths[\analysis].notNil) {
                            synths[\analysis].set(\smoothing, val);
                        };
                    }),
                    [NumberBox().value_(config[\smoothing]).decimals_(2).maxWidth_(50), stretch: 0]
                ),
                StaticText().string_("Bands: " ++ config[\numBands])
            ).spacing_(10).margins_(5)
        );

        // Drone controls
        View(window, 280@150).layout_(
            VLayout(
                HLayout(
                    [StaticText().string_("Root").align_(\left), stretch: 0],
                    Slider().orientation_(\horizontal).value_((config[\rootNote] - 24) / 36).action_({ |sl|
                        var midiNote = (sl.value * 36 + 24).round.asInteger;  // 24-60 range
                        this.setRootNote(midiNote);
                    }),
                    [NumberBox().value_(config[\rootNote]).decimals_(0).maxWidth_(50).action_({ |nb|
                        this.setRootNote(nb.value.asInteger);
                    }), stretch: 0]
                ),
                HLayout(
                    [StaticText().string_("Dry/Wet").align_(\left), stretch: 0],
                    Slider().orientation_(\horizontal).value_(dryWet).action_({ |sl|
                        this.setDryWet(sl.value);
                    }),
                    [NumberBox().value_(dryWet).decimals_(2).maxWidth_(50), stretch: 0]
                ),
                HLayout(
                    [StaticText().string_("Drone").align_(\left), stretch: 0],
                    Slider().orientation_(\horizontal).value_(droneLevel).action_({ |sl|
                        this.setDroneLevel(sl.value);
                    }),
                    [NumberBox().value_(droneLevel).decimals_(2).maxWidth_(50), stretch: 0]
                )
            ).spacing_(10).margins_(5)
        );

        window.view.decorator.nextLine;

        // Layer mix
        StaticText(window, (width - 20)@20).string_("LAYER MIX").font_(Font("Helvetica", 14, true));
        window.view.decorator.nextLine;

        View(window, (width - 20)@60).layout_(
            HLayout(
                VLayout(
                    StaticText().string_("Sub").align_(\center),
                    Slider().orientation_(\horizontal).value_(layerAmps[0]).action_({ |sl|
                        this.setLayerAmp(\sub, sl.value);
                    })
                ),
                VLayout(
                    StaticText().string_("Pad").align_(\center),
                    Slider().orientation_(\horizontal).value_(layerAmps[1]).action_({ |sl|
                        this.setLayerAmp(\pad, sl.value);
                    })
                ),
                VLayout(
                    StaticText().string_("Shimmer").align_(\center),
                    Slider().orientation_(\horizontal).value_(layerAmps[2]).action_({ |sl|
                        this.setLayerAmp(\shimmer, sl.value);
                    })
                ),
                VLayout(
                    StaticText().string_("Noise").align_(\center),
                    Slider().orientation_(\horizontal).value_(layerAmps[3]).action_({ |sl|
                        this.setLayerAmp(\noise, sl.value);
                    })
                )
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
        grainBuffer.free;
        freezeBuffer.free;
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
}
