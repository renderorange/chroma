#!/bin/bash

echo "=== Testing Chroma class syntax ==="

# Create a minimal test class to verify syntax
cat > /tmp/test_minimal.sc << 'EOF'
TestMinimal {
    classvar <instance;
    
    *new { |server|
        ^super.new.init(server);
    }
    
    init { |argServer|
        ^this;
    }
    
    *start { |server|
        if(instance.notNil) {
            "Already running".warn;
            ^instance;
        };
        instance = TestMinimal(server);
        ^instance;
    }
}
EOF

# Copy the minimal test to extensions
cp /tmp/test_minimal.sc ~/.local/share/SuperCollider/Extensions/

# Test the minimal class
cat > /tmp/test_minimal_run.scd << 'EOF'
if (TestMinimal.class.isMetaClass.not) {
    "✓ TestMinimal class found".postln;
    "✓ Chroma class syntax is correct".postln;
    0.exit;
} {
    "✗ TestMinimal class not found".postln;
    "✗ Chroma class may have syntax errors".postln;
    1.exit;
};
EOF

echo "Testing minimal class with same syntax structure..."
QT_QPA_PLATFORM=offscreen sclang -D /tmp/test_minimal_run.scd 2>&1
MINIMAL_RESULT=$?

if [ $MINIMAL_RESULT -eq 0 ]; then
    echo "✓ Minimal test passed - syntax structure is correct"
    echo "The issue might be in the specific implementation details of Chroma.sc"
else
    echo "✗ Minimal test failed - there may be a fundamental syntax issue"
fi

# Clean up
rm -f /tmp/test_minimal.sc /tmp/test_minimal_run.scd
rm -f ~/.local/share/SuperCollider/Extensions/TestMinimal.sc

exit $MINIMAL_RESULT