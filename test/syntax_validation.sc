// syntax_validation.sc - Comprehensive syntax and compilation validation
// Migrated and enhanced from test_compilation.sh and test_syntax.sh

"=== COMPREHENSIVE SYNTAX VALIDATION ===".postln;

// Test 1: Basic Class Structure Validation
~validateBasicStructure = {
    "=== VALIDATING BASIC CLASS STRUCTURE ===".postln;
    
    var testPassed = true;
    
    try {
        // Load Chroma class for validation
        thisProcess.interpreter.executeFile(thisProcess.nowExecutingPath.dirname +/+ "/../Chroma.sc");
        "✓ Chroma class loaded successfully".postln;
        
        // Test 1: Check class declaration
        var classFound = "Chroma".asClass.notNil;
        if(classFound) {
            "✓ Class declaration found".postln;
        } {
            "✗ Class declaration not found".error;
            testPassed = false;
        };
        
        // Test 2: Check class variable
        var instanceVar = "instance".asSymbol.asClass.getVar(\instance);
        if(instanceVar.notNil) {
            "✓ Class variable declaration found".postln;
        } {
            "✗ Class variable declaration not found".error;
            testPassed = false;
        };
        
        // Test 3: Check constructor method
        var constructorFound = "new".asSymbol.asClass.getVar(\new).notNil;
        if(constructorFound) {
            "✓ Constructor method found".postln;
        } {
            "✗ Constructor method not found".error;
            testPassed = false;
        };
        
        // Test 4: Check start method
        var startMethod = "start".asSymbol.asClass.getVar(\start).notNil;
        if(startMethod) {
            "✓ Start method found".postln;
        } {
            "✗ Start method not found".error;
            testPassed = false;
        };
        
    } { |error|
        "ERROR: Basic structure validation failed: %".format(error).postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\BasicStructure, testPassed,
        if(testPassed, {"Basic class structure valid"}, {"Basic class structure validation failed"}));
};

// Test 2: Advanced Structure Validation
~validateAdvancedStructure = {
    "=== VALIDATING ADVANCED CLASS STRUCTURE ===".postln;
    
    var testPassed = true;
    
    try {
        // Load Chroma class
        thisProcess.interpreter.executeFile(thisProcess.nowExecutingPath.dirname +/+ "/../Chroma.sc");
        
        // Test 1: Check for required methods
        var requiredMethods = [\boot, \cleanup, \allocateResources, \loadSynthDefs, 
                              \createSynths, \reconnectEffects];
        
        requiredMethods.do { |methodName|
            var methodFound = methodName.asSymbol.asClass.getVar(methodName).notNil;
            if(methodFound) {
                ("✓ Method % found".format(methodName)).postln;
            } {
                ("✗ Method % not found".format(methodName)).error;
                testPassed = false;
            };
        };
        
        // Test 2: Check for OSC handlers
        var oscHandlers = [\setupOSC, \cleanupOSC];
        oscHandlers.do { |handlerName|
            var handlerFound = handlerName.asSymbol.asClass.getVar(handlerName).notNil;
            if(handlerFound) {
                ("✓ OSC handler % found".format(handlerName)).postln;
            } {
                ("✗ OSC handler % not found".format(handlerName)).error;
                testPassed = false;
            };
        };
        
        // Test 3: Check balance validation
        var chromaCode = (thisProcess.nowExecutingPath.dirname +/+ "/../Chroma.sc").loadPath.openReadFile.readAllString;
        var openBraces = chromaCode.split(${}).size;
        var closeBraces = chromaCode.split($)}).size;
        
        if(openBraces == closeBraces) {
            "✓ Braces are balanced (% open, % close)".format(openBraces, closeBraces).postln;
        } {
            ("✗ Braces are not balanced (% open, % close)".format(openBraces, closeBraces)).error;
            testPassed = false;
        };
        
    } { |error|
        "ERROR: Advanced structure validation failed: %".format(error).postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\AdvancedStructure, testPassed,
        if(testPassed, {"Advanced class structure valid"}, {"Advanced class structure validation failed"}));
};

