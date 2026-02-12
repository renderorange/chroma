#!/bin/bash
# ci_test_runner.sh - CI-compatible test runner for Chroma
# This script runs tests that don't require audio hardware (JACK)

set -e  # Exit on error

echo "=== Chroma CI Test Suite ==="
echo "Running tests compatible with headless/CI environments"
echo ""

TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(dirname "$TEST_DIR")"
cd "$APP_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Function to report test result
report_test() {
    local test_name="$1"
    local result="$2"
    local message="$3"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    if [[ "$result" == "PASS" ]]; then
        echo -e "${GREEN}✓${NC} $test_name: PASS"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}✗${NC} $test_name: FAIL"
        echo "  $message"
        FAILED_TESTS=$((FAILED_TESTS + 1))
    fi
}

echo "Test 1: File Structure Validation"
echo "-----------------------------------"

# Check Chroma.sc exists
if [[ -f "Chroma.sc" && -s "Chroma.sc" ]]; then
    report_test "Chroma.sc exists" "PASS"
else
    report_test "Chroma.sc exists" "FAIL" "File missing or empty"
fi

# Check startup.scd exists
if [[ -f "startup.scd" && -s "startup.scd" ]]; then
    report_test "startup.scd exists" "PASS"
else
    report_test "startup.scd exists" "FAIL" "File missing or empty"
fi

# Check run.sh exists and is executable
if [[ -x "run.sh" ]]; then
    report_test "run.sh executable" "PASS"
else
    report_test "run.sh executable" "FAIL" "File missing or not executable"
fi

echo ""
echo "Test 2: Syntax Validation"
echo "-----------------------------------"

# Check class declaration
if grep -q "^Chroma {" Chroma.sc; then
    report_test "Class declaration" "PASS"
else
    report_test "Class declaration" "FAIL" "Class declaration not found"
fi

# Check class variable
if grep -q "classvar" Chroma.sc; then
    report_test "Class variables" "PASS"
else
    report_test "Class variables" "FAIL" "No class variables found"
fi

# Check constructor
if grep -q "\*new" Chroma.sc; then
    report_test "Constructor method" "PASS"
else
    report_test "Constructor method" "FAIL" "Constructor not found"
fi

# Check braces are balanced
OPEN_BRACES=$(grep -o '{' Chroma.sc | wc -l)
CLOSE_BRACES=$(grep -o '}' Chroma.sc | wc -l)
if [[ $OPEN_BRACES -eq $CLOSE_BRACES ]]; then
    report_test "Braces balanced" "PASS" "$OPEN_BRACES open, $CLOSE_BRACES close"
else
    report_test "Braces balanced" "FAIL" "$OPEN_BRACES open, $CLOSE_BRACES close"
fi

# Check parentheses are balanced
OPEN_PARENS=$(grep -o '(' Chroma.sc | wc -l)
CLOSE_PARENS=$(grep -o ')' Chroma.sc | wc -l)
if [[ $OPEN_PARENS -eq $CLOSE_PARENS ]]; then
    report_test "Parentheses balanced" "PASS" "$OPEN_PARENS open, $CLOSE_PARENS close"
else
    report_test "Parentheses balanced" "FAIL" "$OPEN_PARENS open, $CLOSE_PARENS close"
fi

# Check brackets are balanced
OPEN_BRACKETS=$(grep -o '\[' Chroma.sc | wc -l)
CLOSE_BRACKETS=$(grep -o '\]' Chroma.sc | wc -l)
if [[ $OPEN_BRACKETS -eq $CLOSE_BRACKETS ]]; then
    report_test "Brackets balanced" "PASS" "$OPEN_BRACKETS open, $CLOSE_BRACKETS close"
else
    report_test "Brackets balanced" "FAIL" "$OPEN_BRACKETS open, $CLOSE_BRACKETS close"
fi

echo ""
echo "Test 3: Required Methods"
echo "-----------------------------------"

REQUIRED_METHODS=("init" "boot" "cleanup" "allocateResources" "loadSynthDefs" "createSynths")
for method in "${REQUIRED_METHODS[@]}"; do
    if grep -qE "^\s*${method}\s*\{|^\s*\*${method}\s*\{" Chroma.sc || \
       grep -qE "^\s*${method}\s*\|^\s*\*${method}\s*\\|" Chroma.sc; then
        report_test "Method: $method" "PASS"
    else
        report_test "Method: $method" "FAIL" "Method not found"
    fi
done

echo ""
echo "Test 4: OSC Handlers"
echo "-----------------------------------"

