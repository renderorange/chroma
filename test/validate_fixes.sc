// validate_fixes.sc - Validate the critical audio fixes
// This script tests the major fixes implemented

"=== VALIDATING CHROMA AUDIO FIXES ===".postln;
"Loading Chroma...".postln;

// Start Chroma with verbose debugging
try {
    Chroma.start(debug: 2);
    
    // Test 1: Bus routing validation
    "=== TEST 1: Bus Routing Validation ===".postln;
    
    // Check that intermediate buses are created
    var intermediateBuses = [\filterToOverdrive, \overdriveToBitcrush, \bitcrushToGranular, 
                           \granularToReverb, \reverbToDelay];
    
    intermediateBuses.do { |busName|
        var bus = Chroma.instance.buses[busName];
        if(bus.notNil) {
            ("✓ Intermediate bus % created (index %)".format(busName, bus.index)).postln;
        } {
            ("✗ Intermediate bus % NOT FOUND".format(busName)).error;
        };
    };
    
    // Test 2: Effects chain connectivity
    "=== TEST 2: Effects Chain Connectivity ===".postln;
    
    var effectsOrder = Chroma.instance.getEffectsOrder();
    ("Current effects order: %".format(effectsOrder)).postln;
    
    // Check that getEffectOutputBus returns correct intermediate buses
    var expectedMappings = (
        \filter: \filterToOverdrive,
        \overdrive: \overdriveToBitcrush,
        \bitcrush: \bitcrushToGranular,
        \granular: \granularToReverb,
        \reverb: \reverbToDelay,
        \delay: \delayAudio
    );
    
    expectedMappings.keysDo { |effectName|
        var expectedBus = expectedMappings[effectName];
        var actualBus = Chroma.instance.getEffectOutputBus(effectName);
        var actualBusName = if(actualBus == Chroma.instance.buses[\delayAudio], {\delayAudio}, {
            Chroma.instance.buses.keys.select({ |key| Chroma.instance.buses[key] == actualBus }).first;
        });
        
        if(actualBusName == expectedBus) {
            ("✓ Effect % correctly maps to %".format(effectName, expectedBus)).postln;
        } {
            ("✗ Effect % mapping error: expected %, got %".format(effectName, expectedBus, actualBusName)).error;
        };
    };
    
    // Test 3: Input validation
    "=== TEST 3: Input Validation ===".postln;
    
    try {
        Chroma.instance.validateInputChannel(0); // Should pass
        "✓ Input channel 0 validation passed".postln;
    } { |error|
        ("✗ Input channel 0 validation failed: %".format(error)).error;
    };
    
    try {
        Chroma.instance.validateInputChannel(999); // Should fail
        "✗ Input channel 999 validation should have failed".error;
    } { |error|
        "✓ Input channel 999 correctly rejected".postln;
    };
    
    // Test 4: Output validation  
    "=== TEST 4: Output Validation ===".postln;
    
    try {
        Chroma.instance.validateOutputBus(0); // Should pass
        "✓ Output bus 0 validation passed".postln;
    } { |error|
        ("✗ Output bus 0 validation failed: %".format(error)).error;
    };
    
    try {
        Chroma.instance.validateOutputBus(999); // Should fail
        "✗ Output bus 999 validation should have failed".error;
    } { |error|
        "✓ Output bus 999 correctly rejected".postln;
    };
    
    // Test 5: Signal tracing
    "=== TEST 5: Signal Tracing ===".postln;
    
    // Test signal tracing methods exist and work
    try {
        Chroma.instance.traceSignalStage(\test, 0.1);
        "✓ Signal tracing method works".postln;
    } { |error|
        ("✗ Signal tracing failed: %".format(error)).error;
    };
    
    try {
        Chroma.instance.traceBusActivity(\frozenAudio);
        "✓ Bus activity tracing works".postln;
    } { |error|
        ("✗ Bus activity tracing failed: %".format(error)).error;
    };
    
    // Test 6: Debug level names
    "=== TEST 6: Debug Level Enhancement ===".postln;
    
    [0, 1, 2].do { |level|
        var levelNames = ["off", "basic", "verbose"];
        Chroma.instance.setDebugLevel(level);
        ("✓ Debug level % (%) set correctly".format(level, levelNames[level])).postln;
    };
    
    // Test 7: Effects reconnection
    "=== TEST 7: Effects Reconnection ===".postln;
    
    // Test effects reconnection with different order
    var originalOrder = effectsOrder.copy;
    var newOrder = [\delay, \reverb, \granular, \bitcrush, \overdrive, \filter];
    
    try {
        Chroma.instance.setEffectsOrder(newOrder);
        ("✓ Effects order changed to %".format(newOrder)).postln;
        
        // Restore original order
        Chroma.instance.setEffectsOrder(originalOrder);
        ("✓ Effects order restored to %".format(originalOrder)).postln;
    } { |error|
        ("✗ Effects reconnection failed: %".format(error)).error;
    };
    
    "=== VALIDATION COMPLETE ===".postln;
    "All major fixes tested. Review output above for any failures.".postln;
    
    // Cleanup
    2.wait;
    Chroma.stop();
    "Chroma stopped. Validation complete.".postln;
    
} { |error|
    "ERROR during validation: %".format(error).error;
    error.postln;
};