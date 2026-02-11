Chroma {
    classvar <instance;

    var <server;
    var <config;
    var <fftBuffer;
    var <buses;
    var <synths;
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
    var <reverbParams;
    var <delayParams;
    var <bitcrushParams;
    var <inputGain;
    var <>grainIntensity = \subtle;
    var <grainIntensityMultipliers;  // Dictionary for intensity multiplier constants
    var <effectsOrder;  // Array of effect symbols in processing order

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
        grainIntensityMultipliers = (
            subtle: 1.0,        // Normal granular effect intensity
            pronounced: 3.0,    // 3x intensity based on perceptual testing for pronounced grain effect
            extreme: 6.0         // 6x intensity for extreme granular effect
        );
        filterParams = (
            enabled: true,
            amount: 0.5,
            cutoff: 2000,
            resonance: 0.3
        );
        overdriveParams = (
            enabled: false,
            drive: 0.5,
            tone: 0.7,
            bias: 0.5,  // neutral bias (no DC offset)
            mix: 0.0
        );
        granularParams = (
            enabled: true,
            density: 20,
            size: 0.15,
            pitchScatter: 0.2,
            posScatter: 0.3,
            mix: 0.5
        );
        reverbParams = (
            enabled: false,
            decayTime: 3,
            mix: 0.3
        );
        delayParams = (
            enabled: false,
            delayTime: 0.3,
            decayTime: 3,
            modRate: 0.5,
            modDepth: 0.3,
            mix: 0.3
        );
        bitcrushParams = (
            enabled: false,
            bitDepth: 8,
            sampleRate: 11025,
            drive: 0.5,
            mix: 0.3
        );
        effectsOrder = [\filter, \overdrive, \bitcrush, \granular, \reverb, \delay];
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
            this.setupOSC;
            "Chroma ready".postln;
        };
    }

    setEffectsOrder { |newOrder|
        effectsOrder = newOrder;
        this.reconnectEffects();
    }

    getEffectsOrder {
        ^effectsOrder;
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
        buses[\bitcrushAudio] = Bus.audio(server, 1);
        buses[\granularAudio] = Bus.audio(server, 1);
        buses[\reverbAudio] = Bus.audio(server, 1);
        buses[\delayAudio] = Bus.audio(server, 1);

        // Effect control buses
        buses[\filterGains] = Bus.control(server, 8);
        buses[\overdriveCtrl] = Bus.control(server, 3);  // drive, tone, bias (was 2)
        buses[\granularCtrl] = Bus.control(server, 4);  // density, size, pitchScatter, posScatter

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
        this.loadBitcrushSynthDef;
        this.loadGranularSynthDef;
        this.loadReverbSynthDef;
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
            filterGainsBus, overdriveCtrlBus, granularCtrlBus,
            baseFilterAmount=0.5, baseOverdriveDrive=0.5, baseOverdriveTone=0.7, baseOverdriveBias=0.5,
            baseGrainDensity=10, baseGrainSize=0.1,
            basePitchScatter=0.1, basePosScatter=0.2,
            grainIntensityMultiplier=1.0|

            var bands, centroid, spread, flatness;
            var filterGains, overdriveDrive, overdriveTone, overdriveBias;
            var grainDensity, grainSize, pitchScatter, posScatter;
            var extremeFactor;

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

            overdriveBias = Select.kr(mode, [
                // Mirror: bright input = more bias
                centroid.linlin(0, 1, baseOverdriveBias * 0.8, baseOverdriveBias * 1.2),
                // Complement: dark input = more bias
                (1 - centroid).linlin(0, 1, baseOverdriveBias * 0.8, baseOverdriveBias * 1.2),
                // Transform: flatness affects bias
                flatness.linlin(0, 1, baseOverdriveBias * 0.7, baseOverdriveBias * 1.3)
            ]);

            // Granular parameters
            grainDensity = Select.kr(mode, [
                // Mirror: more energy = denser
                bands.sum.linlin(0, 4, baseGrainDensity * 0.5 * grainIntensityMultiplier, baseGrainDensity * 2 * grainIntensityMultiplier),
                // Complement: more energy = sparser
                bands.sum.linlin(0, 4, baseGrainDensity * 2 * grainIntensityMultiplier, baseGrainDensity * 0.5 * grainIntensityMultiplier),
                // Transform: flatness controls density
                flatness.linlin(0, 1, baseGrainDensity * 0.3 * grainIntensityMultiplier, baseGrainDensity * 3 * grainIntensityMultiplier)
            ]);

            grainSize = Select.kr(mode, [
                // Mirror: more energy = smaller grains
                bands.sum.linlin(0, 4, baseGrainSize * 2 * grainIntensityMultiplier, baseGrainSize * 0.5 * grainIntensityMultiplier),
                // Complement: more energy = larger grains
                bands.sum.linlin(0, 4, baseGrainSize * 0.5 * grainIntensityMultiplier, baseGrainSize * 2 * grainIntensityMultiplier),
                // Transform: spread controls size
                spread.linlin(0, 1, baseGrainSize * 0.5 * grainIntensityMultiplier, baseGrainSize * 2 * grainIntensityMultiplier)
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

            // Enhanced granular parameters for extreme mode
            // Use smooth scaling instead of hard threshold
            extremeFactor = grainIntensityMultiplier.linlin(1.0, 6.0, 1.0, 2.0).clip(1.0, 2.0);
            grainDensity = grainDensity * extremeFactor;  // Extra density for extreme
            grainSize = grainSize * grainIntensityMultiplier.linlin(1.0, 6.0, 1.0, 1.5).clip(1.0, 1.5);       // Larger size variation
            pitchScatter = pitchScatter * grainIntensityMultiplier.linlin(1.0, 6.0, 1.0, 1.33).clip(1.0, 1.33); // More pitch variation

            Out.kr(filterGainsBus, filterGains.lag(0.1));
            Out.kr(overdriveCtrlBus, [overdriveDrive, overdriveTone, overdriveBias].clip(0, 1).lag(0.1));
            Out.kr(granularCtrlBus, [grainDensity, grainSize, pitchScatter, posScatter].lag(0.1));
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
        SynthDef(\chroma_overdrive, { |inBus, outBus, ctrlBus, drive=0.5, tone=0.7, bias=0.5, mix=0.0|
            var sig, dry, wet, ctrl, modDrive, modTone, modBias, dcOffset;
            var preGain, postGain, cutoff;

            sig = In.ar(inBus);
            dry = sig;

            // Read control bus (now 3 channels)
            ctrl = In.kr(ctrlBus, 3);
            modDrive = ctrl[0].lag(0.05);
            modTone = ctrl[1].lag(0.05);
            modBias = ctrl[2].lag(0.05);

            // Convert internal bias (0-1) to DC offset (-1 to 1)
            dcOffset = (modBias - 0.5) * 2;

            // Apply DC offset BEFORE saturation for asymmetrical clipping
            sig = sig + dcOffset;

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

    loadBitcrushSynthDef {
        SynthDef(\chroma_bitcrush, { |inBus, outBus, bitDepth=8, sampleRate=11025, drive=0.5, mix=0.3|
            var sig, dry, wet, crushed, driven;
            var rateDivisor, quantization;

            sig = In.ar(inBus);
            dry = sig;

            // Sample rate reduction
            rateDivisor = SampleRate.ir / sampleRate;
            crushed = Latch.ar(sig, Impulse.ar(sampleRate));

            // Bit depth reduction
            quantization = (2.pow(bitDepth) - 1) / 2;
            crushed = (crushed * quantization).round / quantization;

            // Drive/distortion
            driven = (crushed * (1 + drive * 19)).tanh;
            driven = driven * (1 - drive * 0.3);  // Compensate gain

            // Mix dry and wet
            sig = XFade2.ar(dry, driven, mix.linlin(0, 1, -1, 1));

            Out.ar(outBus, sig);
        }).add;
    }

    loadReverbSynthDef {
        SynthDef(\chroma_reverb, { |inBus, outBus, decayTime=3, mix=0.3|
            var sig, dry, wet, verb;

            sig = In.ar(inBus);
            dry = sig;

            // Create reverb with diffusion network
            verb = sig;
            verb = verb + LocalIn.ar(1);

            // Diffusion via allpass chain
            4.do { |i|
                verb = AllpassC.ar(verb, 0.05, { Rand(0.01, 0.05) }.dup(1).sum, decayTime * 0.3);
            };

            LocalOut.ar(verb * (decayTime / 10).clip(0, 0.8));

            // Final reverb tail using FreeVerb
            wet = FreeVerb.ar(verb, 0.8, decayTime.linlin(0.5, 10, 0.5, 0.95), 0.5);

            // Mix dry and wet
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
            dryWet=0.5, outBus=0|

            var dry, granular, reverb, delay, wet, sig;

            dry = In.ar(inBus);
            granular = In.ar(granularBus);
            reverb = In.ar(reverbBus);
            delay = In.ar(delayBus);

            // Mix all effects (reverb and delay are already gated by their mix controls)
            wet = granular + reverb + delay;

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
            \baseFilterAmount, filterParams[\amount],
            \baseOverdriveDrive, overdriveParams[\drive],
            \baseOverdriveTone, overdriveParams[\tone],
            \baseOverdriveBias, overdriveParams[\bias],
            \baseGrainDensity, granularParams[\density],
            \baseGrainSize, granularParams[\size],
            \basePitchScatter, granularParams[\pitchScatter],
            \basePosScatter, granularParams[\posScatter],
            \grainIntensityMultiplier, grainIntensity == \pronounced ? 3.0 : (grainIntensity == \extreme ? 6.0 : 1.0)
        ], synths[\analysis], \addAfter);

        // Create all effects synths with initial connections (order-independent)
        synths[\filter] = Synth(\chroma_filter, [
            \inBus, buses[\filteredAudio],
            \outBus, buses[\filteredAudio],
            \gainsBus, buses[\filterGains],
            \amount, if(filterParams[\enabled], { filterParams[\amount] }, { 0.0 }),
            \baseCutoff, filterParams[\cutoff],
            \resonance, filterParams[\resonance]
        ], synths[\blend], \addAfter);

        synths[\overdrive] = Synth(\chroma_overdrive, [
            \inBus, buses[\overdriveAudio], 
            \outBus, buses[\overdriveAudio],
            \ctrlBus, buses[\overdriveCtrl],
            \drive, overdriveParams[\drive],
            \tone, overdriveParams[\tone],
            \bias, overdriveParams[\bias],
            \mix, if(overdriveParams[\enabled], { overdriveParams[\mix] }, { 0.0 })
        ], synths[\blend], \addAfter);

        synths[\bitcrush] = Synth(\chroma_bitcrush, [
            \inBus, buses[\bitcrushAudio],
            \outBus, buses[\bitcrushAudio],
            \bitDepth, bitcrushParams[\bitDepth],
            \sampleRate, bitcrushParams[\sampleRate],
            \drive, bitcrushParams[\drive],
            \mix, if(bitcrushParams[\enabled], { bitcrushParams[\mix] }, { 0.0 })
        ], synths[\blend], \addAfter);

        synths[\granular] = Synth(\chroma_granular, [
            \inBus, buses[\granularAudio],
            \outBus, buses[\granularAudio],
            \grainBuf, grainBuffer,
            \freezeBuf, freezeBuffer,
            \ctrlBus, buses[\granularCtrl],
            \freeze, frozen.asInteger,
            \mix, if(granularParams[\enabled], { granularParams[\mix] }, { 0.0 })
        ], synths[\blend], \addAfter);

        synths[\reverb] = Synth(\chroma_reverb, [
            \inBus, buses[\reverbAudio],
            \outBus, buses[\reverbAudio],
            \decayTime, reverbParams[\decayTime],
            \mix, if(reverbParams[\enabled], { reverbParams[\mix] }, { 0.0 })
        ], synths[\blend], \addAfter);

        synths[\modDelay] = Synth(\chroma_mod_delay, [
            \inBus, buses[\delayAudio],
            \outBus, buses[\delayAudio],
            \delayTime, delayParams[\delayTime],
            \decayTime, delayParams[\decayTime],
            \modRate, delayParams[\modRate],
            \modDepth, delayParams[\modDepth],
            \mix, if(delayParams[\enabled], { delayParams[\mix] }, { 0.0 })
        ], synths[\blend], \addAfter);

        // Output mixer (at tail)
        synths[\output] = Synth(\chroma_output, [
            \inBus, buses[\overdriveAudio],
            \granularBus, buses[\granularAudio],
            \reverbBus, buses[\reverbAudio],
            \delayBus, buses[\delayAudio],
            \dryWet, dryWet
        ], nil, \addToTail);

        // Map control buses
        synths[\granular].map(\ctrlBus, buses[\granularCtrl]);
        
        // Apply current effects order
        this.reconnectEffects();
    }

    reconnectEffects {
        var prevEffect = \blend;
        var lastEffect, lastBus;
        
        // Connect effects in order
        effectsOrder.do { |effectName, i|
            var synth = synths[effectName];
            if (synth.notNil) {
                // Move synth to follow previous effect
                synth.moveAfter(synths[prevEffect]);
                
                // Update synth input/output buses based on position
                if (i == 0) {
                    // First effect reads from filtered audio bus
                    synth.set(\inBus, buses[\filteredAudio]);
                } {
                    // Subsequent effects read from previous effect
                    var prevBus = this.getEffectOutputBus(effectsOrder[i-1]);
                    synth.set(\inBus, prevBus);
                };
                
                prevEffect = effectName;
            }
        };
        
        // Ensure output synth reads from last effect correctly
        lastEffect = effectsOrder.last;
        lastBus = this.getEffectOutputBus(lastEffect);
        synths[\output].set(\inBus, lastBus);
    }

    getEffectOutputBus { |effectName|
        // Return the output bus for each effect type
        switch (effectName,
            \filter, { buses[\filteredAudio] },
            \overdrive, { buses[\overdriveAudio] },
            \bitcrush, { buses[\bitcrushAudio] },
            \granular, { buses[\granularAudio] },
            \reverb, { buses[\reverbAudio] },
            \delay, { buses[\delayAudio] },
            { buses[\filteredAudio] } // fallback
        );
    }

    cleanup {

        this.cleanupOSC;
        synths.do(_.free);
        buses.do(_.free);
        fftBuffer.free;
        grainBuffer.free;
        freezeBuffer.free;
        inputFreezeBuffer.free;
        
        "Chroma stopped".postln;
    }

    setupOSC { |replyPort=9000|
        var replyAddr = NetAddr("127.0.0.1", replyPort);

        // Input controls
        OSCdef(\chromaGain, { |msg|
            this.setInputGain(msg[1]);

        }, '/chroma/gain');
        OSCdef(\chromaInputFreeze, { |msg|
            if(msg[1].asBoolean != inputFrozen) { this.toggleInputFreeze };

        }, '/chroma/inputFreeze');
        OSCdef(\chromaInputFreezeLength, { |msg|
            this.setInputFreezeLength(msg[1]);

        }, '/chroma/inputFreezeLength');

        // Filter controls
        OSCdef(\chromaFilterEnabled, { |msg|
            this.setFilterEnabled(msg[1].asBoolean);

        }, '/chroma/filterEnabled');
        OSCdef(\chromaFilterAmount, { |msg|
            this.setFilterAmount(msg[1]);

        }, '/chroma/filterAmount');
        OSCdef(\chromaFilterCutoff, { |msg|
            this.setFilterCutoff(msg[1]);

        }, '/chroma/filterCutoff');
        OSCdef(\chromaFilterResonance, { |msg|
            this.setFilterResonance(msg[1]);

        }, '/chroma/filterResonance');

        // Overdrive controls
        OSCdef(\chromaOverdriveEnabled, { |msg|
            this.setOverdriveEnabled(msg[1].asBoolean);

        }, '/chroma/overdriveEnabled');
        OSCdef(\chromaOverdriveDrive, { |msg|
            this.setOverdriveDrive(msg[1]);

        }, '/chroma/overdriveDrive');
        OSCdef(\chromaOverdriveTone, { |msg|
            this.setOverdriveTone(msg[1]);

        }, '/chroma/overdriveTone');
        OSCdef(\chromaOverdriveMix, { |msg|
            this.setOverdriveMix(msg[1]);

        }, '/chroma/overdriveMix');
        OSCdef(\chromaOverdriveBias, { |msg|
            if(msg.size >= 2 && msg[1].isNumber) {
                this.setOverdriveBias(msg[1]);
            } {
                "Invalid overdriveBias message: %".format(msg).warn;
            }
        }, '/chroma/overdriveBias');

        // Granular controls
        OSCdef(\chromaGranularEnabled, { |msg|
            this.setGranularEnabled(msg[1].asBoolean);

        }, '/chroma/granularEnabled');
        OSCdef(\chromaGranularDensity, { |msg|
            this.setGrainDensity(msg[1]);

        }, '/chroma/granularDensity');
        OSCdef(\chromaGranularSize, { |msg|
            this.setGrainSize(msg[1]);

        }, '/chroma/granularSize');
        OSCdef(\chromaGranularPitchScatter, { |msg|
            this.setGrainPitchScatter(msg[1]);

        }, '/chroma/granularPitchScatter');
        OSCdef(\chromaGranularPosScatter, { |msg|
            this.setGrainPosScatter(msg[1]);

        }, '/chroma/granularPosScatter');
        OSCdef(\chromaGranularMix, { |msg|
            this.setGranularMix(msg[1]);

        }, '/chroma/granularMix');
        OSCdef(\chromaGranularFreeze, { |msg|
            if(msg[1].asBoolean != frozen) { this.toggleGranularFreeze };

        }, '/chroma/granularFreeze');
        OSCdef(\chromaGrainIntensity, { |msg|
            this.setGrainIntensity(msg[1].asSymbol);

        }, '/chroma/grainIntensity');

        // Bitcrushing controls
        OSCdef(\chromaBitcrushEnabled, { |msg|
            this.setBitcrushEnabled(msg[1].asBoolean);

        }, '/chroma/bitcrushEnabled');
        OSCdef(\chromaBitDepth, { |msg|
            this.setBitDepth(msg[1]);

        }, '/chroma/bitDepth');
        OSCdef(\chromaBitcrushSampleRate, { |msg|
            this.setBitcrushSampleRate(msg[1]);

        }, '/chroma/bitcrushSampleRate');
        OSCdef(\chromaBitcrushDrive, { |msg|
            this.setBitcrushDrive(msg[1]);

        }, '/chroma/bitcrushDrive');
        OSCdef(\chromaBitcrushMix, { |msg|
            this.setBitcrushMix(msg[1]);

        }, '/chroma/bitcrushMix');

        // Reverb controls
        OSCdef(\chromaReverbEnabled, { |msg|
            this.setReverbEnabled(msg[1].asBoolean);

        }, '/chroma/reverbEnabled');
        OSCdef(\chromaReverbDecayTime, { |msg|
            this.setReverbDecayTime(msg[1]);

        }, '/chroma/reverbDecayTime');
        OSCdef(\chromaReverbMix, { |msg|
            this.setReverbMix(msg[1]);

        }, '/chroma/reverbMix');

        // Delay controls
        OSCdef(\chromaDelayEnabled, { |msg|
            this.setDelayEnabled(msg[1].asBoolean);

        }, '/chroma/delayEnabled');
        OSCdef(\chromaDelayTime, { |msg|
            this.setDelayTime(msg[1]);

        }, '/chroma/delayTime');
        OSCdef(\chromaDelayDecayTime, { |msg|
            this.setDelayDecayTime(msg[1]);

        }, '/chroma/delayDecayTime');
        OSCdef(\chromaModRate, { |msg|
            this.setModRate(msg[1]);

        }, '/chroma/modRate');
        OSCdef(\chromaModDepth, { |msg|
            this.setModDepth(msg[1]);

        }, '/chroma/modDepth');
        OSCdef(\chromaDelayMix, { |msg|
            this.setDelayMix(msg[1]);

        }, '/chroma/delayMix');

        // Global controls
        OSCdef(\chromaBlendMode, { |msg|
            var modes = [\mirror, \complement, \transform];
            this.setBlendMode(modes[msg[1].asInteger.clip(0, 2)]);

        }, '/chroma/blendMode');
        OSCdef(\chromaDryWet, { |msg|
            this.setDryWet(msg[1]);

        }, '/chroma/dryWet');

        // Effects order controls
        OSCdef(\chromaEffectsOrder, { |msg|
            var newOrder = msg[1..];
            this.setEffectsOrder(newOrder);

        }, '/chroma/effectsOrder');







        "Chroma OSC responders ready".postln;
    }



    cleanupOSC {
        OSCdef(\chromaGain).free;
        OSCdef(\chromaInputFreeze).free;
        OSCdef(\chromaInputFreezeLength).free;
        OSCdef(\chromaFilterEnabled).free;
        OSCdef(\chromaFilterAmount).free;
        OSCdef(\chromaFilterCutoff).free;
        OSCdef(\chromaFilterResonance).free;
        OSCdef(\chromaOverdriveEnabled).free;
        OSCdef(\chromaOverdriveDrive).free;
        OSCdef(\chromaOverdriveTone).free;
        OSCdef(\chromaOverdriveBias).free;
        OSCdef(\chromaOverdriveMix).free;
        OSCdef(\chromaGranularEnabled).free;
        OSCdef(\chromaGranularDensity).free;
        OSCdef(\chromaGranularSize).free;
        OSCdef(\chromaGranularPitchScatter).free;
        OSCdef(\chromaGranularPosScatter).free;
        OSCdef(\chromaGranularMix).free;
        OSCdef(\chromaGranularFreeze).free;
        OSCdef(\chromaGrainIntensity).free;
        OSCdef(\chromaBitcrushEnabled).free;
        OSCdef(\chromaBitDepth).free;
        OSCdef(\chromaBitcrushSampleRate).free;
        OSCdef(\chromaBitcrushDrive).free;
        OSCdef(\chromaBitcrushMix).free;
        OSCdef(\chromaReverbEnabled).free;
        OSCdef(\chromaReverbDecayTime).free;
        OSCdef(\chromaReverbMix).free;
        OSCdef(\chromaDelayEnabled).free;
        OSCdef(\chromaDelayTime).free;
        OSCdef(\chromaDelayDecayTime).free;
        OSCdef(\chromaModRate).free;
        OSCdef(\chromaModDepth).free;
        OSCdef(\chromaDelayMix).free;
        OSCdef(\chromaEffectsOrder).free;
        OSCdef(\chromaGetEffectsOrder).free;
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

    setFilterEnabled { |enabled|
        filterParams[\enabled] = enabled;
        if(synths[\filter].notNil) {
            synths[\filter].set(\amount, if(enabled, { filterParams[\amount] }, { 0.0 }));
        };
    }

    setFilterAmount { |val|
        filterParams[\amount] = val.clip(0, 1);
        if(synths[\filter].notNil && { filterParams[\enabled] }) {
            synths[\filter].set(\amount, val);
        };
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

    setOverdriveEnabled { |enabled|
        overdriveParams[\enabled] = enabled;
        if(synths[\overdrive].notNil) {
            synths[\overdrive].set(\mix, if(enabled, { overdriveParams[\mix] }, { 0.0 }));
        };
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
        if(synths[\overdrive].notNil && { overdriveParams[\enabled] }) {
            synths[\overdrive].set(\mix, val);
        };
    }

    setOverdriveBias { |val|
        var normalizedVal = val.linlin(-1, 1, 0, 1);  // Convert OSC range (-1,1) to internal (0,1)
        overdriveParams[\bias] = normalizedVal.clip(0, 1);
        if(synths[\overdrive].notNil) { synths[\overdrive].set(\bias, normalizedVal) };
        if(synths[\blend].notNil) { synths[\blend].set(\baseOverdriveBias, normalizedVal) };
    }

    setGranularEnabled { |enabled|
        granularParams[\enabled] = enabled;
        if(synths[\granular].notNil) {
            synths[\granular].set(\mix, if(enabled, { granularParams[\mix] }, { 0.0 }));
        };
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
        if(synths[\granular].notNil && { granularParams[\enabled] }) {
            synths[\granular].set(\mix, val);
        };
    }



    setGrainIntensity { |mode|
        var validModes = grainIntensityMultipliers.keys;
        var validMode = validModes.includes(mode).if({ mode }, { \subtle });
        
        if(validMode != mode) {
            "Invalid grainIntensity mode: %. Using subtle".format(mode).warn;
        };
        
        grainIntensity = validMode;
        if(synths[\blend].notNil) { 
            synths[\blend].set(\grainIntensityMultiplier, grainIntensityMultipliers[validMode]);
        }
    }

    // Bitcrushing controls
    setBitcrushEnabled { |enabled|
        bitcrushParams[\enabled] = enabled;
        if(synths[\bitcrush].notNil) { 
            synths[\bitcrush].set(\mix, if(enabled, { bitcrushParams[\mix] }, { 0.0 }));
        };
    }

    setBitDepth { |val|
        bitcrushParams[\bitDepth] = val.clip(4, 16);
        if(synths[\bitcrush].notNil) { synths[\bitcrush].set(\bitDepth, val) };
    }

    setBitcrushSampleRate { |val|
        bitcrushParams[\sampleRate] = val.clip(1000, 44100);
        if(synths[\bitcrush].notNil) { synths[\bitcrush].set(\sampleRate, val) };
    }

    setBitcrushDrive { |val|
        bitcrushParams[\drive] = val.clip(0, 1);
        if(synths[\bitcrush].notNil) { synths[\bitcrush].set(\drive, val) };
    }

    setBitcrushMix { |val|
        bitcrushParams[\mix] = val.clip(0, 1);
        if(synths[\bitcrush].notNil && { bitcrushParams[\enabled] }) { 
            synths[\bitcrush].set(\mix, val);
        };
    }

    // Reverb controls
    setReverbEnabled { |enabled|
        reverbParams[\enabled] = enabled;
        if(synths[\reverb].notNil) { 
            synths[\reverb].set(\mix, if(enabled, { reverbParams[\mix] }, { 0.0 }));
        };
    }

    setReverbDecayTime { |val|
        reverbParams[\decayTime] = val.clip(0.5, 10);
        if(synths[\reverb].notNil) { synths[\reverb].set(\decayTime, val) };
    }

    setReverbMix { |val|
        reverbParams[\mix] = val.clip(0, 1);
        if(synths[\reverb].notNil && { reverbParams[\enabled] }) { 
            synths[\reverb].set(\mix, val);
        };
    }

    // Delay controls
    setDelayEnabled { |enabled|
        delayParams[\enabled] = enabled;
        if(synths[\modDelay].notNil) { 
            synths[\modDelay].set(\mix, if(enabled, { delayParams[\mix] }, { 0.0 }));
        };
    }

    setDelayTime { |val|
        delayParams[\delayTime] = val.clip(0.1, 1);
        if(synths[\modDelay].notNil) { synths[\modDelay].set(\delayTime, val) };
    }

    setDelayDecayTime { |val|
        delayParams[\decayTime] = val.clip(0.5, 10);
        if(synths[\modDelay].notNil) { synths[\modDelay].set(\decayTime, val) };
    }

    setModRate { |val|
        delayParams[\modRate] = val.clip(0.1, 5);
        if(synths[\modDelay].notNil) { synths[\modDelay].set(\modRate, val) };
    }

    setModDepth { |val|
        delayParams[\modDepth] = val.clip(0, 1);
        if(synths[\modDelay].notNil) { synths[\modDelay].set(\modDepth, val) };
    }

    setDelayMix { |val|
        delayParams[\mix] = val.clip(0, 1);
        if(synths[\modDelay].notNil && { delayParams[\enabled] }) { 
            synths[\modDelay].set(\mix, val);
        };
    }


}
