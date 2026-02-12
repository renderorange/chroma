#!/bin/bash
# run_tests.sh - Chroma test runner script

CURRENT_DIR=$(pwd)
DIR_NAME=$(dirname "$0")
TEST_DIR=$(realpath "${DIR_NAME}")
APP_DIR=$(realpath "${TEST_DIR}/../")
SCRIPT_NAME=$(basename $0)

pushd $APP_DIR

echo "=== Chroma Audio System Test Runner ==="
echo ""

# Check if SuperCollider is available
if ! command -v sclang &> /dev/null; then
    echo "Error: SuperCollider (sclang) not found in PATH"
    echo "Please install SuperCollider and ensure sclang is available"
    exit 1
fi

# Function to run SuperCollider test
run_sclang_test() {
    local test_file="$1"
    local description="$2"

    local test_full_path=$(realpath "test/${test_file}")

    echo "Running: $description"
    echo "File: test/$test_file"
    echo "---"
    
    # Create temporary test script
    temp_script=$(mktemp)
    cat << EOF > "$temp_script"
// Auto-generated test script for $test_file
load("$test_full_path");
EOF
    
    # Run test with sclang
    sclang "$temp_script"
    
    # Clean up
    rm -f "$temp_script"
    
    echo ""
    echo "Test completed."
    echo "========================================="
    echo ""
}

# Menu for test selection
show_menu() {
    echo "Available tests:"
    echo "1) Automated Audio Chain Tests"
    echo "2) Manual Testing Procedures" 
    echo "3) Audio Diagnostics Tools"
    echo "4) Bias Parameter Tests"
    echo "5) SynthDef Compilation Tests"
    echo "6) System Integration Tests"
    echo "7) Grain Intensity Tests"
    echo "8) TUI OSC Integration Tests"
    echo "9) Effect Enabled Toggles Tests"
    echo "10) Dynamic Effects Reconnection Tests"
    echo "11) Syntax Validation"
    echo "12) Run All Tests"
    echo "13) Exit"
    echo ""
    echo -n "Select test (1-13): "
}

# Main execution
main() {
    while true; do
        show_menu
        read choice
        
        case $choice in
            1)
                run_sclang_test "test_audio_chain.sc" "Automated Audio Chain Testing"
                ;;
            2)
                run_sclang_test "manual_test_procedures.sc" "Manual Testing Procedures"
                ;;
            3)
                run_sclang_test "audio_diagnostics.sc" "Audio Diagnostics Tools"
                ;;
            4)
                run_sclang_test "bias_parameter_test.sc" "Comprehensive Bias Parameter Tests"
                ;;
            5)
                run_sclang_test "synthdef_compilation_test.sc" "SynthDef Compilation and Validation Tests"
                ;;
            6)
                run_sclang_test "system_integration_test.sc" "System Integration and Headless Tests"
                ;;
            7)
                run_sclang_test "grain_intensity_test.sc" "Grain Intensity Functionality Tests"
                ;;
            8)
                run_sclang_test "osc_integration_test.sc" "OSC Integration Verification Tests"
                ;;
            9)
                run_sclang_test "effect_enabled_toggles_test.sc" "Effect Enabled Toggles Comprehensive Tests"
                ;;
            10)
                run_sclang_test "dynamic_effects_reconnection_test.sc" "Dynamic Effects Reconnection Validation Tests"
                ;;
            11)
                ./test/syntax_validation.sh
                ;;
            12)
                echo "Running all tests sequentially..."
                echo ""
                run_sclang_test "test_bias.sc" "Basic Bias Parameter Test"
                run_sclang_test "audio_diagnostics.sc" "Audio Diagnostics Tools"
                run_sclang_test "bias_parameter_test.sc" "Comprehensive Bias Parameter Tests"
                run_sclang_test "synthdef_compilation_test.sc" "SynthDef Compilation Tests"
                run_sclang_test "system_integration_test.sc" "System Integration Tests"
                run_sclang_test "test_audio_chain.sc" "Automated Audio Chain Testing"
                run_sclang_test "manual_test_procedures.sc" "Manual Testing Procedures"
                run_sclang_test "grain_intensity_test.sc" "Grain Intensity Functionality Tests"
                run_sclang_test "osc_integration_test.sc" "OSC Integration Verification Tests"
                run_sclang_test "effect_enabled_toggles_test.sc" "Effect Enabled Toggles Comprehensive Tests"
                run_sclang_test "dynamic_effects_reconnection_test.sc" "Dynamic Effects Reconnection Validation Tests"
                ./test/syntax_validation.sh "Enhanced Syntax Validation"
                echo ""
                echo "All tests completed!"
                ;;
            13)
                echo "Exiting test runner."
                exit 0
                ;;
            *)
                echo "Invalid selection. Please choose 1-13."
                ;;
        esac
    done
}

# Check if test directory exists
if [ ! -d "test" ]; then
    echo "Error: test/ directory not found!"
    echo "Please run this script from the Chroma project root directory."
    exit 1
fi

# Check if Chroma.sc exists
if [ ! -f "Chroma.sc" ]; then
    echo "Error: Chroma.sc not found!"
    echo "Please run this script from the Chroma project root directory."
    exit 1
fi

echo "SuperCollider found: $(which sclang)"
echo "Test directory found: $(pwd)/test/"
echo "Chroma.sc found: $(pwd)/Chroma.sc"
echo ""

# Start menu
main
