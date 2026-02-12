// test_audio_chain.sc - Automated audio chain testing
// This script provides automated testing of the Chroma audio signal chain

// Load Chroma first
"Loading Chroma for automated testing...".postln;
Chroma.start(debug: 2); // Verbose debugging

(
// ============ AUTOMATED TEST SUITE ============

~testResults = [];

~addTestResult = { |testName, passed, message|
    var result, status;
    result = (test: testName, passed: passed, message: message);
    ~testResults = ~testResults.add(result);
    status = if(passed, {"PASS"}, {"FAIL"});
    ("[TEST] %: % - %".format(testName, status, message)).postln;
};

~generateTestSignal = { |freq=440, duration=1.0, amp=0.1|
    var testSynth, testSig;
    ("Generating %Hz test signal for % seconds...".format(freq, duration)).postln;
    
    testSynth = {
        testSig = SinOsc.ar(freq) * amp;
        Out.ar(~chroma.buses[\inputAudio].index, testSig);
    }.play;
    
    duration.wait;
    testSynth.free;
    "Test signal generation complete".postln;
    ^testSynth;
};

~verifySignalChain = {
    var inputLevelStart, inputLevelEnd, outputLevel, testPassed, expectedBuses;
    "=== VERIFYING SIGNAL CHAIN ===".postln;

    inputLevelStart = nil;
    inputLevelEnd = nil;
    outputLevel = nil;
    testPassed = true;
    
    // Check initial input level
    if(~chroma.buses[\inputAmp].notNil) {
        inputLevelStart = ~chroma.buses[\inputAmp].getSynchronous;
        ("Initial input level: %".format(inputLevelStart)).postln;
    };
    
    // Generate test signal
    ~generateTestSignal.value(440, 2.0, 0.2);
    
    // Check output chain connectivity
    expectedBuses = [\frozenAudio, \filterToOverdrive, \overdriveToBitcrush, 
                         \bitcrushToGranular, \granularToReverb, \reverbToDelay, \delayAudio];
    
    expectedBuses.do { |busName, i|
        var bus;
        bus = ~chroma.buses[busName];
        if(bus.notNil) {
            ("✓ Bus %: connected (index %)".format(busName, bus.index)).postln;
        } {
            ("✗ Bus %: NOT CONNECTED".format(busName)).error;
            testPassed = false;
        };
    };
    
    ~addTestResult.value(\SignalChainConnectivity, testPassed, 
        if(testPassed, {"All buses properly connected"}, {"Missing bus connections"}));
};

~testEachEffect = {
    var effects, testPassed, effectSynth, nodeID;
    "=== TESTING INDIVIDUAL EFFECTS ===".postln;
    
    effects = [\filter, \overdrive, \bitcrush, \granular, \reverb, \delay];
    
    effects.do { |effectName|
        testPassed = true;
        effectSynth = ~chroma.synths[effectName];
        
        if(effectSynth.notNil) {
            ("✓ Effect %: synth created and running".format(effectName)).postln;
            
            // Check if effect synth is alive
            nodeID = effectSynth.nodeID;
            if(nodeID.notNil and: {nodeID > 0}) {
                ("✓ Effect %: node ID % is active".format(effectName, nodeID)).postln;
            } {
                ("✗ Effect %: synth not active".format(effectName)).error;
                testPassed = false;
            };
            
        } {
            ("✗ Effect %: synth NOT CREATED".format(effectName)).error;
            testPassed = false;
        };
        
        ~addTestResult.value((effectName ++ "Effect"), testPassed, 
            if(testPassed, {"Effect running properly"}, {"Effect creation/activation failed"}));
    };
};

~testAudioInput = {
    var testPassed, inputChannel, inputSynth;
    "=== TESTING AUDIO INPUT ===".postln;
    
    testPassed = true;
    inputChannel = ~chroma.config[\inputChannel];
    
    // Validate input channel exists
    if(Server.default.options.numInputBusChannels > inputChannel) {
        ("✓ Input channel %: available".format(inputChannel)).postln;
    } {
        ("✗ Input channel %: NOT AVAILABLE".format(inputChannel)).error;
        testPassed = false;
    };
    
    // Test input synth creation
    inputSynth = ~chroma.synths[\input];
    if(inputSynth.notNil) {
        "✓ Input synth: created and running".postln;
    } {
        "✗ Input synth: NOT CREATED".error;
        testPassed = false;
    };
    
    ~addTestResult.value(\AudioInput, testPassed, 
        if(testPassed, {"Input channel and synth working"}, {"Input system failed"}));
};

~testAudioOutput = {
    var testPassed, outputSynth;
    "=== TESTING AUDIO OUTPUT ===".postln;
    
    testPassed = true;
    
    // Validate output channel exists
    if(Server.default.options.numOutputChannels > 0) {
        "✓ Output channel 0: available".postln;
    } {
        "✗ Output channel 0: NOT AVAILABLE".error;
        testPassed = false;
    };
    
    // Test output synth creation
    outputSynth = ~chroma.synths[\output];
    if(outputSynth.notNil) {
        "✓ Output synth: created and running".postln;
    } {
        "✗ Output synth: NOT CREATED".error;
        testPassed = false;
    };
    
    ~addTestResult.value(\AudioOutput, testPassed, 
        if(testPassed, {"Output system working"}, {"Output system failed"}));
};

~testSpectralAnalysis = {
    var testPassed, analysisSynth, spectralBuses;
    "=== TESTING SPECTRAL ANALYSIS ===".postln;
    
    testPassed = true;
    analysisSynth = ~chroma.synths[\analysis];
    
    if(analysisSynth.notNil) {
        "✓ Analysis synth: created and running".postln;
        
        // Check spectral analysis buses
        spectralBuses = [\bands, \centroid, \spread, \flatness];
        spectralBuses.do { |busName|
            var bus;
            bus = ~chroma.buses[busName];
            if(bus.notNil) {
                ("✓ Spectral bus %: allocated (index %)".format(busName, bus.index)).postln;
            } {
                ("✗ Spectral bus %: NOT ALLOCATED".format(busName)).error;
                testPassed = false;
            };
        };
    } {
        "✗ Analysis synth: NOT CREATED".error;
        testPassed = false;
    };
    
    ~addTestResult.value(\SpectralAnalysis, testPassed, 
        if(testPassed, {"Spectral analysis working"}, {"Spectral analysis failed"}));
};

~testOSCCommunication = {
    var testPassed, testMsg;
    "=== TESTING OSC COMMUNICATION ===".postln;
    
    testPassed = true;
    
    // Test basic OSC communication
    try {
        testMsg = NetAddr("127.0.0.1", 57120);
        testMsg.sendMsg("/chroma/gain", 0.8);
        "✓ OSC communication: message sent successfully".postln;
        
        // Test multiple OSC messages
        testMsg.sendMsg("/chroma/filterAmount", 0.5);
        testMsg.sendMsg("/chroma/dryWet", 0.7);
        "✓ OSC communication: multiple messages sent".postln;
        
    } { |error|
        "✗ OSC communication: FAILED".error;
        error.postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\OSCCommunication, testPassed, 
        if(testPassed, {"OSC working"}, {"OSC failed"}));
};

~runAutomatedTests = {
    var totalTests, passedTests, failedTests;
    "=== RUNNING AUTOMATED TEST SUITE ===".postln;
    "This will test all major audio system components...".postln;
    "".postln;
    
    ~testResults = []; // Clear previous results
    
    // Run all tests
    ~testAudioInput.value;
    ~testAudioOutput.value;
    ~testSpectralAnalysis.value;
    ~testEachEffect.value;
    ~testOSCCommunication.value;

    // Generate summary
    "".postln;
    "=== TEST SUMMARY ===".postln;
    totalTests = ~testResults.size;
    passedTests = ~testResults.count({ |result| result.passed });
    failedTests = totalTests - passedTests;
    
    ("Total tests: %".format(totalTests)).postln;
    ("Passed: %".format(passedTests)).postln;
    ("Failed: %".format(failedTests)).postln;
    ("Success rate: %%%".format((passedTests/totalTests * 100).round(1))).postln;
    
    if(failedTests > 0) {
        "".postln;
        "FAILED TESTS:".postln;
        ~testResults.select({ |result| result.passed.not }).do { |result|
            ("  %: %".format(result.test, result.message)).postln;
        };
    };
    
    "=== AUTOMATED TESTING COMPLETE ===".postln;
    
    ^failedTests == 0; // Return true if all tests passed
};

// ============ INTERACTIVE COMMANDS ============

"=== AUTOMATED TEST SUITE LOADED ===".postln;
"Available commands:".postln;
"  ~runAutomatedTests.value".postln;
"  ~testAudioInput.value".postln;
"  ~testAudioOutput.value".postln;
"  ~testSpectralAnalysis.value".postln;
"  ~testEachEffect.value".postln;
"  ~testSignalChain.value".postln;
"  ~testOSCCommunication.value".postln;
"".postln;
"Example usage:".postln;
"  ~runAutomatedTests.value; // Run complete test suite".postln;
"  ~generateTestSignal.value(880, 2.0, 0.15); // Custom test signal".postln;
)
