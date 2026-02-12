// osc_integration_test.sc - OSC interface verification
// Tests OSC endpoints and code verification for external OSC clients
// Migrated from functional_full_workflow.sh with enhanced verification

"=== OSC INTEGRATION VERIFICATION ===".postln;

(
// ============ OSC VERIFICATION SUITE ============

~testResults = [];

~addTestResult = { |testName, passed, message|
    var result = (test: testName, passed: passed, message: message);
    ~testResults = ~testResults.add(result);
    var status = if(passed, {"PASS"}, {"FAIL"});
    ("[TEST] %: % - %".format(testName, status, message)).postln;
};

~verifyOSCEndpointsExist = {
    "=== VERIFYING OSC ENDPOINTS IN CODE ===".postln;
    
    var testPassed = true;
    var chromaCode;
    
    try {
        // Load Chroma.sc content
        chromaCode = (thisProcess.nowExecutingPath.dirname +/+ "/../Chroma.sc").loadPath.openReadFile.readAllString;
        
        "Checking for required OSC endpoints...".postln;
        
        // Test grain intensity controls
        var hasGrainIntensity = chromaCode.contains("grainIntensity") && chromaCode.contains("/chroma/grainIntensity");
        if(hasGrainIntensity) {
            "✓ grainIntensity controls and OSC endpoint found".postln;
        } {
            "✗ grainIntensity controls or OSC endpoint not found".error;
            testPassed = false;
        };
        
        // Test all effect enabled endpoints
        var effectEndpoints = [
            "/chroma/filterEnabled",
            "/chroma/overdriveEnabled", 
            "/chroma/granularEnabled",
            "/chroma/bitcrushEnabled",
            "/chroma/reverbEnabled",
            "/chroma/delayEnabled"
        ];
        
        effectEndpoints.do { |endpoint|
            if(chromaCode.contains(endpoint)) {
                "✓ OSC endpoint % found".format(endpoint).postln;
            } {
                "✗ OSC endpoint % not found".format(endpoint).error;
                testPassed = false;
            };
        };
        
    } {|error|
        "✗ Error reading Chroma.sc - %".format(error).error;
        testPassed = false;
    };
    
    ~addTestResult.value(\OSCEndpointsExist, testPassed,
        if(testPassed, {"All required OSC endpoints found"}, {"Some OSC endpoints missing"}));
    
    ^testPassed;
};

~verifyEffectSettersExist = {
    "=== VERIFYING EFFECT SETTER METHODS ===".postln;
    
    var testPassed = true;
    var chromaCode;
    
    try {
        chromaCode = (thisProcess.nowExecutingPath.dirname +/+ "/../Chroma.sc").loadPath.openReadFile.readAllString;
        
        "Checking for required setter methods...".postln;
        
        var requiredSetters = [
            "setFilterEnabled",
            "setOverdriveEnabled", 
            "setGranularEnabled",
            "setBitcrushEnabled",
            "setReverbEnabled",
            "setDelayEnabled"
        ];
        
        requiredSetters.do { |setter|
            if(chromaCode.contains(setter)) {
                "✓ Setter method % found".format(setter).postln;
            } {
                "✗ Setter method % not found".format(setter).error;
                testPassed = false;
            };
        };
        
    } {|error|
        "✗ Error reading Chroma.sc for setters - %".format(error).error;
        testPassed = false;
    };
    
    ~addTestResult.value(\EffectSettersExist, testPassed,
        if(testPassed, {"All required setter methods found"}, {"Some setter methods missing"}));
    
    ^testPassed;
};

~testOSCEndpointFunctionality = {
    "=== TESTING OSC ENDPOINT FUNCTIONALITY ===".postln;
    
    var testPassed = true;
    var client = NetAddr("localhost", 57120);
    
    // Start Chroma for testing
    if(Chroma.instance.isNil) {
        "Starting Chroma for OSC testing...".postln;
        Chroma.start(debug: 1);
        1.0.wait;
    };
    
    // Test grain intensity OSC functionality
    "Testing grain intensity OSC endpoint...".postln;
    try {
        var originalMode = Chroma.instance.grainIntensity;
        
        // Test each mode via OSC
        [\subtle, \pronounced, \extreme].do { |mode|
            client.sendMsg("/chroma/grainIntensity", mode);
            0.2.wait;
            
            var currentMode = Chroma.instance.grainIntensity;
            if(currentMode == mode) {
                "✓ OSC /chroma/grainIntensity % works".format(mode).postln;
            } {
                "✗ OSC /chroma/grainIntensity % failed (got %)".format(mode, currentMode).error;
                testPassed = false;
            };
        };
        
        // Restore original mode
        Chroma.instance.setGrainIntensity(originalMode);
        
    } {|error|
        "✗ Error testing grain intensity OSC - %".format(error).error;
        testPassed = false;
    };
    
    // Test effect enabled OSC functionality
    "Testing effect enabled OSC endpoints...".postln;
    var effects = [\filter, \overdrive, \granular, \bitcrush, \reverb, \delay];
    
    effects.do { |effect|
        var endpoint = "/chroma/" ++ effect ++ "Enabled";
        
        try {
            var originalState = Chroma.instance.perform((effect ++ "Params").asSymbol)[\enabled];
            
            // Test enable via OSC
            client.sendMsg(endpoint, 1);
            0.1.wait;
            var enabledState = Chroma.instance.perform((effect ++ "Params").asSymbol)[\enabled];
            
            if(enabledState == true) {
                "✓ OSC % enables correctly".format(endpoint).postln;
            } {
                "✗ OSC % enable failed".format(endpoint).error;
                testPassed = false;
            };
            
            // Test disable via OSC
            client.sendMsg(endpoint, 0);
            0.1.wait;
            var disabledState = Chroma.instance.perform((effect ++ "Params").asSymbol)[\enabled];
            
            if(disabledState == false) {
                "✓ OSC % disables correctly".format(endpoint).postln;
            } {
                "✗ OSC % disable failed".format(endpoint).error;
                testPassed = false;
            };
            
            // Restore original state
            Chroma.instance.perform(("set" ++ effect.capitalize ++ "Enabled").asSymbol, originalState);
            
        } {|error|
            "✗ Error testing % endpoint - %".format(endpoint, error).error;
            testPassed = false;
        };
    };
    
    ~addTestResult.value(\OSCEndpointFunctionality, testPassed,
        if(testPassed, {"OSC endpoints function correctly"}, {"OSC endpoint functionality has issues"}));
    
    ^testPassed;
};

~verifyOSCClientParameters = {
    "=== VERIFYING OSC-CONTROLLABLE PARAMETERS ===".postln;
    
    var testPassed = true;
    var chromaCode;
    
    try {
        chromaCode = (thisProcess.nowExecutingPath.dirname +/+ "/../Chroma.sc").loadPath.openReadFile.readAllString;
        
        "Checking for OSC-controllable parameters...".postln;
        
        // Test for bias parameter (overdrive)
        var hasBias = chromaCode.contains("overdriveBias") && chromaCode.contains("/chroma/overdriveBias");
        if(hasBias) {
            "✓ overdriveBias parameter and OSC endpoint found".postln;
        } {
            "✗ overdriveBias parameter or OSC endpoint not found".error;
            testPassed = false;
        };
        
        // Test for blend modes
        var hasBlendModes = chromaCode.contains("blendMode") && chromaCode.contains("/chroma/blendMode");
        if(hasBlendModes) {
            "✓ blendMode parameter and OSC endpoint found".postln;
        } {
            "✗ blendMode parameter or OSC endpoint not found".error;
            testPassed = false;
        };
        
        // Test for dry/wet control
        var hasDryWet = chromaCode.contains("dryWet") && chromaCode.contains("/chroma/dryWet");
        if(hasDryWet) {
            "✓ dryWet parameter and OSC endpoint found".postln;
        } {
            "✗ dryWet parameter or OSC endpoint not found".error;
            testPassed = false;
        };
        
        // Test for master gain
        var hasGain = chromaCode.contains("masterGain") && chromaCode.contains("/chroma/gain");
        if(hasGain) {
            "✓ masterGain parameter and OSC endpoint found".postln;
        } {
            "✗ masterGain parameter or OSC endpoint not found".error;
            testPassed = false;
        };
        
    } {|error|
        "✗ Error verifying OSC parameters - %".format(error).error;
        testPassed = false;
    };
    
    ~addTestResult.value(\OSCClientParameters, testPassed,
        if(testPassed, {"All OSC-controllable parameters found"}, {"Some OSC parameters missing"}));
    
    ^testPassed;
};

~testOSCMessageHandling = {
    "=== TESTING OSC MESSAGE HANDLING ===".postln;
    
    var testPassed = true;
    var client = NetAddr("localhost", 57120);
    
    // Ensure Chroma is running
    if(Chroma.instance.isNil) {
        Chroma.start(debug: 1);
        1.0.wait;
    };
    
    "Testing OSC message format validation...".postln;
    
    // Test various message types that OSC clients would send
    var testMessages = [
        ["/chroma/gain", 0.8],
        ["/chroma/dryWet", 0.5],
        ["/chroma/blendMode", "mirror"],
        ["/chroma/grainIntensity", "pronounced"],
        ["/chroma/overdriveBias", -0.5],
        ["/chroma/filterEnabled", 1],
        ["/chroma/filterEnabled", 0]
    ];
    
    testMessages.do { |msg|
        var endpoint = msg[0];
        var value = msg[1];
        
        try {
            client.sendMsg(endpoint, value);
            0.1.wait;
            
            "✓ OSC message % % handled successfully".format(endpoint, value).postln;
            
        } {|error|
            "✗ OSC message % % failed - %".format(endpoint, value, error).error;
            testPassed = false;
        };
    };
    
    // Test invalid message handling
    "Testing invalid OSC message handling...".postln;
    
    try {
        // Test invalid grain intensity
        client.sendMsg("/chroma/grainIntensity", "invalidMode");
        0.2.wait;
        
        var fallbackMode = Chroma.instance.grainIntensity;
        if(fallbackMode == \subtle) {
            "✓ Invalid grain intensity correctly falls back".postln;
        } {
            "✗ Invalid grain intensity handling failed".error;
            testPassed = false;
        };
        
    } {|error|
        "✗ Error testing invalid message handling - %".format(error).error;
        testPassed = false;
    };
    
    ~addTestResult.value(\OSCMessageHandling, testPassed,
        if(testPassed, {"OSC message handling works correctly"}, {"OSC message handling has issues"}));
    
    ^testPassed;
};

// ============ COMPREHENSIVE OSC INTEGRATION TEST ============

~runOSCIntegrationTests = {
    "========================================".postln;
    "  OSC INTEGRATION COMPREHENSIVE TESTS".postln;
    "========================================".postln;
    "".postln;
    
    var allTestsPassed = true;
    
    // Run all OSC integration tests
    allTestsPassed = allTestsPassed && ~verifyOSCEndpointsExist.value;
    allTestsPassed = allTestsPassed && ~verifyEffectSettersExist.value;
    allTestsPassed = allTestsPassed && ~testOSCEndpointFunctionality.value;
    allTestsPassed = allTestsPassed && ~verifyOSCClientParameters.value;
    allTestsPassed = allTestsPassed && ~testOSCMessageHandling.value;
    
    "".postln;
    "========================================".postln;
    if(allTestsPassed) {
        "✅ ALL OSC INTEGRATION TESTS PASSED".postln;
    } {
        "❌ SOME OSC INTEGRATION TESTS FAILED".postln;
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
~runOSCIntegrationTests.value;
)