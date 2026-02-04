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
    var <inputFreezeBuffer;
    var <inputFrozen;
    var <inputFreezeLength;
    var <filterParams;
    var <overdriveParams;
    var <granularParams;
    var <reverbDelayParams;
    var <inputGain;

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
        inputFrozen = false;
        inputFreezeLength = 0.1;
        inputGain = 1.0;
        filterParams = (
            amount: 0.5,
            cutoff: 2000,
            resonance: 0.3
        );
        overdriveParams = (
            drive: 0.5,
            tone: 0.7,
            mix: 0.0
        );
        granularParams = (
            density: 20,
            size: 0.15,
            pitchScatter: 0.2,
            posScatter: 0.3,
            mix: 0.5
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
            this.setupOSC;
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
        buses[\overdriveAudio] = Bus.audio(server, 1);
        buses[\granularAudio] = Bus.audio(server, 1);
        buses[\reverbAudio] = Bus.audio(server, 1);
        buses[\delayAudio] = Bus.audio(server, 1);

        // Effect control buses
        buses[\filterGains] = Bus.control(server, 8);
        buses[\overdriveCtrl] = Bus.control(server, 2);  // drive, tone
        buses[\granularCtrl] = Bus.control(server, 4);  // density, size, pitchScatter, posScatter
        buses[\reverbDelayCtrl] = Bus.control(server, 4);  // blend, decay, modRate, modDepth

        // Grain buffers (2 seconds at server sample rate)
        grainBuffer = Buffer.alloc(server, server.sampleRate * 2, 1);
        freezeBuffer = Buffer.alloc(server, server.sampleRate * 2, 1);

        // Input freeze buffer (0.5 seconds max loop length)
        inputFreezeBuffer = Buffer.alloc(server, (server.sampleRate * 0.5).asInteger, 1);

        // Bus for frozen audio output
        buses[\frozenAudio] = Bus.audio(server, 1);
    }

    loadSynthDefs {
        this.loadInputSynthDef;
        this.loadInputFreezeSynthDef;
        this.loadAnalysisSynthDef;
        this.loadBlendControlSynthDef;
        this.loadFilterSynthDef;
        this.loadOverdriveSynthDef;
        this.loadGranularSynthDef;
        this.loadShimmerReverbSynthDef;
        this.loadModDelaySynthDef;
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

    loadInputFreezeSynthDef {
        SynthDef(\chroma_input_freeze, { |inBus, outBus, freezeBuf, freeze=0, loopLength=0.1|
            var sig, bufFrames, maxLoopFrames, loopFrames;
            var writePos, readPos1, readPos2;
            var env1, env2, out1, out2, frozen;

            sig = In.ar(inBus);
            bufFrames = BufFrames.kr(freezeBuf);
            maxLoopFrames = bufFrames;
            loopFrames = (loopLength * SampleRate.ir).clip(1, maxLoopFrames);

            // Write position - continuously writes when not frozen
            writePos = Phasor.ar(0, 1 - freeze, 0, bufFrames);
            BufWr.ar(sig, freezeBuf, writePos);

            // Two read positions for crossfade looping
            // When frozen, loop through loopFrames with crossfade
            readPos1 = Phasor.ar(0, freeze, 0, loopFrames);
            readPos2 = (readPos1 + (loopFrames * 0.5)).wrap(0, loopFrames);

            // Crossfade envelopes - triangle waves offset by half period
            env1 = (readPos1 / loopFrames * 2).fold(0, 1);
            env2 = (readPos2 / loopFrames * 2).fold(0, 1);

            // Read from buffer
            out1 = BufRd.ar(1, freezeBuf, readPos1, interpolation: 4) * env1;
            out2 = BufRd.ar(1, freezeBuf, readPos2, interpolation: 4) * env2;

            // Combine crossfaded outputs when frozen, otherwise pass through
            frozen = (out1 + out2) * 2;  // *2 to compensate for crossfade amplitude
            sig = Select.ar(freeze, [sig, frozen]);

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
            filterGainsBus, overdriveCtrlBus, granularCtrlBus, reverbDelayCtrlBus,
            baseFilterAmount=0.5, baseOverdriveDrive=0.5, baseOverdriveTone=0.7,
            baseGrainDensity=10, baseGrainSize=0.1,
            basePitchScatter=0.1, basePosScatter=0.2,
            baseReverbDelayBlend=0.5, baseDecayTime=3, baseModRate=0.5, baseModDepth=0.3|

            var bands, centroid, spread, flatness;
            var filterGains, overdriveDrive, overdriveTone;
            var grainDensity, grainSize, pitchScatter, posScatter;
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

            // Overdrive modulation: drive reacts to spectral energy, tone to centroid
            overdriveDrive = Select.kr(mode, [
                // Mirror: bright input = more drive
                centroid.linlin(0, 1, baseOverdriveDrive * 0.5, baseOverdriveDrive * 1.5),
                // Complement: dark input = more drive
                (1 - centroid).linlin(0, 1, baseOverdriveDrive * 0.5, baseOverdriveDrive * 1.5),
                // Transform: flatness affects drive
                flatness.linlin(0, 1, baseOverdriveDrive * 0.3, baseOverdriveDrive * 1.8)
            ]);

            overdriveTone = Select.kr(mode, [
                // Mirror: bright input = brighter tone
                centroid.linlin(0, 1, baseOverdriveTone * 0.8, baseOverdriveTone * 1.2),
                // Complement: dark input = brighter tone
                (1 - centroid).linlin(0, 1, baseOverdriveTone * 0.8, baseOverdriveTone * 1.2),
                // Transform: spread affects tone
                spread.linlin(0, 1, baseOverdriveTone * 0.7, baseOverdriveTone * 1.3)
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
            Out.kr(overdriveCtrlBus, [overdriveDrive, overdriveTone].clip(0, 1).lag(0.1));
            Out.kr(granularCtrlBus, [grainDensity, grainSize, pitchScatter, posScatter].lag(0.1));
            Out.kr(reverbDelayCtrlBus, [reverbDelayBlend, decayTime, modRate, modDepth].lag(0.1));
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

    loadOverdriveSynthDef {
        SynthDef(\chroma_overdrive, { |inBus, outBus, ctrlBus, drive=0.5, tone=0.7, mix=0.0|
            var sig, dry, wet, ctrl, modDrive, modTone;
            var preGain, postGain, cutoff;

            sig = In.ar(inBus);
            dry = sig;

            // Read modulated values from control bus
            ctrl = In.kr(ctrlBus, 2);
            modDrive = ctrl[0].lag(0.05);
            modTone = ctrl[1].lag(0.05);

            // Pre-gain based on drive (1x to 20x)
            preGain = modDrive.linexp(0, 1, 1, 20);

            // Soft clip with tanh
            wet = (sig * preGain).tanh;

            // Compensate output level
            postGain = modDrive.linlin(0, 1, 1, 0.5);
            wet = wet * postGain;

            // Tone control: lowpass filter (500Hz to 12kHz)
            cutoff = modTone.linexp(0, 1, 500, 12000);
            wet = LPF.ar(wet, cutoff);

            // Dry/wet mix
            sig = XFade2.ar(dry, wet, mix.linlin(0, 1, -1, 1));

            Out.ar(outBus, sig);
        }).add;
    }

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

    createSynths {
        // Input stage
        synths[\input] = Synth(\chroma_input, [
            \inChannel, config[\inputChannel],
            \gain, 1,
            \outBus, buses[\inputAudio],
            \ampBus, buses[\inputAmp]
        ]);

        // Input freeze (between input and analysis)
        synths[\inputFreeze] = Synth(\chroma_input_freeze, [
            \inBus, buses[\inputAudio],
            \outBus, buses[\frozenAudio],
            \freezeBuf, inputFreezeBuffer,
            \freeze, inputFrozen.asInteger,
            \loopLength, inputFreezeLength
        ], synths[\input], \addAfter);

        // Analysis (reads from frozen audio)
        synths[\analysis] = Synth(\chroma_analysis, [
            \inBus, buses[\frozenAudio],
            \fftBuf, fftBuffer,
            \numBands, config[\numBands],
            \smoothing, config[\smoothing],
            \bandsBus, buses[\bands],
            \centroidBus, buses[\centroid],
            \spreadBus, buses[\spread],
            \flatnessBus, buses[\flatness]
        ], synths[\inputFreeze], \addAfter);

        // Blend control
        synths[\blend] = Synth(\chroma_blend, [
            \mode, this.blendModeIndex,
            \bandsBus, buses[\bands],
            \centroidBus, buses[\centroid],
            \spreadBus, buses[\spread],
            \flatnessBus, buses[\flatness],
            \filterGainsBus, buses[\filterGains],
            \overdriveCtrlBus, buses[\overdriveCtrl],
            \granularCtrlBus, buses[\granularCtrl],
            \reverbDelayCtrlBus, buses[\reverbDelayCtrl],
            \baseFilterAmount, filterParams[\amount],
            \baseOverdriveDrive, overdriveParams[\drive],
            \baseOverdriveTone, overdriveParams[\tone],
            \baseGrainDensity, granularParams[\density],
            \baseGrainSize, granularParams[\size],
            \basePitchScatter, granularParams[\pitchScatter],
            \basePosScatter, granularParams[\posScatter],
            \baseReverbDelayBlend, reverbDelayParams[\blend],
            \baseDecayTime, reverbDelayParams[\decayTime],
            \baseModRate, reverbDelayParams[\modRate],
            \baseModDepth, reverbDelayParams[\modDepth]
        ], synths[\analysis], \addAfter);

        // Spectral filter (reads from frozen audio)
        synths[\filter] = Synth(\chroma_filter, [
            \inBus, buses[\frozenAudio],
            \outBus, buses[\filteredAudio],
            \gainsBus, buses[\filterGains],
            \amount, filterParams[\amount],
            \baseCutoff, filterParams[\cutoff],
            \resonance, filterParams[\resonance]
        ], synths[\blend], \addAfter);

        // Overdrive (reads from filtered audio)
        synths[\overdrive] = Synth(\chroma_overdrive, [
            \inBus, buses[\filteredAudio],
            \outBus, buses[\overdriveAudio],
            \ctrlBus, buses[\overdriveCtrl],
            \drive, overdriveParams[\drive],
            \tone, overdriveParams[\tone],
            \mix, overdriveParams[\mix]
        ], synths[\filter], \addAfter);

        // Granular (reads from overdrive audio)
        synths[\granular] = Synth(\chroma_granular, [
            \inBus, buses[\overdriveAudio],
            \outBus, buses[\granularAudio],
            \grainBuf, grainBuffer,
            \freezeBuf, freezeBuffer,
            \ctrlBus, buses[\granularCtrl],
            \freeze, frozen.asInteger,
            \mix, granularParams[\mix]
        ], synths[\overdrive], \addAfter);

        // Shimmer reverb (reads from overdrive audio)
        synths[\shimmerReverb] = Synth(\chroma_shimmer_reverb, [
            \inBus, buses[\overdriveAudio],
            \outBus, buses[\reverbAudio],
            \decayTime, reverbDelayParams[\decayTime],
            \shimmerPitch, reverbDelayParams[\shimmerPitch],
            \mix, reverbDelayParams[\mix]
        ], synths[\granular], \addAfter);

        // Modulated delay (reads from overdrive audio)
        synths[\modDelay] = Synth(\chroma_mod_delay, [
            \inBus, buses[\overdriveAudio],
            \outBus, buses[\delayAudio],
            \delayTime, reverbDelayParams[\delayTime],
            \decayTime, reverbDelayParams[\decayTime],
            \modRate, reverbDelayParams[\modRate],
            \modDepth, reverbDelayParams[\modDepth],
            \mix, reverbDelayParams[\mix]
        ], synths[\shimmerReverb], \addAfter);

        // Output mixer (at tail)
        synths[\output] = Synth(\chroma_output, [
            \inBus, buses[\overdriveAudio],
            \granularBus, buses[\granularAudio],
            \reverbBus, buses[\reverbAudio],
            \delayBus, buses[\delayAudio],
            \reverbDelayBlend, reverbDelayParams[\blend],
            \dryWet, dryWet
        ], nil, \addToTail);

        // Map control buses
        synths[\granular].map(\ctrlBus, buses[\granularCtrl]);
    }

    buildGUI {
        var width = 650, height = 720;
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
        View(window, 300@100).layout_(
            VLayout(
                HLayout(
                    StaticText().string_("Gain").fixedWidth_(60),
                    Slider().orientation_(\horizontal).value_(0.5).action_({ |sl|
                        this.setInputGain(sl.value * 2);
                    })
                ),
                HLayout(
                    StaticText().string_("Loop").fixedWidth_(60),
                    Slider().orientation_(\horizontal).value_(inputFreezeLength.linlin(0.05, 0.5, 0, 1)).action_({ |sl|
                        this.setInputFreezeLength(sl.value.linlin(0, 1, 0.05, 0.5));
                    })
                ),
                Button().states_([
                    ["INPUT FREEZE", Color.black, Color.white],
                    ["INPUT FREEZE", Color.white, Color.red]
                ]).action_({ |btn|
                    this.toggleInputFreeze;
                    btn.value = inputFrozen.asInteger;
                })
            ).margins_(5).spacing_(2)
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

        // Overdrive and Granular sections
        StaticText(window, 300@20).string_("OVERDRIVE").font_(Font("Helvetica", 12, true));
        StaticText(window, 300@20).string_("GRANULAR").font_(Font("Helvetica", 12, true));

        window.view.decorator.nextLine;

        // Overdrive controls
        View(window, 300@90).layout_(
            VLayout(
                HLayout(
                    StaticText().string_("Drive").fixedWidth_(60),
                    Slider().orientation_(\horizontal).value_(overdriveParams[\drive]).action_({ |sl|
                        this.setOverdriveDrive(sl.value);
                    })
                ),
                HLayout(
                    StaticText().string_("Tone").fixedWidth_(60),
                    Slider().orientation_(\horizontal).value_(overdriveParams[\tone]).action_({ |sl|
                        this.setOverdriveTone(sl.value);
                    })
                ),
                HLayout(
                    StaticText().string_("Mix").fixedWidth_(60),
                    Slider().orientation_(\horizontal).value_(overdriveParams[\mix]).action_({ |sl|
                        this.setOverdriveMix(sl.value);
                    })
                )
            ).margins_(5).spacing_(2)
        );

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
                    ["GRAIN FREEZE", Color.black, Color.white],
                    ["GRAIN FREEZE", Color.white, Color.green]
                ]).action_({ |btn|
                    this.toggleGranularFreeze;
                    btn.value = frozen.asInteger;
                })
            ).margins_(5).spacing_(2)
        );

        window.view.decorator.nextLine;

        // Reverb/Delay section
        StaticText(window, 300@20).string_("REVERB / DELAY").font_(Font("Helvetica", 12, true));

        window.view.decorator.nextLine;

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
        this.cleanupOSC;
        synths.do(_.free);
        buses.do(_.free);
        fftBuffer.free;
        grainBuffer.free;
        freezeBuffer.free;
        inputFreezeBuffer.free;
        if(window.notNil) { window.close };
        "Chroma stopped".postln;
    }

    setupOSC { |replyPort=9000|
        var replyAddr = NetAddr("127.0.0.1", replyPort);

        // Input controls
        OSCdef(\chromaGain, { |msg| this.setInputGain(msg[1]) }, '/chroma/gain');
        OSCdef(\chromaInputFreeze, { |msg|
            if(msg[1].asBoolean != inputFrozen) { this.toggleInputFreeze };
        }, '/chroma/inputFreeze');
        OSCdef(\chromaInputFreezeLength, { |msg| this.setInputFreezeLength(msg[1]) }, '/chroma/inputFreezeLength');

        // Filter controls
        OSCdef(\chromaFilterAmount, { |msg| this.setFilterAmount(msg[1]) }, '/chroma/filterAmount');
        OSCdef(\chromaFilterCutoff, { |msg| this.setFilterCutoff(msg[1]) }, '/chroma/filterCutoff');
        OSCdef(\chromaFilterResonance, { |msg| this.setFilterResonance(msg[1]) }, '/chroma/filterResonance');

        // Overdrive controls
        OSCdef(\chromaOverdriveDrive, { |msg| this.setOverdriveDrive(msg[1]) }, '/chroma/overdriveDrive');
        OSCdef(\chromaOverdriveTone, { |msg| this.setOverdriveTone(msg[1]) }, '/chroma/overdriveTone');
        OSCdef(\chromaOverdriveMix, { |msg| this.setOverdriveMix(msg[1]) }, '/chroma/overdriveMix');

        // Granular controls
        OSCdef(\chromaGranularDensity, { |msg| this.setGrainDensity(msg[1]) }, '/chroma/granularDensity');
        OSCdef(\chromaGranularSize, { |msg| this.setGrainSize(msg[1]) }, '/chroma/granularSize');
        OSCdef(\chromaGranularPitchScatter, { |msg| this.setGrainPitchScatter(msg[1]) }, '/chroma/granularPitchScatter');
        OSCdef(\chromaGranularPosScatter, { |msg| this.setGrainPosScatter(msg[1]) }, '/chroma/granularPosScatter');
        OSCdef(\chromaGranularMix, { |msg| this.setGranularMix(msg[1]) }, '/chroma/granularMix');
        OSCdef(\chromaGranularFreeze, { |msg|
            if(msg[1].asBoolean != frozen) { this.toggleGranularFreeze };
        }, '/chroma/granularFreeze');

        // Reverb/Delay controls
        OSCdef(\chromaReverbDelayBlend, { |msg| this.setReverbDelayBlend(msg[1]) }, '/chroma/reverbDelayBlend');
        OSCdef(\chromaDecayTime, { |msg| this.setDecayTime(msg[1]) }, '/chroma/decayTime');
        OSCdef(\chromaShimmerPitch, { |msg| this.setShimmerPitch(msg[1]) }, '/chroma/shimmerPitch');
        OSCdef(\chromaDelayTime, { |msg| this.setDelayTime(msg[1]) }, '/chroma/delayTime');
        OSCdef(\chromaModRate, { |msg| this.setModRate(msg[1]) }, '/chroma/modRate');
        OSCdef(\chromaModDepth, { |msg| this.setModDepth(msg[1]) }, '/chroma/modDepth');
        OSCdef(\chromaReverbDelayMix, { |msg| this.setReverbDelayMix(msg[1]) }, '/chroma/reverbDelayMix');

        // Global controls
        OSCdef(\chromaBlendMode, { |msg|
            var modes = [\mirror, \complement, \transform];
            this.setBlendMode(modes[msg[1].asInteger.clip(0, 2)]);
        }, '/chroma/blendMode');
        OSCdef(\chromaDryWet, { |msg| this.setDryWet(msg[1]) }, '/chroma/dryWet');

        // Sync request - send full state
        OSCdef(\chromaSync, { |msg|
            this.sendState(replyAddr);
        }, '/chroma/sync');

        "Chroma OSC responders ready".postln;
    }

    sendState { |addr|
        addr.sendMsg('/chroma/state',
            inputGain,
            inputFrozen.asInteger,
            inputFreezeLength,
            filterParams[\amount],
            filterParams[\cutoff],
            filterParams[\resonance],
            overdriveParams[\drive],
            overdriveParams[\tone],
            overdriveParams[\mix],
            granularParams[\density],
            granularParams[\size],
            granularParams[\pitchScatter],
            granularParams[\posScatter],
            granularParams[\mix],
            frozen.asInteger,
            reverbDelayParams[\blend],
            reverbDelayParams[\decayTime],
            reverbDelayParams[\shimmerPitch],
            reverbDelayParams[\delayTime],
            reverbDelayParams[\modRate],
            reverbDelayParams[\modDepth],
            reverbDelayParams[\mix],
            this.blendModeIndex,
            dryWet
        );
    }

    cleanupOSC {
        OSCdef(\chromaGain).free;
        OSCdef(\chromaInputFreeze).free;
        OSCdef(\chromaInputFreezeLength).free;
        OSCdef(\chromaFilterAmount).free;
        OSCdef(\chromaFilterCutoff).free;
        OSCdef(\chromaFilterResonance).free;
        OSCdef(\chromaOverdriveDrive).free;
        OSCdef(\chromaOverdriveTone).free;
        OSCdef(\chromaOverdriveMix).free;
        OSCdef(\chromaGranularDensity).free;
        OSCdef(\chromaGranularSize).free;
        OSCdef(\chromaGranularPitchScatter).free;
        OSCdef(\chromaGranularPosScatter).free;
        OSCdef(\chromaGranularMix).free;
        OSCdef(\chromaGranularFreeze).free;
        OSCdef(\chromaReverbDelayBlend).free;
        OSCdef(\chromaDecayTime).free;
        OSCdef(\chromaShimmerPitch).free;
        OSCdef(\chromaDelayTime).free;
        OSCdef(\chromaModRate).free;
        OSCdef(\chromaModDepth).free;
        OSCdef(\chromaReverbDelayMix).free;
        OSCdef(\chromaBlendMode).free;
        OSCdef(\chromaDryWet).free;
        OSCdef(\chromaSync).free;
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
        inputGain = gain;
        if(synths[\input].notNil) {
            synths[\input].set(\gain, gain);
        };
    }

    toggleInputFreeze {
        inputFrozen = inputFrozen.not;
        if(synths[\inputFreeze].notNil) {
            synths[\inputFreeze].set(\freeze, inputFrozen.asInteger);
        };
    }

    setInputFreezeLength { |val|
        inputFreezeLength = val.clip(0.05, 0.5);
        if(synths[\inputFreeze].notNil) {
            synths[\inputFreeze].set(\loopLength, inputFreezeLength);
        };
    }

    toggleGranularFreeze {
        frozen = frozen.not;
        if(frozen) {
            // Copy current grain buffer to freeze buffer
            grainBuffer.copyData(freezeBuffer);
        };
        if(synths[\granular].notNil) {
            synths[\granular].set(\freeze, frozen.asInteger);
        };
    }

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

    setOverdriveDrive { |val|
        overdriveParams[\drive] = val.clip(0, 1);
        if(synths[\overdrive].notNil) { synths[\overdrive].set(\drive, val) };
        if(synths[\blend].notNil) { synths[\blend].set(\baseOverdriveDrive, val) };
    }

    setOverdriveTone { |val|
        overdriveParams[\tone] = val.clip(0, 1);
        if(synths[\overdrive].notNil) { synths[\overdrive].set(\tone, val) };
        if(synths[\blend].notNil) { synths[\blend].set(\baseOverdriveTone, val) };
    }

    setOverdriveMix { |val|
        overdriveParams[\mix] = val.clip(0, 1);
        if(synths[\overdrive].notNil) { synths[\overdrive].set(\mix, val) };
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
}
