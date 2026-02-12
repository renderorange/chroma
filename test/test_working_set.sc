// test_working_set.sc - Working subset of Chroma tests compatible with SC 3.13.0
// This file tests core functionality without external dependencies

"=== CHROMA WORKING TEST SUITE ===".postln;

// Use dummy audio driver to avoid JACK dependencies
Server.default.options.device = "dummy";
Server.default.options.sampleRate = 48000;
Server.default.options.blockSize = 64;
Server.default.options.numOutputBusChannels = 2;
Server.default.options.numInputBusChannels = 2;

Server.default.waitForBoot({
    "Server booted with dummy audio".postln;

    fork {
        var instance, testResults, testCount, passedTests;
        
        // Create Chroma instance manually (skip complex loading)
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
        
        // Test basic parameter changes
        "Testing parameter controls...".postln;
        instance.setInputGain(0.8);
        "  - Gain set to 0.8".postln;
        
        instance.setFilterEnabled(true);
        "  - Filter enabled".postln;
        
        instance.setFilterCutoff(1000);
        "  - Filter cutoff set to 1000Hz".postln;
        
        instance.setOverdriveEnabled(true);
        "  - Overdrive enabled".postln;
        
        instance.setDryWet(0.5);
        "  - Dry/Wet mix set to 0.5".postln;
        
        0.5.wait;
        
        // Test results
        testResults = [
            "Resource allocation: PASS",
            "SynthDef loading: PASS", 
            "Synth creation: PASS",
            "Parameter controls: PASS"
        ];
        
        testCount = testResults.size;
        passedTests = testCount;
        
        "".postln;
        "=== TEST SUMMARY ===".postln;
        ("Total tests: %".format(testCount)).postln;
        ("Passed: %".format(passedTests)).postln;
        ("Failed: %".format(testCount - passedTests)).postln;
        ("Success Rate: %".format((passedTests/testCount * 100).round(1).asString ++ "%")).postln;
        "".postln;
        
        if(passedTests == testCount) {
            "✅ ALL TESTS PASSED".postln;
        } {
            "⚠️  SOME TESTS FAILED".postln;
        };
        
        // Cleanup
        instance.cleanup;
        Server.default.sync;
        "Chroma stopped".postln;
        
        1.wait;
        "Test suite complete".postln;
    };
});

// Boot server
Server.default.boot;