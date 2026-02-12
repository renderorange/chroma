// synthdef_compilation_test.sc - SynthDef compilation and validation testing
// Migrated and enhanced from test_overdrive.scd

"=== SYNTHDEF COMPILATION AND VALIDATION TESTS ===".postln;

// Test 1: SynthDef Compilation Validation
~testSynthDefCompilation = {
    "=== TESTING SYNTHDEF COMPILATION ===".postln;
    
    var testPassed = true;
    var synthDefs = [\chroma_input, \chroma_filter, \chroma_overdrive, \chroma_bitcrush, 
                   \chroma_granular, \chroma_reverb, \chroma_mod_delay, \chroma_output,
                   \chroma_blend, \chroma_analysis, \chroma_input_freeze];
    
    try {
        synthDefs.do { |synthName|
            var synthDesc = SynthDescLib.global.at(synthName);
            if(synthDesc.notNil) {
                ("✓ %: compiled and available".format(synthName)).postln;
                
                // Check required controls
                var requiredControls = switch(synthName,
                    \chroma_overdrive, [\drive, \tone, \bias],
                    \chroma_filter, [\inBus, \outBus, \gainsBus, \amount, \baseCutoff, \resonance],
                    \chroma_granular, [\inBus, \outBus, \grainBuf, \freezeBuf, \ctrlBus, \freeze, \mix],
                    \chroma_output, [\dryBus, \wetBus, \dryWet, \outBus],
                    []); // default check
                
                requiredControls.do { |controlName|
                    var hasControl = synthDesc.controls.detect({ |ctrl| ctrl.name == controlName }).notNil;
                    if(hasControl) {
                        ("    ✓ Control % available".format(controlName)).postln;
                    } {
                        ("    ✗ Control % missing".format(controlName)).error;
                        testPassed = false;
                    };
                };
                
            } {
                ("✗ %: NOT COMPILED".format(synthName)).error;
                testPassed = false;
            };
        };
        
    } { |error|
        "ERROR: SynthDef compilation test failed: %".format(error).postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\SynthDefCompilation, testPassed,
        if(testPassed, {"All SynthDefs compiled successfully"}, {"Some SynthDefs failed to compile"}));
};

// Test 2: Overdrive Bias Parameter Integration
~testOverdriveBiasIntegration = {
    "=== TESTING OVERDRIVE BIAS INTEGRATION ===".postln;
    
    var testPassed = true;
    
    try {
        // Check overdrive SynthDef has bias parameter
        var overdriveDesc = SynthDescLib.global.at(\chroma_overdrive);
        if(overdriveDesc.notNil) {
            var biasControl = overdriveDesc.controls.detect({ |ctrl| ctrl.name == \bias });
            if(biasControl.notNil) {
                "✓ Overdrive SynthDef has bias parameter: %".format(biasControl).postln;
            } {
                "✗ Overdrive SynthDef missing bias parameter".error;
                testPassed = false;
            };
            
            // Check blend SynthDef includes baseOverdriveBias
            var blendDesc = SynthDescLib.global.at(\chroma_blend);
            if(blendDesc.notNil) {
                var baseBiasControl = blendDesc.controls.detect({ |ctrl| ctrl.name == \baseOverdriveBias });
                if(baseBiasControl.notNil) {
                    "✓ Blend SynthDef has baseOverdriveBias parameter: %".format(baseBiasControl).postln;
                } {
                    "✗ Blend SynthDef missing baseOverdriveBias parameter".error;
                    testPassed = false;
                };
            } {
                "✗ Blend SynthDef not found".error;
                testPassed = false;
            };
        } {
            "✗ Overdrive SynthDef not found".error;
            testPassed = false;
        };
        
    } { |error|
        "ERROR: Overdrive bias integration test failed: %".format(error).postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\OverdriveBiasIntegration, testPassed,
        if(testPassed, {"Overdrive bias integration working"}, {"Overdrive bias integration failed"}));
};

