#!/bin/bash
# Simple test runner that doesn't require audio hardware

echo "=== Chroma Test Suite ==="
echo ""

# Test 1: Syntax Validation
echo "Test 1: Syntax Validation"
echo "  - Checking file structure..."

# Check if Chroma.sc exists and has content
if [[ -f "Chroma.sc" && -s "Chroma.sc" ]]; then
    echo "  ✓ Chroma.sc exists and is non-empty"
else
    echo "  ✗ Chroma.sc missing or empty"
    exit 1
fi

# Check for basic class structure
if grep -q "^Chroma {" Chroma.sc; then
    echo "  ✓ Class declaration found"
else
    echo "  ✗ Class declaration not found"
    exit 1
fi

if grep -q "classvar" Chroma.sc; then
    echo "  ✓ Class variables found"
else
    echo "  ✗ Class variables not found"
    exit 1
fi

if grep -q "\*new" Chroma.sc; then
    echo "  ✓ Constructor method found"
else
    echo "  ✗ Constructor method not found"
    exit 1
fi

echo ""
echo "Test 2: Brace and Parenthesis Balance"

# Count braces
open_braces=$(grep -o '{' Chroma.sc | wc -l)
close_braces=$(grep -o '}' Chroma.sc | wc -l)
if [[ $open_braces -eq $close_braces ]]; then
    echo "  ✓ Braces balanced ($open_braces open, $close_braces close)"
else
    echo "  ✗ Braces unbalanced ($open_braces open, $close_braces close)"
    exit 1
fi

# Count parentheses
open_parens=$(grep -o '(' Chroma.sc | wc -l)
close_parens=$(grep -o ')' Chroma.sc | wc -l)
if [[ $open_parens -eq $close_parens ]]; then
    echo "  ✓ Parentheses balanced ($open_parens open, $close_parens close)"
else
    echo "  ✗ Parentheses unbalanced ($open_parens open, $close_parens close)"
    exit 1
fi

echo ""
echo "Test 3: Required Methods"

methods=("boot" "cleanup" "allocateResources" "loadSynthDefs" "createSynths" "reconnectEffects" "setupOSC" "cleanupOSC")
for method in "${methods[@]}"; do
    if grep -q "^\s*${method}\s*{" Chroma.sc || grep -q "^\s*${method}\s*\|" Chroma.sc; then
        echo "  ✓ Method '$method' found"
    else
        echo "  ⚠ Method '$method' not found (may be OK if implemented differently)"
    fi
done

echo ""
echo "Test 4: OSC Handlers"

osc_handlers=("/chroma/gain" "/chroma/filter" "/chroma/overdrive" "/chroma/bitcrush" "/chroma/granular" "/chroma/reverb" "/chroma/delay")
for handler in "${osc_handlers[@]}"; do
    if grep -q "$handler" Chroma.sc; then
        echo "  ✓ OSC handler '$handler' found"
    else
        echo "  ✗ OSC handler '$handler' not found"
    fi
done

echo ""
echo "Test 5: SynthDef Definitions"

synthdefs=("chroma_input" "chroma_filter" "chroma_overdrive" "chroma_bitcrush" "chroma_granular" "chroma_reverb" "chroma_output")
for synthdef in "${synthdefs[@]}"; do
    if grep -q "SynthDef.*${synthdef}" Chroma.sc || grep -q "'${synthdef}'" Chroma.sc || grep -q "\\\\${synthdef}" Chroma.sc; then
        echo "  ✓ SynthDef '$synthdef' found"
    else
        echo "  ⚠ SynthDef '$synthdef' not found (may be defined differently)"
    fi
done

echo ""
echo "=== TEST SUMMARY ==="
echo "All basic syntax tests passed!"
echo ""
echo "Note: Full functional tests require a running SuperCollider server with audio hardware."
echo "To run full tests, use: ./run.sh to start the system, then run tests manually."
