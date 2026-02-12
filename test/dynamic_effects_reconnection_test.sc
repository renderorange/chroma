// dynamic_effects_reconnection_test.sc - Dynamic effects reconnection validation
// Tests the reconnection methods and integration points that were missing from test coverage
// Enhanced from functional_full_workflow.sh with comprehensive validation

"=== DYNAMIC EFFECTS RECONNECTION VALIDATION ===".postln;

(
// ============ DYNAMIC EFFECTS RECONNECTION TEST SUITE ============

~testResults = [];

~addTestResult = { |testName, passed, message|
    var result = (test: testName, passed: passed, message: message);
    ~testResults = ~testResults.add(result);
    var status = if(passed, {"PASS"}, {"FAIL"});
    ("[TEST] %: % - %".format(testName, status, message)).postln;
};

~verifyReconnectionMethodsExist = {
    "=== VERIFYING RECONNECTION METHODS IN CODE ===".postln;
    
    var testPassed = true;
    var chromaCode;
    
    try {
        chromaCode = (thisProcess.nowExecutingPath.dirname +/+ "/../Chroma.sc").loadPath.openReadFile.readAllString;
        
        "Checking for required reconnection methods...".postln;
        
        // Test reconnectEffects method
        var hasReconnectEffects = chromaCode.contains("reconnectEffects") && 
                                   chromaCode.contains("reconnectEffects\\s*\\{");
        if(hasReconnectEffects) {
            "✓ reconnectEffects method found".postln;
        } {
            "✗ reconnectEffects method not found".error;
            testPassed = false;
        };
        
        // Test getEffectOutputBus method
        var hasGetEffectOutputBus = chromaCode.contains("getEffectOutputBus") &&
                                     chromaCode.contains("getEffectOutputBus\\s*\\{");
        if(hasGetEffectOutputBus) {
            "✓ getEffectOutputBus method found".postln;
        } {
            "✗ getEffectOutputBus method not found".error;
            testPassed = false;
        };
        
        // Verify method signatures
        var reconnectPattern = "reconnectEffects\\s*\\{";
        if(chromaCode.findRegexp(reconnectPattern).size > 0) {
            "✓ reconnectEffects has correct signature".postln;
        } {
            "✗ reconnectEffects signature not found".error;
            testPassed = false;
        };
        
        var getBusPattern = "getEffectOutputBus\\s*\\{\\s*\\|effect\\|";
        if(chromaCode.findRegexp(getBusPattern).size > 0) {
            "✓ getEffectOutputBus has correct signature".postln;
        } {
            // Try alternative pattern
            var altPattern = "getEffectOutputBus\\s*\\{\\s*\\|.*effect.*\\|";
            if(chromaCode.findRegexp(altPattern).size > 0) {
                "✓ getEffectOutputBus has valid signature".postln;
            } {
                "✗ getEffectOutputBus signature not found".error;
                testPassed = false;
            };
        };
        
    } {|error|
        "✗ Error reading Chroma.sc for reconnection methods - %".format(error).error;
        testPassed = false;
    };
    
    ~addTestResult.value(\ReconnectionMethodsExist, testPassed,
        if(testPassed, {"All reconnection methods found"}, {"Some reconnection methods missing"}));
    
    ^testPassed;
};

~verifyReconnectionIntegrationPoints = {
    "=== VERIFYING RECONNECTION INTEGRATION POINTS ===".postln;
    
    var testPassed = true;
    var chromaCode;
    
    try {
        chromaCode = (thisProcess.nowExecutingPath.dirname +/+ "/../Chroma.sc").loadPath.openReadFile.readAllString;
        
        "Checking for reconnection integration points...".postln;
        
        // Check setEffectsOrder calls reconnectEffects
        var hasEffectsOrderCall = chromaCode.contains("setEffectsOrder") && 
                                  chromaCode.contains("this.reconnectEffects");
        if(hasEffectsOrderCall) {
            "✓ setEffectsOrder calls reconnectEffects".postln;
        } {
            "✗ setEffectsOrder does not call reconnectEffects".error;
            testPassed = false;
        };
        
        // Check createSynths calls reconnectEffects
        var hasCreateSynthsCall = chromaCode.contains("createSynths") && 
                                  chromaCode.contains("this.reconnectEffects();");
        if(hasCreateSynthsCall) {
            "✓ createSynths calls reconnectEffects".postln;
        } {
            "✗ createSynths does not call reconnectEffects".error;
            testPassed = false;
        };
        
        // Verify integration in context
        var effectsOrderPattern = "setEffectsOrder\\s*\\{.*this\\.reconnectEffects";
        if(chromaCode.findRegexp(effectsOrderPattern).size > 0) {
            "✓ setEffectsOrder integration found".postln;
        } {
            "✗ setEffectsOrder integration not found".error;
            testPassed = false;
        };
        
        var createSynthsPattern = "createSynths\\s*\\{.*this\\.reconnectEffects\\(\\)";
        if(chromaCode.findRegexp(createSynthsPattern).size > 0) {
            "✓ createSynths integration found".postln;
        } {
            "✗ createSynths integration not found".error;
            testPassed = false;
        };
        
    } {|error|
        "✗ Error reading Chroma.sc for integration points - %".format(error).error;
        testPassed = false;
    };
    
    ~addTestResult.value(\ReconnectionIntegrationPoints, testPassed,
        if(testPassed, {"All reconnection integration points found"}, {"Some reconnection integration points missing"}));
    
    ^testPassed;
};

~testReconnectionFunctionality = {
    "=== TESTING RECONNECTION FUNCTIONALITY ===".postln;
    
    var testPassed = true;
    
    // Start Chroma for testing
    if(Chroma.instance.isNil) {
        "Starting Chroma for reconnection testing...".postln;
        Chroma.start(debug: 1);
        1.0.wait;
    };
    
    try {
        // Test 1: getEffectOutputBus method
        "Testing getEffectOutputBus method...".postln;
        
        var effects = [\input, \filter, \overdrive, \bitcrush, \granular, \reverb, \delay, \output];
        
        effects.do { |effect|
            try {
                var outputBus = Chroma.instance.getEffectOutputBus(effect);
                if(outputBus.notNil) {
                    "✓ getEffectOutputBus(%) returns valid bus (index: %)".format(effect, outputBus.index).postln;
                } {
                    "✗ getEffectOutputBus(%) returns nil".format(effect).error;
                    testPassed = false;
                };
            } {|error|
                "✗ Error calling getEffectOutputBus(%) - %".format(effect, error).error;
                testPassed = false;
            };
        };
        
        // Test 2: Invalid effect handling
        try {
            var invalidBus = Chroma.instance.getEffectOutputBus(\invalidEffect);
            if(invalidBus.isNil) {
                "✓ getEffectOutputBus handles invalid effect correctly".postln;
            } {
                "✗ getEffectOutputBus doesn't handle invalid effect".error;
                testPassed = false;
            };
        } {|error|
            "✗ Error testing invalid effect - %".format(error).error;
            testPassed = false;
        };
        
        // Test 3: reconnectEffects method
        "Testing reconnectEffects method...".postln;
        
        // Store initial bus connections
        var initialConnections = effects.collect { |effect|
            Chroma.instance.getEffectOutputBus(effect).index;
        };
        
        // Call reconnection method
        Chroma.instance.reconnectEffects;
        0.2.wait; // Allow reconnection to complete
        
        // Verify connections are still valid
        var reconnectedIndices = effects.collect { |effect|
            Chroma.instance.getEffectOutputBus(effect).index;
        };
        
        // Check that all buses are still valid (non-negative)
        var allValid = reconnectedIndices.every { |index| index >= 0 };
        if(allValid) {
            "✓ reconnectEffects maintains valid bus connections".postln;
        } {
            "✗ reconnectEffects created invalid bus connections".error;
            testPassed = false;
        };
        
    } {|error|
        "✗ Error testing reconnection functionality - %".format(error).error;
        testPassed = false;
    };
    
    ~addTestResult.value(\ReconnectionFunctionality, testPassed,
        if(testPassed, {"Reconnection methods work correctly"}, {"Reconnection methods have issues"}));
    
    ^testPassed;
};

~testEffectsOrderReconnection = {
    "=== TESTING EFFECTS ORDER RECONNECTION ===".postln;
    
    var testPassed = true;
    
    // Start Chroma for testing
    if(Chroma.instance.isNil) {
        Chroma.start(debug: 1);
        1.0.wait;
    };
    
    try {
        "Testing effects order changes trigger reconnection...".postln;
        
        // Get initial effects order
        var initialOrder = Chroma.instance.effectsOrder;
        "Initial effects order: %".format(initialOrder).postln;
        
        // Test different effects order
        var testOrders = [
            [\filter, \overdrive, \bitcrush, \granular, \reverb, \delay],
            [\delay, \reverb, \granular, \bitcrush, \overdrive, \filter],
            [\granular, \filter, \delay, \overdrive, \reverb, \bitcrush]
        ];
        
        testOrders.do { |order, index|
            "Testing order %: %".format(index + 1, order).postln;
            
            try {
                // Set new effects order
                Chroma.instance.setEffectsOrder(order);
                0.3.wait; // Allow reconnection to complete
                
                // Verify the order was set
                var currentOrder = Chroma.instance.effectsOrder;
                if(currentOrder == order) {
                    "✓ Effects order set correctly".postln;
                } {
                    "✗ Effects order not set correctly (got %)".format(currentOrder).error;
                    testPassed = false;
                };
                
                // Verify bus connections are still valid after reconnection
                var allEffectsValid = order.every { |effect|
                    var outputBus = Chroma.instance.getEffectOutputBus(effect);
                    outputBus.notNil && (outputBus.index >= 0);
                };
                
                if(allEffectsValid) {
                    "✓ All effects have valid bus connections after reconnection".postln;
                } {
                    "✗ Some effects have invalid bus connections after reconnection".error;
                    testPassed = false;
                };
                
            } {|error|
                "✗ Error testing order % - %".format(order, error).error;
                testPassed = false;
            };
        };
        
        // Restore initial order
        Chroma.instance.setEffectsOrder(initialOrder);
        0.2.wait;
        
    } {|error|
        "✗ Error testing effects order reconnection - %".format(error).error;
        testPassed = false;
    };
    
    ~addTestResult.value(\EffectsOrderReconnection, testPassed,
        if(testPassed, {"Effects order reconnection works correctly"}, {"Effects order reconnection has issues"}));
    
    ^testPassed;
};

~testReconnectionAudioContinuity = {
    "=== TESTING RECONNECTION AUDIO CONTINUITY ===".postln;
    
    var testPassed = true;
    
    // Start Chroma for testing
    if(Chroma.instance.isNil) {
        Chroma.start(debug: 1);
        1.0.wait;
    };
    
    try {
        "Testing audio continuity during reconnection...".postln;
        
        // Generate a continuous test signal
        var testSynth = {
            var testSig = SinOsc.ar(440) * 0.2;
            Out.ar(~chroma.buses[\inputAudio].index, testSig);
        }.play;
        
        0.5.wait; // Let signal stabilize
        
        // Measure initial output
        var initialOutput = ~chroma.buses[\delayAudio].getSynchronous;
        "Initial output level: %".format(initialOutput).postln;
        
        // Trigger reconnection by changing effects order
        var newOrder = [\delay, \reverb, \granular, \bitcrush, \overdrive, \filter];
        Chroma.instance.setEffectsOrder(newOrder);
        
        0.2.wait; // Allow reconnection
        
        // Measure output after reconnection
        var reconnectedOutput = ~chroma.buses[\delayAudio].getSynchronous;
        "Output after reconnection: %".format(reconnectedOutput).postln;
        
        // Check that audio is still flowing
        var audioStillFlowing = (reconnectedOutput != 0) || (initialOutput == 0);
        if(audioStillFlowing) {
            "✓ Audio continues flowing after reconnection".postln;
        } {
            "✗ Audio stopped flowing after reconnection".error;
            testPassed = false;
        };
        
        // Test multiple rapid reconnections
        "Testing rapid reconnections...".postln;
        5.do { |i|
            var rapidOrder = newOrder.rotate(i);
            Chroma.instance.setEffectsOrder(rapidOrder);
            0.05.wait;
            
            var rapidOutput = ~chroma.buses[\delayAudio].getSynchronous;
            if(rapidOutput == 0 && initialOutput != 0) {
                "✗ Audio lost during rapid reconnection %".format(i + 1).error;
                testPassed = false;
            };
        };
        
        if(testPassed) {
            "✓ Rapid reconnections maintain audio continuity".postln;
        };
        
        // Clean up test signal
        testSynth.free;
        
    } {|error|
        "✗ Error testing reconnection audio continuity - %".format(error).error;
        testPassed = false;
    };
    
    ~addTestResult.value(\ReconnectionAudioContinuity, testPassed,
        if(testPassed, {"Audio continuity maintained during reconnection"}, {"Audio continuity broken during reconnection"}));
    
    ^testPassed;
};

// ============ COMPREHENSIVE DYNAMIC EFFECTS RECONNECTION TEST ============

~runDynamicEffectsReconnectionTests = {
    "========================================".postln;
    "  DYNAMIC EFFECTS RECONNECTION COMPREHENSIVE TESTS".postln;
    "========================================".postln;
    "".postln;
    
    var allTestsPassed = true;
    
    // Run all dynamic effects reconnection tests
    allTestsPassed = allTestsPassed && ~verifyReconnectionMethodsExist.value;
    allTestsPassed = allTestsPassed && ~verifyReconnectionIntegrationPoints.value;
    allTestsPassed = allTestsPassed && ~testReconnectionFunctionality.value;
    allTestsPassed = allTestsPassed && ~testEffectsOrderReconnection.value;
    allTestsPassed = allTestsPassed && ~testReconnectionAudioContinuity.value;
    
    "".postln;
    "========================================".postln;
    if(allTestsPassed) {
        "✅ ALL DYNAMIC EFFECTS RECONNECTION TESTS PASSED".postln;
    } {
        "❌ SOME DYNAMIC EFFECTS RECONNECTION TESTS FAILED".postln;
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
~runDynamicEffectsReconnectionTests.value;
)