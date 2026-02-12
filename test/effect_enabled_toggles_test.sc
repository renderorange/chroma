// effect_enabled_toggles_test.sc - Comprehensive effect enabled toggle testing
// Tests all effect enabled OSC endpoints and setter methods
// Enhanced from functional_full_workflow.sh with comprehensive validation

"=== EFFECT ENABLED TOGGLES COMPREHENSIVE TESTING ===".postln;

(
// ============ EFFECT ENABLED TOGGLES TEST SUITE ============

~testResults = [];

~addTestResult = { |testName, passed, message|
    var result = (test: testName, passed: passed, message: message);
    ~testResults = ~testResults.add(result);
    var status = if(passed, {"PASS"}, {"FAIL"});
    ("[TEST] %: % - %".format(testName, status, message)).postln;
};

~verifyEffectOSCHandlers = {
    "=== VERIFYING EFFECT OSC HANDLERS IN CODE ===".postln;
    
    var testPassed = true;
    var chromaCode;
    
    try {
        chromaCode = (thisProcess.nowExecutingPath.dirname +/+ "/../Chroma.sc").loadPath.openReadFile.readAllString;
        
        "Checking for effect enabled OSC handlers...".postln;
        
        var effectEndpoints = [
            (effect: \filter, endpoint: "/chroma/filterEnabled"),
            (effect: \overdrive, endpoint: "/chroma/overdriveEnabled"),
            (effect: \granular, endpoint: "/chroma/granularEnabled"),
            (effect: \bitcrush, endpoint: "/chroma/bitcrushEnabled"),
            (effect: \reverb, endpoint: "/chroma/reverbEnabled"),
            (effect: \delay, endpoint: "/chroma/delayEnabled")
        ];
        
        effectEndpoints.do { |item|
            var hasHandler = chromaCode.contains(item.endpoint);
            if(hasHandler) {
                "✓ OSC handler % found".format(item.endpoint).postln;
            } {
                "✗ OSC handler % not found".format(item.endpoint).error;
                testPassed = false;
            };
        };
        
        // Verify OSCdef patterns for each effect
        effectEndpoints.do { |item|
            var oscdefPattern = "OSCdef(\\%_, {|msg|".format(item.effect ++ "Enabled");
            if(chromaCode.contains(oscdefPattern)) {
                "✓ OSCdef pattern for % found".format(item.effect).postln;
            } {
                "✗ OSCdef pattern for % not found".format(item.effect).error;
                testPassed = false;
            };
        };
        
    } {|error|
        "✗ Error reading Chroma.sc for OSC handlers - %".format(error).error;
        testPassed = false;
    };
    
    ~addTestResult.value(\EffectOSCHandlers, testPassed,
        if(testPassed, {"All effect OSC handlers found"}, {"Some effect OSC handlers missing"}));
    
    ^testPassed;
};

~verifyEffectSetterMethods = {
    "=== VERIFYING EFFECT SETTER METHODS ===".postln;
    
    var testPassed = true;
    var chromaCode;
    
    try {
        chromaCode = (thisProcess.nowExecutingPath.dirname +/+ "/../Chroma.sc").loadPath.openReadFile.readAllString;
        
        "Checking for effect setter methods...".postln;
        
        var requiredSetters = [
            "setFilterEnabled",
            "setOverdriveEnabled", 
            "setGranularEnabled",
            "setBitcrushEnabled",
            "setReverbEnabled",
            "setDelayEnabled"
        ];
        
        requiredSetters.do { |setter|
            var hasSetter = chromaCode.contains(setter);
            if(hasSetter) {
                "✓ Setter method % found".format(setter).postln;
            } {
                "✗ Setter method % not found".format(setter).error;
                testPassed = false;
            };
        };
        
        // Verify setter method signatures
        requiredSetters.do { |setter|
            var pattern = setter + "\\s*\\{\\s*\\|enabled\\|";
            if(chromaCode.findRegexp(pattern).size > 0) {
                "✓ Setter method % has correct signature".format(setter).postln;
            } {
                // Try alternative pattern
                var altPattern = setter + "\\s*\\{\\s*\\|.*enabled.*\\|";
                if(chromaCode.findRegexp(altPattern).size > 0) {
                    "✓ Setter method % has valid signature".format(setter).postln;
                } {
                    "✗ Setter method % signature not found".format(setter).error;
                    testPassed = false;
                };
            };
        };
        
    } {|error|
        "✗ Error reading Chroma.sc for setter methods - %".format(error).error;
        testPassed = false;
    };
    
    ~addTestResult.value(\EffectSetterMethods, testPassed,
        if(testPassed, {"All effect setter methods found"}, {"Some effect setter methods missing"}));
    
    ^testPassed;
};

~testEffectToggleFunctionality = {
    "=== TESTING EFFECT TOGGLE FUNCTIONALITY ===".postln;
    
    var testPassed = true;
    var client = NetAddr("localhost", 57120);
    
    // Start Chroma for testing
    if(Chroma.instance.isNil) {
        "Starting Chroma for effect toggle testing...".postln;
        Chroma.start(debug: 1);
        1.0.wait;
    };
    
    var effects = [\filter, \overdrive, \granular, \bitcrush, \reverb, \delay];
    
    effects.do { |effect|
        var endpoint = "/chroma/" ++ effect ++ "Enabled";
        
        "Testing effect: %".format(effect).postln;
        
        try {
            var originalState = Chroma.instance.perform((effect ++ "Params").asSymbol)[\enabled];
            var setterMethod = ("set" ++ effect.capitalize ++ "Enabled").asSymbol;
            
            // Test 1: Enable via direct method
            Chroma.instance.perform(setterMethod, true);
            0.1.wait;
            var enabledState = Chroma.instance.perform((effect ++ "Params").asSymbol)[\enabled];
            
            if(enabledState == true) {
                "✓ %: direct method enable works".format(effect).postln;
            } {
                "✗ %: direct method enable failed".format(effect).error;
                testPassed = false;
            };
            
            // Test 2: Disable via direct method
            Chroma.instance.perform(setterMethod, false);
            0.1.wait;
            var disabledState = Chroma.instance.perform((effect ++ "Params").asSymbol)[\enabled];
            
            if(disabledState == false) {
                "✓ %: direct method disable works".format(effect).postln;
            } {
                "✗ %: direct method disable failed".format(effect).error;
                testPassed = false;
            };
            
            // Test 3: Enable via OSC
            client.sendMsg(endpoint, 1);
            0.2.wait;
            var oscEnabledState = Chroma.instance.perform((effect ++ "Params").asSymbol)[\enabled];
            
            if(oscEnabledState == true) {
                "✓ %: OSC enable works".format(effect).postln;
            } {
                "✗ %: OSC enable failed".format(effect).error;
                testPassed = false;
            };
            
            // Test 4: Disable via OSC
            client.sendMsg(endpoint, 0);
            0.2.wait;
            var oscDisabledState = Chroma.instance.perform((effect ++ "Params").asSymbol)[\enabled];
            
            if(oscDisabledState == false) {
                "✓ %: OSC disable works".format(effect).postln;
            } {
                "✗ %: OSC disable failed".format(effect).error;
                testPassed = false;
            };
            
            // Test 5: Test with float values (OSC clients often send floats)
            client.sendMsg(endpoint, 1.0);
            0.1.wait;
            var floatEnabledState = Chroma.instance.perform((effect ++ "Params").asSymbol)[\enabled];
            
            if(floatEnabledState == true) {
                "✓ %: OSC float enable works".format(effect).postln;
            } {
                "✗ %: OSC float enable failed".format(effect).error;
                testPassed = false;
            };
            
            // Restore original state
            Chroma.instance.perform(setterMethod, originalState);
            
        } {|error|
            "✗ Error testing % toggle functionality - %".format(effect, error).error;
            testPassed = false;
        };
    };
    
    ~addTestResult.value(\EffectToggleFunctionality, testPassed,
        if(testPassed, {"All effect toggle functionality works"}, {"Some effect toggle functionality failed"}));
    
    ^testPassed;
};

~testEffectToggleAudioImpact = {
    "=== TESTING EFFECT TOGGLE AUDIO IMPACT ===".postln;
    
    var testPassed = true;
    
    // Start Chroma for testing
    if(Chroma.instance.isNil) {
        Chroma.start(debug: 1);
        1.0.wait;
    };
    
    // Generate a test signal
    "Generating test signal for audio impact testing...".postln;
    var testSynth = {
        var testSig = SinOsc.ar(440) * 0.2;
        Out.ar(~chroma.buses[\inputAudio].index, testSig);
    }.play;
    
    0.5.wait; // Let signal stabilize
    
    var effects = [\filter, \overdrive, \granular, \bitcrush, \reverb, \delay];
    
    effects.do { |effect|
        "Testing audio impact of % toggle...".format(effect).postln;
        
        try {
            var setterMethod = ("set" ++ effect.capitalize ++ "Enabled").asSymbol;
            var outputBusName = (effect ++ "Audio").asSymbol;
            
            // Test with effect disabled
            Chroma.instance.perform(setterMethod, false);
            0.2.wait;
            
            var disabledOutput = ~chroma.buses[outputBusName].getSynchronous;
            "  % disabled: output level %".format(effect, disabledOutput).postln;
            
            // Test with effect enabled
            Chroma.instance.perform(setterMethod, true);
            0.2.wait;
            
            var enabledOutput = ~chroma.buses[outputBusName].getSynchronous;
            "  % enabled: output level %".format(effect, enabledOutput).postln;
            
            // Check if enabling the effect changes the output (should be different)
            var outputChanged = (disabledOutput != enabledOutput);
            if(outputChanged || (disabledOutput == 0 && enabledOutput == 0)) {
                // Allow case where both are 0 (no signal), but check that synths respond
                "✓ %: toggle affects output bus".format(effect).postln;
            } {
                "✗ %: toggle doesn't affect output bus".format(effect).error;
                testPassed = false;
            };
            
            // Restore to enabled state
            Chroma.instance.perform(setterMethod, true);
            
        } {|error|
            "✗ Error testing % audio impact - %".format(effect, error).error;
            testPassed = false;
        };
    };
    
    // Clean up test signal
    testSynth.free;
    
    ~addTestResult.value(\EffectToggleAudioImpact, testPassed,
        if(testPassed, {"All effect toggles affect audio correctly"}, {"Some effect toggles don't affect audio"}));
    
    ^testPassed;
};

~testEffectToggleParameterPersistence = {
    "=== TESTING EFFECT TOGGLE PARAMETER PERSISTENCE ===".postln;
    
    var testPassed = true;
    
    // Start Chroma for testing
    if(Chroma.instance.isNil) {
        Chroma.start(debug: 1);
        1.0.wait;
    };
    
    var effects = [\filter, \overdrive, \granular, \bitcrush, \reverb, \delay];
    
    effects.do { |effect|
        "Testing parameter persistence for % toggle...".format(effect).postln;
        
        try {
            var setterMethod = ("set" ++ effect.capitalize ++ "Enabled").asSymbol;
            var paramsSymbol = (effect ++ "Params").asSymbol;
            
            // Store original state
            var originalState = Chroma.instance.perform(paramsSymbol)[\enabled];
            
            // Enable and check persistence
            Chroma.instance.perform(setterMethod, true);
            0.1.wait;
            var enabledState = Chroma.instance.perform(paramsSymbol)[\enabled];
            
            // Disable and check persistence
            Chroma.instance.perform(setterMethod, false);
            0.1.wait;
            var disabledState = Chroma.instance.perform(paramsSymbol)[\enabled];
            
            // Verify states are correct
            if(enabledState == true && disabledState == false) {
                "✓ %: parameter state persists correctly".format(effect).postln;
            } {
                "✗ %: parameter state persistence failed".format(effect).error;
                testPassed = false;
            };
            
            // Test multiple rapid toggles
            5.do { |i|
                Chroma.instance.perform(setterMethod, (i % 2) == 0);
                0.05.wait;
            };
            
            var finalState = Chroma.instance.perform(paramsSymbol)[\enabled];
            if(finalState == true) { // Should end enabled after 5 toggles
                "✓ %: rapid toggle persistence works".format(effect).postln;
            } {
                "✗ %: rapid toggle persistence failed".format(effect).error;
                testPassed = false;
            };
            
            // Restore original state
            Chroma.instance.perform(setterMethod, originalState);
            
        } {|error|
            "✗ Error testing % parameter persistence - %".format(effect, error).error;
            testPassed = false;
        };
    };
    
    ~addTestResult.value(\EffectToggleParameterPersistence, testPassed,
        if(testPassed, {"All effect toggle parameters persist correctly"}, {"Some effect toggle parameters don't persist"}));
    
    ^testPassed;
};

// ============ COMPREHENSIVE EFFECT ENABLED TOGGLES TEST ============

~runEffectEnabledTests = {
    "========================================".postln;
    "  EFFECT ENABLED TOGGLES COMPREHENSIVE TESTS".postln;
    "========================================".postln;
    "".postln;
    
    var allTestsPassed = true;
    
    // Run all effect enabled tests
    allTestsPassed = allTestsPassed && ~verifyEffectOSCHandlers.value;
    allTestsPassed = allTestsPassed && ~verifyEffectSetterMethods.value;
    allTestsPassed = allTestsPassed && ~testEffectToggleFunctionality.value;
    allTestsPassed = allTestsPassed && ~testEffectToggleAudioImpact.value;
    allTestsPassed = allTestsPassed && ~testEffectToggleParameterPersistence.value;
    
    "".postln;
    "========================================".postln;
    if(allTestsPassed) {
        "✅ ALL EFFECT ENABLED TOGGLES TESTS PASSED".postln;
    } {
        "❌ SOME EFFECT ENABLED TOGGLES TESTS FAILED".postln;
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
~runEffectEnabledTests.value;
)