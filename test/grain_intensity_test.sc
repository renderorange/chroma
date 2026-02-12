// grain_intensity_test.sc - Grain intensity functionality testing
// Tests the grain intensity parameter that was missing from test coverage
// Migrated from functional_full_workflow.sh with enhanced functionality

"=== GRAIN INTENSITY FUNCTIONALITY TESTING ===".postln;

// Load Chroma for testing
"Loading Chroma for grain intensity testing...".postln;
Chroma.start(debug: 2);

(
// ============ GRAIN INTENSITY TEST SUITE ============

~testResults = [];

~addTestResult = { |testName, passed, message|
    var result = (test: testName, passed: passed, message: message);
    ~testResults = ~testResults.add(result);
    var status = if(passed, {"PASS"}, {"FAIL"});
    ("[TEST] %: % - %".format(testName, status, message)).postln;
};

~testGrainIntensityModes = {
    "=== TESTING GRAIN INTENSITY MODES ===".postln;
    
    var testPassed = true;
    var validModes = [\subtle, \pronounced, \extreme];
    
    // Test 1: Available modes
    "Testing available grain intensity modes...".postln;
    validModes.do { |mode|
        var modeTestPassed = true;
        
        try {
            Chroma.instance.setGrainIntensity(mode);
            0.1.wait; // Allow parameter to settle
            
            var currentMode = Chroma.instance.grainIntensity;
            if(currentMode == mode) {
                "✓ Mode %: successfully set".format(mode).postln;
            } {
                "✗ Mode %: failed to set (got %)".format(mode, currentMode).error;
                modeTestPassed = false;
                testPassed = false;
            };
            
        } {|error|
            "✗ Mode %: error setting mode - %".format(mode, error).error;
            modeTestPassed = false;
            testPassed = false;
        };
    };
    
    ~addTestResult.value(\GrainIntensityModes, testPassed, 
        if(testPassed, {"All grain intensity modes work correctly"}, {"Some grain intensity modes failed"}));
    
    ^testPassed;
};

~testGrainIntensityOSC = {
    "=== TESTING GRAIN INTENSITY OSC CONTROL ===".postln;
    
    var testPassed = true;
    var client = NetAddr("localhost", 57120);
    
    // Test 2: OSC endpoint responds
    "Testing OSC endpoint /chroma/grainIntensity...".postln;
    
    [\subtle, \pronounced, \extreme].do { |mode|
        var oscTestPassed = true;
        
        try {
            // Send OSC message (simulating TUI)
            client.sendMsg("/chroma/grainIntensity", mode);
            0.2.wait; // Allow OSC processing
            
            var currentMode = Chroma.instance.grainIntensity;
            if(currentMode == mode) {
                "✓ OSC: % mode successfully set via /chroma/grainIntensity".format(mode).postln;
            } {
                "✗ OSC: % mode failed to set via OSC (got %)".format(mode, currentMode).error;
                oscTestPassed = false;
                testPassed = false;
            };
            
        } {|error|
            "✗ OSC: error setting % mode - %".format(mode, error).error;
            oscTestPassed = false;
            testPassed = false;
        };
    };
    
    ~addTestResult.value(\GrainIntensityOSC, testPassed,
        if(testPassed, {"OSC grain intensity control works correctly"}, {"OSC grain intensity control failed"}));
    
    ^testPassed;
};

~testGrainIntensityAudioEffect = {
    "=== TESTING GRAIN INTENSITY AUDIO EFFECT ===".postln;
    
    var testPassed = true;
    var baselineDensity, baselineSize;
    
    try {
        // Get baseline parameters in subtle mode
        Chroma.instance.setGrainIntensity(\subtle);
        0.2.wait;
        baselineDensity = Chroma.instance.granularParams[\density];
        baselineSize = Chroma.instance.granularParams[\size];
        
        "Baseline (subtle) - Density: %, Size: %".format(baselineDensity, baselineSize).postln;
        
        // Test pronounced mode increases intensity
        Chroma.instance.setGrainIntensity(\pronounced);
        0.2.wait;
        var pronouncedDensity = Chroma.instance.granularParams[\density];
        var pronouncedSize = Chroma.instance.granularParams[\size];
        
        "Pronounced - Density: %, Size: %".format(pronouncedDensity, pronouncedSize).postln;
        
        // Test extreme mode increases intensity further
        Chroma.instance.setGrainIntensity(\extreme);
        0.2.wait;
        var extremeDensity = Chroma.instance.granularParams[\density];
        var extremeSize = Chroma.instance.granularParams[\size];
        
        "Extreme - Density: %, Size: %".format(extremeDensity, extremeSize).postln;
        
        // Verify intensity progression (subtle < pronounced < extreme)
        var densityProgression = (baselineDensity < pronouncedDensity) && (pronouncedDensity < extremeDensity);
        var sizeProgression = (baselineSize > pronouncedSize) && (pronouncedSize > extremeSize);
        
        if(densityProgression && sizeProgression) {
            "✓ Grain intensity correctly affects granular parameters".postln;
        } {
            "✗ Grain intensity parameter progression failed".error;
            testPassed = false;
        };
        
    } {|error|
        "✗ Error testing grain intensity audio effect - %".format(error).error;
        testPassed = false;
    };
    
    ~addTestResult.value(\GrainIntensityAudioEffect, testPassed,
        if(testPassed, {"Grain intensity affects audio parameters correctly"}, {"Grain intensity audio effect failed"}));
    
    ^testPassed;
};

~testGrainIntensityErrorHandling = {
    "=== TESTING GRAIN INTENSITY ERROR HANDLING ===".postln;
    
    var testPassed = true;
    var initialMode = Chroma.instance.grainIntensity;
    
    // Test 3: Invalid mode handling
    "Testing invalid grain intensity mode handling...".postln;
    
    try {
        // Test invalid mode falls back to subtle
        Chroma.instance.setGrainIntensity(\invalidMode);
        0.1.wait;
        
        var fallbackMode = Chroma.instance.grainIntensity;
        if(fallbackMode == \subtle) {
            "✓ Invalid mode correctly falls back to subtle".postln;
        } {
            "✗ Invalid mode handling failed (got %)".format(fallbackMode).error;
            testPassed = false;
        };
        
    } {|error|
        "✗ Error testing invalid mode - %".format(error).error;
        testPassed = false;
    };
    
    // Test 4: Empty string handling
    try {
        Chroma.instance.setGrainIntensity("");
        0.1.wait;
        
        var emptyMode = Chroma.instance.grainIntensity;
        if(emptyMode == \subtle) {
            "✓ Empty string correctly falls back to subtle".postln;
        } {
            "✗ Empty string handling failed (got %)".format(emptyMode).error;
            testPassed = false;
        };
        
    } {|error|
        "✗ Error testing empty string - %".format(error).error;
        testPassed = false;
    };
    
    // Restore initial mode
    Chroma.instance.setGrainIntensity(initialMode);
    
    ~addTestResult.value(\GrainIntensityErrorHandling, testPassed,
        if(testPassed, {"Error handling works correctly"}, {"Error handling has issues"}));
    
    ^testPassed;
};

// ============ COMPREHENSIVE GRAIN INTENSITY TEST ============

~runGrainIntensityTests = {
    "========================================".postln;
    "  GRAIN INTENSITY COMPREHENSIVE TESTS".postln;
    "========================================".postln;
    "".postln;
    
    var allTestsPassed = true;
    
    // Run all grain intensity tests
    allTestsPassed = allTestsPassed && ~testGrainIntensityModes.value;
    allTestsPassed = allTestsPassed && ~testGrainIntensityOSC.value;
    allTestsPassed = allTestsPassed && ~testGrainIntensityAudioEffect.value;
    allTestsPassed = allTestsPassed && ~testGrainIntensityErrorHandling.value;
    
    "".postln;
    "========================================".postln;
    if(allTestsPassed) {
        "✅ ALL GRAIN INTENSITY TESTS PASSED".postln;
    } {
        "❌ SOME GRAIN INTENSITY TESTS FAILED".postln;
    };
    "========================================".postln;
    
    // Print test summary
    "".postln;
    "=== TEST SUMMARY ===".postln;
    ~testResults.do { |result|
        var status = if(result.passed, {"✓"}, {"✗"});
        ("% %: %".format(status, result.test, result.message)).postln;
    };
    
    ^allTestsPassed;
};

// Auto-run the comprehensive test
~runGrainIntensityTests.value;
)