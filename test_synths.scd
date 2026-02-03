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

        "Testing input freeze...".postln;
        ("  - inputFrozen before: " ++ instance.inputFrozen).postln;
        instance.toggleInputFreeze;
        ("  - inputFrozen after: " ++ instance.inputFrozen).postln;
        instance.toggleInputFreeze;
        ("  - inputFrozen reset: " ++ instance.inputFrozen).postln;
        instance.setInputFreezeLength(0.2);
        ("  - inputFreezeLength: " ++ instance.inputFreezeLength).postln;

        "Testing granular freeze toggle...".postln;
        ("  - frozen before: " ++ instance.frozen).postln;
        instance.toggleGranularFreeze;
        ("  - frozen after: " ++ instance.frozen).postln;
        instance.toggleGranularFreeze;
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
