#!/bin/bash
# Chroma launcher with audio device selection via JACK

CONFIG_DIR="$HOME/.config/chroma"
CONFIG_FILE="$CONFIG_DIR/config"

show_help() {
    echo "Usage: ./run.sh [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  -d, --device CARD   Use specified ALSA card number, e.g. 1 (saved to config)"
    echo "  -l, --list          List available audio devices"
    echo "  -h, --help          Show this help message"
    echo ""
    echo "Without arguments, uses device from config file or card 0."
    echo ""
    echo "Example: ./run.sh -d 1   # Use card 1 (USB device)"
}

list_devices() {
    echo "Available audio devices:"
    echo ""
    aplay -l 2>/dev/null | grep "^card"
    echo ""
    echo "Use the card number with -d, e.g.: ./run.sh -d 1"
}

load_config() {
    if [[ -f "$CONFIG_FILE" ]]; then
        source "$CONFIG_FILE"
    fi
}

save_config() {
    mkdir -p "$CONFIG_DIR"
    echo "device=$1" > "$CONFIG_FILE"
    echo "Saved device to $CONFIG_FILE"
}

# Parse arguments
DEVICE=""
while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--device)
            DEVICE="$2"
            shift 2
            ;;
        -l|--list)
            list_devices
            exit 0
            ;;
        -h|--help)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            show_help
            exit 1
            ;;
    esac
done

# Load config if no device specified
if [[ -z "$DEVICE" ]]; then
    load_config
    if [[ -n "$device" ]]; then
        DEVICE="$device"
        echo "Using device from config: hw:$DEVICE"
    else
        DEVICE="0"
        echo "Using default device: hw:0"
    fi
else
    save_config "$DEVICE"
fi

# Stop any existing JACK server
killall jackd 2>/dev/null
sleep 0.5

# Start JACK with the specified device
echo "Starting JACK with hw:$DEVICE..."
jackd -d alsa -d "hw:$DEVICE" -r 48000 -p 1024 &
JACK_PID=$!
sleep 1

# Check if JACK started successfully
if ! kill -0 $JACK_PID 2>/dev/null; then
    echo "Error: Failed to start JACK with hw:$DEVICE"
    exit 1
fi

# Run Chroma
sclang "$(dirname "$0")/startup.scd"

# Cleanup JACK when done
kill $JACK_PID 2>/dev/null
