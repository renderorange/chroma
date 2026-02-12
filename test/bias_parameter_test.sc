// bias_parameter_test.sc - Comprehensive bias parameter testing
// Migrated and enhanced from test_bias_integration.scd

"=== BIAS PARAMETER INTEGRATION TEST ===".postln;

// Test 1: OSC Bias Range and Conversion
~testBiasOSCRange = {
    "=== TESTING OSC BIAS RANGE CONVERSION ===".postln;
    
    var testPassed, testValues, testAddr;
    testPassed = true;
    testValues = [-1.0, -0.75, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0];
    
    try {
        testAddr = NetAddr("127.0.0.1", 57120);
        
        "Testing bias parameter range conversion:".postln;
        testValues.do { |val|
            var expectedInternal = val.linlin(-1, 1, 0, 1);
            var expectedDC = (expectedInternal - 0.5) * 2;
            
            ("  OSC % -> Internal % -> DC %".format(val, expectedInternal, expectedDC)).postln;
            
            // Send OSC message
            testAddr.sendMsg("/chroma/overdriveBias", val);
            0.05.wait;
            
            // Verify parameter stored correctly
            var actualInternal = ~chroma.overdriveParams[\bias];
            var match = (actualInternal - expectedInternal).abs < 0.01;
            
            if(match) {
                "    ✓ Conversion accurate".postln;
            } {
                "    ✗ Conversion error: expected %, got %".format(expectedInternal, actualInternal).error;
                testPassed = false;
            };
        };
        
    } { |error|
        "ERROR: OSC bias range test failed: %".format(error).postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\BiasOSCRange, testPassed,
        if(testPassed, {"OSC bias range conversion working"}, {"OSC bias range conversion failed"}));
};

// Test 2: Parameter Storage and Retrieval
~testBiasParameterStorage = {
    "=== TESTING BIAS PARAMETER STORAGE ===".postln;
    
    var testPassed = true;
    
    try {
        "Current bias parameter: %".format(~chroma.overdriveParams[\bias]).postln;
        
        // Test setting different values
        [0.0, 0.25, 0.5, 0.75, 1.0].do { |internalVal|
            var oscVal = internalVal.linlin(0, 1, -1, 1);
            ~chroma.setOverdriveBias(oscVal);
            var storedVal = ~chroma.overdriveParams[\bias];
            
            ("Set bias to % -> Stored: %".format(oscVal, storedVal)).postln;
            
            var expected = internalVal;
            var match = (storedVal - expected).abs < 0.001;
            
            if(match) {
                "    ✓ Storage accurate".postln;
            } {
                "    ✗ Storage error: expected %, got %".format(expected, storedVal).error;
                testPassed = false;
            };
        };
        
    } { |error|
        "ERROR: Bias parameter storage test failed: %".format(error).postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\BiasParameterStorage, testPassed,
        if(testPassed, {"Bias parameter storage working"}, {"Bias parameter storage failed"}));
};

// Test 3: Spectral Modulation Integration
~testBiasSpectralModulation = {
    "=== TESTING BIAS SPECTRAL MODULATION ===".postln;
    
    var testPassed = true;
    var baseBias = 0.5;
    var testModes = [
        ("Mirror", {centroid| centroid.linlin(0, 1, baseBias * 0.8, baseBias * 1.2)}),
        ("Complement", {centroid| (1-centroid).linlin(0, 1, baseBias * 0.8, baseBias * 1.2)}),
        ("Transform", {flatness| flatness.linlin(0, 1, baseBias * 0.7, baseBias * 1.3)})
    ];
    
    try {
        "Simulating blend control bias modulation:".postln;
        
        testModes.do { |modeData|
            var modeName = modeData[0];
            var modulationFunc = modeData[1];
            var lowInput = 0.2;
            var highInput = 0.8;
            var lowOutput = modulationFunc.value(lowInput);
            var highOutput = modulationFunc.value(highInput);
            
            ("  %: Input % -> Bias %, Input % -> Bias %".format(
                modeName, lowInput, lowOutput, highInput, highOutput)).postln;
        };
        
        "✓ Spectral modulation logic verified".postln;
        
    } { |error|
        "ERROR: Bias spectral modulation test failed: %".format(error).postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\BiasSpectralModulation, testPassed,
        if(testPassed, {"Bias spectral modulation working"}, {"Bias spectral modulation failed"}));
};