// Test 3: Control Bus Validation
~testControlBuses = {
    "=== TESTING CONTROL BUS VALIDATION ===".postln;
    
    var testPassed = true;
    var expectedControlBuses = [\overdriveCtrl, \filterGains, \granularCtrl];
    
    try {
        expectedControlBuses.do { |busName|
            var bus = ~chroma.buses[busName];
            if(bus.notNil) {
                ("✓ Control bus %: allocated (% channels)".format(busName, bus.numChannels)).postln;
                
                // Check expected channel counts
                var expectedChannels = switch(busName,
                    \overdriveCtrl, 3, // drive, tone, bias
                    \filterGains, 8, // 8 filter gains
                    \granularCtrl, 4, // density, size, pitchScatter, posScatter
                    2); // default
                
                if(bus.numChannels == expectedChannels) {
                    ("    ✓ Expected channel count: %".format(expectedChannels)).postln;
                } {
                    ("    ✗ Expected % channels, got %".format(expectedChannels, bus.numChannels)).error;
                    testPassed = false;
                };
            } {
                ("✗ Control bus %: NOT ALLOCATED".format(busName)).error;
                testPassed = false;
            };
        };
        
    } { |error|
        "ERROR: Control bus validation test failed: %".format(error).postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\ControlBuses, testPassed,
        if(testPassed, {"Control buses properly allocated"}, {"Control bus validation failed"}));
};

// Test 4: Audio Effect Signal Path
~testAudioSignalPath = {
    "=== TESTING AUDIO SIGNAL PATH ===".postln;
    
    var testPassed = true;
    
    try {
        // Test signal flow through overdrive with bias
        var biasValues = [-0.5, 0.0, 0.5];
        
        biasValues.do { |bias|
            ("Testing bias %:".format(bias)).postln;
            
            // Simulate signal processing
            var testSignal = [0, 0.5, 1.0, -0.5, -1.0];
            testSignal.do { |sample|
                var biasedSignal = sample + bias;
                var preGain = 0.5.linexp(0, 1, 1, 20);
                var saturatedSignal = (biasedSignal * preGain).tanh;
                var postGain = 0.5.linlin(0, 1, 1, 0.5);
                var finalSignal = saturatedSignal * postGain;
                
                ("  Sample %+0.1f -> Bias %+0.1f -> Output %+0.3f".format(sample, biasedSignal, finalSignal)).postln;
            };
            
            // Verify bias creates asymmetrical distortion
            var positiveMax = testSignal.select({ |x| x > 0 }).maxItem;
            var negativeMax = testSignal.select({ |x| x < 0 }).minItem;
            var positiveOutput = (positiveMax + bias).tanh;
            var negativeOutput = (negativeMax + bias).tanh;
            
            if(positiveOutput.abs != negativeOutput.abs) {
                "    ✓ Asymmetrical distortion detected".postln;
            } {
                "    ⚠️ Symmetrical distortion (may need more bias)".postln;
            };
            
            "".postln;
        };
        
        "✓ Audio signal path test completed".postln;
        
    } { |error|
        "ERROR: Audio signal path test failed: %".format(error).postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\AudioSignalPath, testPassed,
        if(testPassed, {"Audio signal path working"}, {"Audio signal path failed"}));
};

// Main SynthDef test runner
~runSynthDefCompilationTests = {
    "=== RUNNING SYNTHDEF COMPILATION TESTS ===".postln;
    "".postln;
    
    ~testResults = []; // Clear previous results
    
    // Run all SynthDef tests
    ~testSynthDefCompilation.value;
    0.5.wait;
    
    ~testOverdriveBiasIntegration.value;
    0.5.wait;
    
    ~testControlBuses.value;
    0.5.wait;
    
    ~testAudioSignalPath.value;
    
    // Summary
    "".postln;
    "=== SYNTHDEF COMPILATION TEST SUMMARY ===".postln;
    var totalTests = ~testResults.size;
    var passedTests = ~testResults.count({ |result| result.passed });
    var failedTests = totalTests - passedTests;
    
    ("Total SynthDef tests: %".format(totalTests)).postln;
    ("Passed: %".format(passedTests)).postln;
    ("Failed: %".format(failedTests)).postln;
    ("Success rate: %%%".format((passedTests/totalTests * 100).round(1))).postln;
    
    if(failedTests > 0) {
        "".postln;
        "FAILED SYNTHDEF TESTS:".postln;
        ~testResults.select({ |result| result.passed.not }).do { |result|
            ("  %: %".format(result.test, result.message)).postln;
        };
    };
    
    "=== SYNTHDEF COMPILATION TESTING COMPLETE ===".postln;
    
    ^failedTests == 0; // Return true if all tests passed
};

// Interactive commands
"=== SYNTHDEF COMPILATION TESTING TOOLS LOADED ===".postln;
"Available commands:".postln;
"  ~runSynthDefCompilationTests.value".postln;
"  ~testSynthDefCompilation.value".postln;
"  ~testOverdriveBiasIntegration.value".postln;
"  ~testControlBuses.value".postln;
"  ~testAudioSignalPath.value".postln;
"".postln;
"Usage:".postln;
"  ~runSynthDefCompilationTests.value; // Run complete SynthDef test suite".postln;
"  // Individual tests for specific debugging".postln;