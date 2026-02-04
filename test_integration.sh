#!/bin/bash
# Integration test script for Chroma
# Runs headless synth tests

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

# Ensure Chroma.sc is in Extensions
ensure_installed() {
    local ext_dir="$HOME/.local/share/SuperCollider/Extensions"
    mkdir -p "$ext_dir"
    if [ "$SCRIPT_DIR/Chroma.sc" -nt "$ext_dir/Chroma.sc" ]; then
        info "Updating Chroma.sc in Extensions folder"
        cp "$SCRIPT_DIR/Chroma.sc" "$ext_dir/"
    fi
}

# Clean up any existing SC processes
cleanup() {
    pkill -f "sclang" 2>/dev/null || true
    pkill -f "scsynth" 2>/dev/null || true
    sleep 1
}

# Test: Headless synth test
test_headless() {
    info "Running headless synth test..."

    output=$(timeout 30 sclang test_synths.scd 2>&1) || true

    if echo "$output" | grep -q "ALL EFFECT TESTS PASSED"; then
        pass "Headless synth test"
        return 0
    else
        echo "$output"
        fail "Headless synth test failed"
    fi
}

# Main
main() {
    echo "========================================"
    echo "  Chroma Integration Tests"
    echo "========================================"
    echo ""

    cleanup
    ensure_installed

    test_headless

    echo ""
    echo -e "${GREEN}All tests passed!${NC}"
}

main "$@"