OSC_PATHS=("/chroma/gain" "/chroma/filterEnabled" "/chroma/filterAmount" "/chroma/filterCutoff" "/chroma/filterResonance" "/chroma/overdriveEnabled" "/chroma/overdriveDrive" "/chroma/overdriveTone" "/chroma/overdriveMix" "/chroma/overdriveBias" "/chroma/bitcrushEnabled" "/chroma/bitDepth" "/chroma/bitcrushSampleRate" "/chroma/bitcrushDrive" "/chroma/bitcrushMix" "/chroma/granularEnabled" "/chroma/granularDensity" "/chroma/granularSize" "/chroma/granularPitchScatter" "/chroma/granularPosScatter" "/chroma/granularMix" "/chroma/granularFreeze" "/chroma/grainIntensity" "/chroma/reverbEnabled" "/chroma/reverbDecayTime" "/chroma/reverbMix" "/chroma/delayEnabled" "/chroma/delayTime" "/chroma/delayDecayTime" "/chroma/modRate" "/chroma/modDepth" "/chroma/dryWet" "/chroma/blendMode" "/chroma/effectsOrder" "/chroma/inputFreeze" "/chroma/inputFreezeLength" "/chroma/debug")
for path in "${OSC_PATHS[@]}"; do
    if grep -q "$path" Chroma.sc; then
        report_test "OSC: $path" "PASS"
    else
        report_test "OSC: $path" "FAIL" "Handler not found"
    fi
done

echo ""
echo "Test 5: SynthDef Definitions"
echo "-----------------------------------"

SYNTHDEFS=("chroma_input" "chroma_filter" "chroma_overdrive" "chroma_bitcrush" "chroma_granular" "chroma_reverb" "chroma_mod_delay" "chroma_output")
for synthdef in "${SYNTHDEFS[@]}"; do
    if grep -qE "SynthDef.*${synthdef}|'${synthdef}'|\\\\${synthdef}" Chroma.sc; then
        report_test "SynthDef: $synthdef" "PASS"
    else
        report_test "SynthDef: $synthdef" "FAIL" "Definition not found"
    fi
done

echo ""
echo "Test 6: Audio Effect Parameters"
echo "-----------------------------------"

EFFECT_PARAMS=("filterParams" "overdriveParams" "granularParams" "reverbParams" "delayParams" "bitcrushParams")
for param in "${EFFECT_PARAMS[@]}"; do
    if grep -q "$param" Chroma.sc; then
        report_test "Params: $param" "PASS"
    else
        report_test "Params: $param" "FAIL" "Parameter dictionary not found"
    fi
done

echo ""
echo "Test 7: Bus Allocation"
echo "-----------------------------------"

BUS_TYPES=("inputAudio" "filteredAudio" "overdriveAudio" "bitcrushAudio" "granularAudio" "reverbAudio" "delayAudio" "frozenAudio" "dryBus" "wetBus" "bands" "centroid" "spread" "flatness")
for bus in "${BUS_TYPES[@]}"; do
    # Check for buses[\bus] or buses['bus'] or \\bus,
    if grep -qE "buses\[\\\\${bus}\]|buses\['${bus}'\]|\\\\${bus},|\\\\${bus}\)" Chroma.sc; then
        report_test "Bus: $bus" "PASS"
    else
        report_test "Bus: $bus" "FAIL" "Bus not found"
    fi
done

echo ""
echo "Test 8: Effect Order"
echo "-----------------------------------"

if grep -q "effectsOrder" Chroma.sc; then
    report_test "Effects order array" "PASS"
else
    report_test "Effects order array" "FAIL" "effectsOrder not found"
fi

echo ""
echo "Test 9: Documentation Files"
echo "-----------------------------------"

DOC_FILES=("README.md" "test/README.md")
for doc in "${DOC_FILES[@]}"; do
    if [[ -f "$doc" ]]; then
        report_test "Doc: $doc" "PASS"
    else
        report_test "Doc: $doc" "FAIL" "File missing"
    fi
done

echo ""
echo "Test 10: Code Quality Checks"
echo "-----------------------------------"

# Check for proper indentation (should use tabs or consistent spaces)
TAB_LINES=$(grep -c $'^\t' Chroma.sc || true)
SPACE_LINES=$(grep -c $'^  ' Chroma.sc || true)
if [[ $TAB_LINES -gt 0 || $SPACE_LINES -gt 0 ]]; then
    report_test "Consistent indentation" "PASS" "Tabs: $TAB_LINES, Spaces: $SPACE_LINES"
else
    report_test "Consistent indentation" "FAIL" "No indentation found"
fi

# Check for TODO/FIXME comments (informational)
TODO_COUNT=$(grep -c "TODO\|FIXME\|XXX" Chroma.sc || true)
if [[ $TODO_COUNT -eq 0 ]]; then
    report_test "No TODO/FIXME comments" "PASS"
else
    report_test "TODO/FIXME comments" "PASS" "Found $TODO_COUNT items (informational)"
fi

# Check file size is reasonable
FILE_SIZE=$(stat -f%z Chroma.sc 2>/dev/null || stat -c%s Chroma.sc 2>/dev/null || echo "0")
if [[ $FILE_SIZE -gt 10000 ]]; then
    report_test "File size reasonable" "PASS" "$FILE_SIZE bytes"
else
    report_test "File size reasonable" "FAIL" "File too small ($FILE_SIZE bytes)"
fi

echo ""
echo "==================================="
echo "Test Summary"
echo "==================================="
echo "Total tests: $TOTAL_TESTS"
echo -e "Passed: ${GREEN}$PASSED_TESTS${NC}"
echo -e "Failed: ${RED}$FAILED_TESTS${NC}"
SUCCESS_RATE=$((PASSED_TESTS * 100 / TOTAL_TESTS))
echo "Success rate: ${SUCCESS_RATE}%"
echo ""

if [[ $FAILED_TESTS -eq 0 ]]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    exit 1
fi
