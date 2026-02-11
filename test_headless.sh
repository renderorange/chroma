#!/bin/bash

# Headless SuperCollider test script
# Tests Chroma class compilation and basic functionality

echo "=== Running headless Chroma test ==="

# Create test script for sclang to execute
cat > /tmp/test_headless.scd << 'EOF'
// Test that Chroma class can be loaded and basic methods work
"Loading Chroma class...".postln;

try {
    // Load Chroma class
    thisProcess.interpreter.executeFile("/home/blaine/git/chroma/Chroma.sc");
    "✓ Chroma class loaded successfully".postln;
    
    // Test basic instantiation without a server
    var testServer = Server(\test, NetAddr("127.0.0.1", 57110));
    testServer.isLocal = false;
    
    var instance = Chroma.new(testServer);
    "✓ Chroma instance created".postln;
    
    // Test basic methods that don't require server connection
    instance.setBlendMode(\mirror);
    instance.setFilterAmount(0.5);
    instance.setDryWet(0.5);
    "✓ Basic control methods work".postln;
    
    // Test blend mode methods
    var modeIndex = instance.blendModeIndex;
    "✓ Blend mode index: %".format(modeIndex).postln;
    
    "=== ALL TESTS PASSED ===".postln;
    
} {|error|
    "ERROR: %".format(error).postln;
    error.backtrace.postln;
    "=== TEST FAILED ===".postln;
};
EOF

# Run test with sclang in headless mode
QT_QPA_PLATFORM=offscreen sclang -i /tmp/test_headless.scd
TEST_RESULT=$?

# Clean up
rm -f /tmp/test_headless.scd

if [ $TEST_RESULT -eq 0 ]; then
    echo "✓ Headless test passed"
    exit 0
else
    echo "✗ Headless test failed"
    exit 1
fi