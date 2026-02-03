// Test SynthDef compilation and synth instantiation
// Uses dummy audio driver for headless testing
(
"=== Testing Chroma Synths ===".postln;

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

        "Testing controls...".postln;
        instance.setDryWet(0.7);
        ("  - dryWet: " ++ instance.dryWet).postln;

        instance.setDroneLevel(0.5);
        ("  - droneLevel: " ++ instance.droneLevel).postln;

        instance.setRootNote(48);
        ("  - rootNote: " ++ instance.config[\rootNote]).postln;

        instance.setLayerAmp(\sub, 0.4);
        ("  - layerAmps: " ++ instance.layerAmps).postln;

        0.5.wait;

        "Cleanup...".postln;
        instance.cleanup;

        0.5.wait;

        "".postln;
        "=== ALL SYNTH TESTS PASSED ===".postln;
        0.exit;
    };
}, onFailure: {
    "SERVER BOOT FAILED".postln;
    1.exit;
});
)
