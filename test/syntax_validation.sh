#!/bin/bash
# syntax_validation.sh - Enhanced syntax and compilation validation
# Migrated and enhanced from test_compilation.sh and test_syntax.sh

echo "=== Enhanced Syntax Validation ==="
echo "Migrated from test_compilation.sh and test_syntax.sh"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; }
info() { echo -e "${YELLOW}→ $1${NC}"; }

# Enhanced Test 1: Basic class structure
test_basic_structure() {
    echo "=== Testing Basic Class Structure ==="
    
    # Check if Chroma.sc exists
    if [ ! -f "Chroma.sc" ]; then
        fail "Chroma.sc not found"
        return 1
    fi
    pass "Chroma.sc found"
    
    # Enhanced syntax checks with grep
    echo "Checking enhanced syntax structure..."
    
    # Check class declaration
    if grep -q "^Chroma {" Chroma.sc; then
        pass "Class declaration found"
    else
        fail "Class declaration missing"
        return 1
    fi
    
    # Check for class variable
    if grep -q "classvar.*instance" Chroma.sc; then
        pass "Class variable declaration found"
    else
        fail "Class variable declaration missing"
        return 1
    fi
    
    # Check for constructor
    if grep -q "\*new {" Chroma.sc; then
        pass "Constructor method found"
    else
        fail "Constructor method missing"
        return 1
    fi
    
    # Check for start method
    if grep -q "\*start {" Chroma.sc; then
        pass "Start method found"
    else
        fail "Start method missing"
        return 1
    fi
    
    pass "Basic structure validation complete"
}

# Enhanced Test 2: Advanced class structure
test_advanced_structure() {
    echo "=== Testing Advanced Class Structure ==="
    
    # Enhanced method validation
    local required_methods=(
        "boot"
        "cleanup" 
        "allocateResources"
        "loadSynthDefs"
        "createSynths"
        "reconnectEffects"
        "setupOSC"
        "cleanupOSC"
    )
    
    echo "Checking for required methods..."
    for method in "${required_methods[@]}"; do
        if grep -q "$method {" Chroma.sc; then
            pass "Method $method found"
        else
            fail "Method $method not found"
            return 1
        fi
    done
    
    # Enhanced OSC handler validation
    echo "Checking for OSC handlers..."
    local osc_handlers=(
        "setupOSC"
        "cleanupOSC"
        "gain"
        "filterAmount"
        "overdriveBias"
    )
    
    for handler in "${osc_handlers[@]}"; do
        if grep -q "$handler" Chroma.sc; then
            pass "OSC handler $handler found"
        else
            fail "OSC handler $handler not found"
            return 1
        fi
    done
    
    pass "Advanced structure validation complete"
}

# Enhanced Test 3: Balance validation
test_balance_validation() {
    echo "=== Testing Code Balance ==="
    
    # Enhanced brace checking
    local open_braces=$(grep -o "{" Chroma.sc | wc -l)
    local close_braces=$(grep -o "}" Chroma.sc | wc -l)
    
    if [ "$open_braces" -eq "$close_braces" ]; then
        pass "Braces are balanced ($open_braces open, $close_braces close)"
    else
        fail "Braces are not balanced ($open_braces open, $close_braces close)"
        return 1
    fi
    
    # Enhanced parenthese checking
    local open_parens=$(grep -o "(" Chroma.sc | wc -l)
    local close_parens=$(grep -o ")" Chroma.sc | wc -l)
    
    if [ "$open_parens" -eq "$close_parens" ]; then
        pass "Parentheses are balanced ($open_parens open, $close_parens close)"
    else
        fail "Parentheses are not balanced ($open_parens open, $close_parens close)"
        return 1
    fi
    
    pass "Code balance validation complete"
}

# Enhanced Test 4: File system validation
test_file_system() {
    echo "=== Testing File System ==="
    
    # Check Chroma.sc file permissions
    if [ -r "Chroma.sc" ] && [ -w "Chroma.sc" ]; then
        pass "Chroma.sc file permissions OK"
    else
        fail "Chroma.sc file permissions issue"
        return 1
    fi
    
    # Check for backup files
    if [ -f "Chroma.sc.bak" ] || [ -f "Chroma.sc~" ]; then
        info "Backup files found (normal for development)"
    fi
    
    # Check file size (should be reasonable)
    local file_size=$(stat --printf="%s" Chroma.sc | cut -d' ' -f 1)
    if [ "$file_size" -gt "100000" ]; then  # > 100KB
        info "Large file size: ${file_size} bytes"
    fi
    
    pass "File system validation complete"
}

# Main function
main() {
    echo "Running enhanced syntax validation suite..."
    echo ""
    
    local tests_failed=0
    
    # Run all enhanced tests
    if ! test_basic_structure; then
        tests_failed=1
    fi
    
    if ! test_advanced_structure; then
        tests_failed=1
    fi
    
    if ! test_balance_validation; then
        tests_failed=1
    fi
    
    if ! test_file_system; then
        tests_failed=1
    fi
    
    echo ""
    echo "=== SYNTAX VALIDATION SUMMARY ==="
    if [ $tests_failed -eq 0 ]; then
        echo -e "${GREEN}All syntax validation tests passed!${NC}"
    else
        echo -e "${RED}Some syntax validation tests failed${NC}"
        return 1
    fi
}

# Run main function with all arguments
main "$@"
