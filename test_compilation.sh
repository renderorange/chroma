#!/bin/bash

# Simple test to verify Chroma class compilation
# Tests basic functionality without audio server

echo "=== Testing Chroma class compilation ==="

# Create simple compilation test
cat > /tmp/test_compilation.scd << 'EOF'
// Test basic class compilation
"Testing Chroma class compilation...".postln;

// Check if class exists
if (Chroma.class.isMetaClass.not) {
    "✓ Chroma class found".postln;
} {
    "✗ Chroma class not found".postln;
    1.exit;
};

// Test class methods
"Testing class methods...".postln;

// Test start method exists
if (Chroma.class.respondsTo(\start)) {
    "✓ start method exists".postln;
} {
    "✗ start method missing".postln;
    1.exit;
};

// Test basic instantiation (without server)
try {
    var classInfo;
    classInfo = Chroma.class;
    "✓ Class can be accessed".postln;
    "✓ All basic tests passed".postln;
    0.exit;
} { |error|
    ("✗ Error: " ++ error).postln;
    1.exit;
};
EOF

# Run compilation test
sclang /tmp/test_compilation.scd 2>/dev/null
TEST_RESULT=$?

# Clean up
rm -f /tmp/test_compilation.scd

if [ $TEST_RESULT -eq 0 ]; then
    echo "✓ Compilation test passed"
    exit 0
else
    echo "✗ Compilation test failed"
    exit 1
fi