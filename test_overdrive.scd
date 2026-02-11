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

"SynthDef compiled and added successfully".postln;