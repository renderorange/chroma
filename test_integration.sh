#!/bin/bash
# Integration test script for Chroma
# Runs headless synth tests and optionally GUI tests

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

# Test 1: Headless synth test
test_headless() {
    info "Running headless synth test..."

    output=$(timeout 30 sclang test_synths.scd 2>&1) || true

    if echo "$output" | grep -q "ALL SYNTH TESTS PASSED"; then
        pass "Headless synth test"
        return 0
    else
        echo "$output"
        fail "Headless synth test failed"
    fi
}

# Test 2: GUI startup test
test_gui_startup() {
    info "Testing GUI startup..."

    # Start in background
    sclang run_gui.scd 2>&1 &
    local pid=$!

    # Wait for startup
    sleep 10

    # Check if processes are running
    if pgrep -f "sclang run_gui.scd" > /dev/null && pgrep scsynth > /dev/null; then
        pass "GUI and audio server started"
    else
        fail "GUI startup failed"
    fi

    # Check for "Chroma ready" would require capturing output differently
    # For now, process check is sufficient

    # Check JACK connections
    if command -v jack_lsp &> /dev/null; then
        if jack_lsp -c 2>/dev/null | grep -q "SuperCollider"; then
            pass "JACK audio connections established"
        else
            info "JACK connections not detected (may be using other audio driver)"
        fi
    fi

    # Cleanup
    info "Stopping Chroma..."
    pkill -f "sclang run_gui.scd" 2>/dev/null || true
    sleep 2
    pkill scsynth 2>/dev/null || true
    sleep 1

    if ! pgrep -f "sclang|scsynth" > /dev/null; then
        pass "Clean shutdown"
    else
        fail "Processes still running after shutdown"
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

    case "${1:-all}" in
        headless)
            test_headless
            ;;
        gui)
            test_gui_startup
            ;;
        all)
            test_headless
            echo ""
            test_gui_startup
            ;;
        *)
            echo "Usage: $0 [headless|gui|all]"
            exit 1
            ;;
    esac

    echo ""
    echo -e "${GREEN}All tests passed!${NC}"
}

main "$@"
