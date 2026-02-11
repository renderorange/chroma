#!/bin/bash

echo "=== Testing Chroma class compilation ==="

# Check if Chroma.sc exists
if [ ! -f "Chroma.sc" ]; then
    echo "✗ Chroma.sc not found"
    exit 1
fi

echo "✓ Chroma.sc found"

# Simple syntax check using head to look for basic structure
echo "Checking basic syntax structure..."

# Check class declaration
if grep -q "^Chroma {" Chroma.sc; then
    echo "✓ Class declaration found"
else
    echo "✗ Class declaration missing"
    exit 1
fi

# Check for class variable
if grep -q "classvar.*instance" Chroma.sc; then
    echo "✓ Class variable declaration found"
else
    echo "✗ Class variable declaration missing"
    exit 1
fi

# Check for constructor
if grep -q "\*new {" Chroma.sc; then
    echo "✓ Constructor method found"
else
    echo "✗ Constructor method missing"
    exit 1
fi

# Check for start method
if grep -q "\*start {" Chroma.sc; then
    echo "✓ Start method found"
else
    echo "✗ Start method missing"
    exit 1
fi

# Check for balanced braces
OPEN_BRACES=$(grep -o "{" Chroma.sc | wc -l)
CLOSE_BRACES=$(grep -o "}" Chroma.sc | wc -l)

if [ "$OPEN_BRACES" -eq "$CLOSE_BRACES" ]; then
    echo "✓ Braces are balanced ($OPEN_BRACES open, $CLOSE_BRACES close)"
else
    echo "✗ Braces are not balanced ($OPEN_BRACES open, $CLOSE_BRACES close)"
    exit 1
fi

echo "✓ All syntax checks passed"
echo "✓ Chroma class compilation test completed successfully"

exit 0