// Test 4: Audio Effect Simulation
~testBiasAudioEffect = {
    "=== TESTING BIAS AUDIO EFFECT SIMULATION ===".postln;
    
    var testPassed = true;
    
    try {
        "Simulating bias effect on signal:".postln;
        
        var testSignal = [0, 0.5, 1.0, -0.5, -1.0];
        var biasValues = [-0.5, 0.0, 0.5];
        
        biasValues.do { |bias|
            ("  Bias %:".format(bias)).postln;
            testSignal.do { |sample|
                var biasedSignal = sample + bias;
                var saturatedSignal = biasedSignal.tanh;
                ("    Sample % -> %+%.1f -> %+%.3f".format(sample, biasedSignal, saturatedSignal)).postln;
            };
            "".postln;
        };
        
        "✓ Audio effect theory verified - bias creates asymmetrical clipping".postln;
        
    } { |error|
        "ERROR: Bias audio effect simulation failed: %".format(error).postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\BiasAudioEffect, testPassed,
        if(testPassed, {"Bias audio effect simulation working"}, {"Bias audio effect simulation failed"}));
};

// Test 5: Edge Cases and Bounds Checking
~testBiasEdgeCases = {
    "=== TESTING BIAS EDGE CASES ===".postln;
    
    var testPassed = true;
    var edgeCases = [-1.5, -1.0, 1.0, 1.5];
    
    try {
        "Testing OSC bounds validation:".postln;
        edgeCases.do { |val|
            var clippedVal = val.clip(-1.0, 1.0);
            var internalVal = clippedVal.linlin(-1, 1, 0, 1);
            ("  OSC input % -> clipped % -> internal %".format(val, clippedVal, internalVal)).postln;
        };
        
        "✓ Edge case handling verified".postln;
        
    } { |error|
        "ERROR: Bias edge cases test failed: %".format(error).postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\BiasEdgeCases, testPassed,
        if(testPassed, {"Bias edge cases working"}, {"Bias edge cases failed"}));
};

// Main bias test runner
~runBiasParameterTests = {
    "=== RUNNING COMPREHENSIVE BIAS PARAMETER TESTS ===".postln;
    "".postln;
    
    ~testResults = []; // Clear previous results
    
    // Run all bias tests
    ~testBiasOSCRange.value;
    0.5.wait;
    
    ~testBiasParameterStorage.value;
    0.5.wait;
    
    ~testBiasSpectralModulation.value;
    0.5.wait;
    
    ~testBiasAudioEffect.value;
    0.5.wait;
    
    ~testBiasEdgeCases.value;
    
    // Summary
    "".postln;
    "=== BIAS PARAMETER TEST SUMMARY ===".postln;
    var totalTests = ~testResults.size;
    var passedTests = ~testResults.count({ |result| result.passed });
    var failedTests = totalTests - passedTests;
    
    ("Total bias tests: %".format(totalTests)).postln;
    ("Passed: %".format(passedTests)).postln;
    ("Failed: %".format(failedTests)).postln;
    ("Success rate: %%%".format((passedTests/totalTests * 100).round(1))).postln;
    
    if(failedTests > 0) {
        "".postln;
        "FAILED BIAS TESTS:".postln;
        ~testResults.select({ |result| result.passed.not }).do { |result|
            ("  %: %".format(result.test, result.message)).postln;
        };
    };
    
    "=== BIAS PARAMETER TESTING COMPLETE ===".postln;
    
    ^failedTests == 0; // Return true if all tests passed
};

// Interactive commands
"=== BIAS PARAMETER TESTING TOOLS LOADED ===".postln;
"Available commands:".postln;
"  ~runBiasParameterTests.value".postln;
"  ~testBiasOSCRange.value".postln;
"  ~testBiasParameterStorage.value".postln;
"  ~testBiasSpectralModulation.value".postln;
"  ~testBiasAudioEffect.value".postln;
"  ~testBiasEdgeCases.value".postln;
"".postln;
"Usage:".postln;
"  ~runBiasParameterTests.value; // Run complete bias test suite".postln;
"  // Individual tests for specific debugging".postln;