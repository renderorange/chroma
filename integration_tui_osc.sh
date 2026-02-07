#!/bin/bash
# Real integration tests that verify actual TUI↔SuperCollider communication

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

pass() { echo -e "${GREEN}✓ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }
info() { echo -e "${YELLOW}→ $1${NC}"; }

# Test: Comprehensive TUI↔SC integration via Go tests
test_tui_sc_integration() {
    info "Testing comprehensive TUI↔SC integration..."
    
    # Ensure Chroma.sc is installed
    local ext_dir="$HOME/.local/share/SuperCollider/Extensions"
    mkdir -p "$ext_dir"
    if [ "$SCRIPT_DIR/Chroma.sc" -nt "$ext_dir/Chroma.sc" ]; then
        info "Updating Chroma.sc in Extensions folder"
        cp "$SCRIPT_DIR/Chroma.sc" "$ext_dir/"
    fi
    
    # Run the comprehensive Go integration tests that verify:
    # 1. OSC message sending and receiving
    # 2. Parameter change round-trips
    # 3. Pending changes prevent overwrites (the bug we fixed)
    # 4. Stale pending changes are cleared
    # 5. All parameter types work correctly
    
    cd chroma-tui
    
    info "Running TUI OSC communication tests..."
    go test -v -run TestOSCCommunication ./integration/ || {
        fail "TUI OSC communication tests failed"
    }
    
    info "Running parameter sync tests..."
    go test -v -run TestParameterSync ./integration/ || {
        fail "Parameter sync tests failed"
    }
    
    cd ..
    
    pass "Comprehensive TUI↔SC integration tests passed"
}

# Test: Unit tests for individual components
test_unit_tests() {
    info "Running unit tests for TUI components..."
    
    cd chroma-tui
    
    info "Running TUI model unit tests..."
    go test -v ./tui/ || {
        fail "TUI model unit tests failed"
    }
    
    info "Running OSC client/server unit tests..."
    go test -v ./osc/ || {
        fail "OSC client/server unit tests failed"
    }
    
    info "Running functional TUI-SuperCollider workflow tests..."
    go test -v ./functional/ || {
        fail "Functional workflow tests failed"
    }
    
    cd ..
    
    pass "Unit tests passed"
}

# Test: Original SuperCollider integration (lighter version)
test_sc_integration() {
    info "Testing SuperCollider integration (basic functionality)..."
    
    export QT_QPA_PLATFORM=offscreen
    
    # Quick test to verify SuperCollider can load Chroma class
    echo 'Chroma.start; Chroma.stop; "SC_INTEGRATION_OK".postln;' | timeout 10 sclang 2>&1 | tee /tmp/sc_test.log
    
    if grep -q "SC_INTEGRATION_OK" /tmp/sc_test.log; then
        pass "SuperCollider integration test passed"
    else
        fail "SuperCollider integration test failed"
    fi
    
    rm -f /tmp/sc_test.log
}

# Main
main() {
    echo "========================================"
    echo "  Real TUI↔SuperCollider Integration Tests"
    echo "========================================"
    echo ""
    
    test_tui_sc_integration
    test_unit_tests
    test_sc_integration
    
    echo ""
    echo -e "${GREEN}All real integration tests passed!${NC}"
    echo ""
    echo -e "${YELLOW}Test Coverage Summary:${NC}"
    echo "  • OSC message sending/receiving"
    echo "  • Parameter adjustment and state synchronization"
    echo "  • Pending changes system (settings bug fix verification)"
    echo "  • Concurrent update handling"
    echo "  • State cleanup and timeout management"
    echo "  • All parameter types (float, int, bool, string)"
    echo "  • Toggle controls and blend modes"
    echo "  • Unit test coverage for TUI and OSC components"
    echo "  • SuperCollider integration verification"
}

main "$@"