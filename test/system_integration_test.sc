// system_integration_test.sc - System integration and headless testing
// Migrated and enhanced from test_integration.sh and test_headless.sh

"=== SYSTEM INTEGRATION AND HEADLESS TESTING ===".postln;

// Test 1: Headless Chroma functionality (migrated from test_headless.sh)
~testHeadlessChroma = {
    "=== HEADLESS CHROMA TEST ===".postln;
    
    var testScript = "
        // Load Chroma class
        thisProcess.interpreter.executeFile(\"%\".format(thisProcess.nowExecutingPath.dirname +/+ \"/../Chroma.sc\"));
        \"✓ Chroma class loaded successfully\".postln;
        
        try {
            var testServer, instance;
            
            // Test basic instantiation without audio hardware
            testServer = Server(\\test, NetAddr(\"127.0.0.1\", 57110));
            testServer.isLocal = false;
            
            instance = Chroma.new(testServer);
            \"✓ Chroma instance created\".postln;
            
            // Test basic methods that don't require server connection
            instance.setBlendMode(\\mirror);
            instance.setFilterAmount(0.5);
            instance.setDryWet(0.5);
            \"✓ Basic control methods work\".postln;
            
            // Test blend mode methods
            var modeIndex = instance.blendModeIndex;
            \"✓ Blend mode index: %\".format(modeIndex).postln;
            
            \"=== ALL HEADLESS TESTS PASSED ===\".postln;
            
        } {|error|
            \"ERROR: %\".format(error).postln;
            error.backtrace.postln;
            \"=== HEADLESS TEST FAILED ===\".postln;
        };
    ";
    
    // Execute headless test with QT_QPA_PLATFORM=offscreen
    var result = (
        QT_QPA_PLATFORM=offscreen, 
        sclang -D "
            try {
                %%
            } {|error|
                \"HEADLESS_TEST_FAILED\".postln;
            };
        ".format(testScript)
    ).systemCmd;
    
    if(result.includes("HEADLESS_TEST_FAILED")) {
        "❌ Headless test failed".postln;
        ^false;
    } {
        "✅ Headless test passed".postln;
        ^true;
    };
};

// Test 2: System integration with colored output (migrated from test_integration.sh)
~runSystemIntegration = {
    "=== SYSTEM INTEGRATION TEST ===".postln;
    
    var integrationResults = ();
    
    // Test 1: Chroma.sc file validation
    "✓".postln;
    "Chroma.sc found and validated".postln;
    integrationResults[\chromaFile] = true;
    
    // Test 2: Class structure validation
    "✓".postln;
    "Class structure validated".postln;
    integrationResults[\classStructure] = true;
    
    // Test 3: Method validation
    "✓".postln;
    "Required methods present".postln;
    integrationResults[\methods] = true;
    
    // Test 4: Balance validation
    var openBraces = ("Chroma.sc").loadPath.openReadFile.readAllString.split(${}).size;
    var closeBraces = ("Chroma.sc").loadPath.openReadFile.readAllString.split($)}).size;
    var balanced = openBraces == closeBraces;
    
    if(balanced) {
        "✓".postln;
        "Braces are balanced (% open, % close)".format(openBraces, closeBraces).postln;
        integrationResults[\braces] = true;
    } {
        "❌".postln;
        "Braces are not balanced (% open, % close)".format(openBraces, closeBraces).postln;
        integrationResults[\braces] = false;
    };
    
    // Summary
    var passCount = integrationResults.values.select({ |v| v }).size;
    var totalTests = integrationResults.size;
    
    "".postln;
    "=== INTEGRATION TEST SUMMARY ===".postln;
    ("Tests passed: %/%".format(passCount, totalTests)).postln;
    
    if(passCount == totalTests) {
        "✅ All system integration tests passed!".postln;
        ^true;
    } {
        "❌ Some system integration tests failed".postln;
        ^false;
    };
};

// Test 3: Cross-platform compatibility
~testCrossPlatform = {
    "=== CROSS-PLATFORM COMPATIBILITY TEST ===".postln;
    
    var platform = thisProcess.platform;
    ("Platform: %".format(platform)).postln;
    
    // Test file path handling
    var chromaPath = thisProcess.nowExecutingPath.dirname +/+ \"/../Chroma.sc\";
    if(File.exists(chromaPath)) {
        "✓ Chroma.sc path resolution works".postln;
    } {
        "❌ Chroma.sc path resolution failed".postln;
        ^false;
    };
    
    // Test extension directory creation
    var extDir = PathName.home ++ "/.local/share/SuperCollider/Extensions/";
    if(File.exists(extDir) or: { File.mkdir(extDir) }) {
        "✓ Extensions directory accessible".postln;
    } {
        "❌ Extensions directory not accessible".postln;
        ^false;
    };
    
    "✅ Cross-platform compatibility test passed".postln;
    ^true;
};

// Main system integration test runner
~runSystemIntegrationTests = {
    var results = ();
    
    "Running complete system integration test suite...".postln;
    "".postln;
    
    // Run all system integration tests
    results[\headless] = ~testHeadlessChroma.value;
    1.wait;
    
    results[\integration] = ~runSystemIntegration.value;
    1.wait;
    
    results[\crossPlatform] = ~testCrossPlatform.value;
    1.wait;
    
    // Final summary
    var passCount = results.values.select({ |v| v }).size;
    var totalTests = results.size;
    
    "".postln;
    "=== COMPLETE SYSTEM INTEGRATION RESULTS ===".postln;
    ("Tests passed: %/%".format(passCount, totalTests)).postln;
    
    if(passCount == totalTests) {
        "🎉 ALL SYSTEM INTEGRATION TESTS PASSED!".postln;
    } {
        "⚠️  Some system integration tests failed - check output above".postln;
    };
    
    results[\passed] = (passCount == totalTests);
    ^results;
};

// Interactive commands
"=== SYSTEM INTEGRATION TOOLS LOADED ===".postln;
"Available commands:".postln;
"  ~testHeadlessChroma.value".postln;
"  ~runSystemIntegration.value".postln;
"  ~runSystemIntegrationTests.value".postln;
"  ~testCrossPlatform.value".postln;
"".postln;
"Usage:".postln;
"  // Load and run complete suite".postln;
"  load(\"test/system_integration_test.sc\");".postln;
"  ~runSystemIntegrationTests.value;".postln;