// Test 3: SuperCollider Compatibility
~validateSCCompatibility = {
    "=== VALIDATING SUPERCOLLIDER COMPATIBILITY ===".postln;
    
    var testPassed = true;
    
    try {
        // Test 1: Check SuperCollider version compatibility
        var scVersion = thisProcess.version;
        ("SuperCollider version: %".format(scVersion)).postln;
        
        // Test 2: Check required classes availability
        var requiredClasses = [Server, Bus, Buffer, Synth, SynthDef, OSCdef, NetAddr];
        requiredClasses.do { |className|
            var classAvailable = className.asClass.notNil;
            if(classAvailable) {
                ("✓ Class % available".format(className)).postln;
            } {
                ("✗ Class % not available".format(className)).error;
                testPassed = false;
            };
        };
        
        // Test 3: Check for deprecated methods
        if(PlayBuf.respondsTo(\rate) && { "⚠️ PlayBuf.rate is deprecated in current SC version".postln; });
        
        // Test 4: Check server options compatibility
        var serverOptions = Server.options;
        ("Server options available: sampleRate=%, blockSize=%, memSize=%".format(
            serverOptions.sampleRate, serverOptions.blockSize, serverOptions.memSize)).postln;
        
    } { |error|
        "ERROR: SC compatibility validation failed: %".format(error).postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\SCCompatibility, testPassed,
        if(testPassed, {"SuperCollider compatibility confirmed"}, {"SuperCollider compatibility issues found"}));
};

// Test 4: Cross-Platform File Handling
~validateFileHandling = {
    "=== VALIDATING FILE HANDLING ===".postln;
    
    var testPassed = true;
    
    try {
        var chromaPath = thisProcess.nowExecutingPath.dirname +/+ "/../Chroma.sc";
        
        // Test 1: Check file existence
        if(File.exists(chromaPath)) {
            "✓ Chroma.sc file exists".postln;
        } {
            "✗ Chroma.sc file not found".error;
            testPassed = false;
        };
        
        // Test 2: Check file readability
        if(File.exists(chromaPath) && { File.readable(chromaPath) }) {
            "✓ Chroma.sc file readable".postln;
        } {
            "✗ Chroma.sc file not readable".error;
            testPassed = false;
        };
        
        // Test 3: Check path resolution
        var platform = thisProcess.platform;
        ("Platform: %".format(platform)).postln;
        
        var testPaths = [
            thisProcess.nowExecutingPath.dirname +/+ "/../Chroma.sc",
            thisProcess.nowExecutingPath.parent +/+ "/Chroma.sc",
            "./Chroma.sc"
        ];
        
        testPaths.do { |path, i|
            if(File.exists(path)) {
                ("✓ Path resolution % works".format(path)).postln;
            } {
                ("⚠️ Path resolution % failed".format(path)).postln;
            };
        };
        
    } { |error|
        "ERROR: File handling validation failed: %".format(error).postln;
        testPassed = false;
    };
    
    ~addTestResult.value(\FileHandling, testPassed,
        if(testPassed, {"File handling working"}, {"File handling issues found"}));
};

// Main syntax validation runner
~runSyntaxValidationTests = {
    "=== RUNNING COMPREHENSIVE SYNTAX VALIDATION ===".postln;
    "".postln;
    
    ~testResults = []; // Clear previous results
    
    // Run all syntax validation tests
    ~validateBasicStructure.value;
    0.5.wait;
    
    ~validateAdvancedStructure.value;
    0.5.wait;
    
    ~validateSCCompatibility.value;
    0.5.wait;
    
    ~validateFileHandling.value;
    
    // Summary
    "".postln;
    "=== SYNTAX VALIDATION SUMMARY ===".postln;
    var totalTests = ~testResults.size;
    var passedTests = ~testResults.count({ |result| result.passed });
    var failedTests = totalTests - passedTests;
    
    ("Total syntax tests: %".format(totalTests)).postln;
    ("Passed: %".format(passedTests)).postln;
    ("Failed: %".format(failedTests)).postln;
    ("Success rate: %%%".format((passedTests/totalTests * 100).round(1))).postln;
    
    if(failedTests > 0) {
        "".postln;
        "FAILED SYNTAX TESTS:".postln;
        ~testResults.select({ |result| result.passed.not }).do { |result|
            ("  %: %".format(result.test, result.message)).postln;
        };
    };
    
    "=== SYNTAX VALIDATION COMPLETE ===".postln;
    
    ^failedTests == 0; // Return true if all tests passed
};

// Interactive commands
"=== COMPREHENSIVE SYNTAX VALIDATION TOOLS LOADED ===".postln;
"Available commands:".postln;
"  ~runSyntaxValidationTests.value".postln;
"  ~validateBasicStructure.value".postln;
"  ~validateAdvancedStructure.value".postln;
"  ~validateSCCompatibility.value".postln;
"  ~validateFileHandling.value".postln;
"".postln;
"Usage:".postln;
"  ~runSyntaxValidationTests.value; // Run complete syntax validation suite".postln;
"  // Individual tests for specific debugging".postln;