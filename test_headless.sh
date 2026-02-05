#!/bin/bash

# Headless SuperCollider test script
# Uses scsynth directly without Qt dependencies

echo "=== Starting headless SuperCollider server ==="

# Start scsynth server in background
scsynth -u 57111 -D 0 -d dummy -R 0 > /dev/null 2>&1 &
SCSYNTH_PID=$!

# Wait for server to start
sleep 2

echo "=== Running test commands ==="

# Create test script for sclang to execute
cat > /tmp/test_headless.scd << 'EOF'
// Connect to running server
s = Server("default", NetAddr("127.0.0.1", 57111));
s.options.sampleRate = 48000;
s.options.blockSize = 64;
s.options.numOutputBusChannels = 2;
s.options.numInputBusChannels = 2;

s.boot;

fork {
    var instance;
    
    "Server booted".postln;
    
    // Create Chroma instance
    instance = Chroma(s);
    
    "Allocating resources...".postln;
    instance.allocateResources;
    s.sync;
    "  - Resources allocated".postln;
    
    "Loading SynthDefs...".postln;
    instance.loadSynthDefs;
    s.sync;
    "  - SynthDefs loaded".postln;
    
    "Creating synths...".postln;
    instance.createSynths;
    s.sync;
    "  - Synths created".postln;
    
    "Testing basic controls...".postln;
    instance.setBlendMode(\mirror);
    instance.setFilterAmount(0.5);
    instance.setDryWet(0.5);
    
    "Cleanup...".postln;
    instance.cleanup;
    
    "=== ALL TESTS PASSED ===".postln;
    0.exit;
};
EOF

# Run test with sclang in non-interactive mode
sclang -i /tmp/test_headless.scd 2>/dev/null
TEST_RESULT=$?

# Clean up
kill $SCSYNTH_PID 2>/dev/null
rm -f /tmp/test_headless.scd

if [ $TEST_RESULT -eq 0 ]; then
    echo "✓ Headless test passed"
    exit 0
else
    echo "✗ Headless test failed"
    exit 1
fi