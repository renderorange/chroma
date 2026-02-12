# Chroma Audio Effects System - Troubleshooting Guide

## Table of Contents

1. [Quick Start](#quick-start)
2. [Common Audio Problems](#common-audio-problems)
3. [Debugging Tools](#debugging-tools)
4. [Testing Procedures](#testing-procedures)
5. [Error Messages Reference](#error-messages-reference)
6. [Performance Issues](#performance-issues)
7. [OSC Communication Problems](#osc-communication-problems)

---

## Quick Start

### When Audio Doesn't Work

1. **Run Health Check**:
   ```supercollider
   load("test/manual_test_procedures.sc");
   ~quickHealthCheck.value;
   ```

2. **Check Basic Audio Path**:
   ```supercollider
   // Test basic audio I/O
   { SinOsc.ar(440) * 0.1 }.play; // Should hear 440Hz tone
   SoundIn.ar(0).scope; // Should show input waveform
   ```

3. **Run Automated Tests**:
   ```supercollider
   load("test/test_audio_chain.sc");
   ~runAutomatedTests.value;
   ```

---

## Common Audio Problems

### No Audio Input

**Symptoms**: 
- No input level display
- Effects don't respond to audio input
- Input freeze shows no signal

**Causes & Solutions**:

1. **Wrong Input Channel**
   ```supercollider
   // Check current input channel
   ~chroma.config[\inputChannel].postln;
   
   // Try different channel (0, 1, 2, etc.)
   ~chroma.config[\inputChannel] = 1;
   ```

2. **Audio Interface Not Connected**
   ```bash
   # Check JACK status
   ps aux | grep jackd
   
   # List audio devices
   aplay -l
   ```

3. **Input Channel Validation Failure**
   - Error: "ERROR: Input channel X not available!"
   - Solution: Restart Chroma with correct channel number

**Debug Commands**:
```supercollider
load("test/audio_diagnostics.sc");
~checkSignalLevels.value;
~monitorBus.value('inputAmp', 5.0);
```

### No Audio Output

**Symptoms**:
- No sound from speakers/headphones
- VU meters show activity but no audio
- Effects processing but silent output

**Causes & Solutions**:

1. **Output Bus Not Available**
   ```supercollider
   // Check available output channels
   Server.default.options.numOutputChannels.postln;
   
   // Validate output bus
   ~chroma.validateOutputBus(0);
   ```

2. **Dry/Wet Mix at 0%**
   ```supercollider
   // Check current mix
   ~chroma.dryWet.postln;
   
   // Set to 50% to hear effects
   ~chroma.setDryWet(0.5);
   ```

3. **All Effects Disabled**
   ```supercollider
   // Enable at least one effect
   ~chroma.setFilterEnabled(true);
   ~chroma.setDryWet(1.0); // Full wet
   ```

**Debug Commands**:
```supercollider
~monitorBus.value('delayAudio', 3.0);
// Monitor final output bus for signal
```

### Effects Not Working

**Symptoms**:
- Audio passes through unchanged
- Individual effects have no impact
- Dry/wet mix doesn't affect sound

**Causes & Solutions**:

1. **Effects Chain Broken**
   ```supercollider
   // Check effects order
   ~chroma.getEffectsOrder().postln;
   
   // Reconnect effects
   ~chroma.reconnectEffects();
   ```

2. **Effect Self-Referential Buses (Critical Bug - FIXED)**
   - Previously: Effects reading/writing to same bus
   - Now: Proper intermediate bus routing implemented

3. **Parameters at Minimum**
   ```supercollider
   // Check effect parameters
   ~chroma.filterParams.postln;
   ~chroma.overdriveParams.postln;
   
   // Set reasonable values
   ~chroma.setFilterAmount(0.5);
   ~chroma.setOverdriveDrive(0.3);
   ```

**Debug Commands**:
```supercollider
load("test/audio_diagnostics.sc");
~validateBusRouting.value;
~reportBusStatus.value;
```

---

## Debugging Tools

### Debug Levels

Chroma supports three debug levels (using descriptive names):

- **Level 0 (off)**: No debug output
- **Level 1 (basic)**: Basic operation logging
- **Level 2 (verbose)**: Detailed signal tracing and bus monitoring

```supercollider
~chroma.setDebugLevel(2); // Verbose debugging
```

### Signal Tracing

Enable detailed signal flow monitoring:

```supercollider
// Monitor specific signal stages
~chroma.traceSignalStage(\input, signal);
~chroma.traceBusActivity(\frozenAudio);

// Check all signal levels
~chroma.traceSignalStage(\effects_chain, finalSignal);
```

### Bus Monitoring

Monitor audio buses in real-time:

```supercollider
load("test/audio_diagnostics.sc");

// Monitor specific bus
~monitorBus.value('granularAudio', 5.0);

// Validate all bus routing
~validateBusRouting.value;

// Comprehensive bus status
~reportBusStatus.value;
```

### Scope and FFT Analysis

```supercollider
// Visual monitoring
s.scope;           // Waveform scope
s.freqscope;       // Frequency spectrum
s.makeWindow;      // Server control window

// Signal analysis
{ SoundIn.ar(0) }.scope;  // Input signal
~chroma.synths[\output].scope; // Output signal
```

---

## Testing Procedures

### Automated Testing

Run comprehensive automated tests:

```supercollider
load("test/test_audio_chain.sc");

// Run complete test suite
~runAutomatedTests.value;

// Individual component tests
~testAudioInput.value;
~testAudioOutput.value;
~testEachEffect.value;
~testSignalChain.value;
~testOSCCommunication.value;
```

**Test Results**:
- PASS: Component working correctly
- FAIL: Component has issues (check error message)
- Success rate percentage shown

### Manual Testing

Step-by-step guided testing:

```supercollider
load("test/manual_test_procedures.sc");

// Full guided testing
~stepByStepTesting.value;

// Quick health check
~quickHealthCheck.value;

// Specific troubleshooting
~noAudioInputHelp.value;
~noAudioOutputHelp.value;
~effectsNotWorkingHelp.value;
~oscCommunicationHelp.value;
~performanceHelp.value;
```

### Test Signal Generation

Generate test signals for troubleshooting:

```supercollider
// Standard test signals
~generateTestSignal.value(440, 2.0, 0.1);  // 440Hz sine, 2 seconds, low volume
~generateTestSignal.value(1000, 1.0, 0.2); // 1kHz sine, 1 second, medium volume

// Custom test signal
{ SinOsc.ar(880) * 0.15 }.play; // Direct test tone
```

---

## Error Messages Reference

### Critical System Errors

| Error Message | Cause | Solution |
|--------------|-------|----------|
| "ERROR: Input channel X not available!" | Invalid input channel | Use channel 0-% with `~chroma.config[\inputChannel] = X` |
| "ERROR: Output bus X not available!" | Invalid output bus | Check available channels with `Server.default.options.numOutputChannels` |
| "Chroma stopping due to input configuration error." | Input validation failure | Restart with correct channel number |
| "Chroma stopping due to output configuration error." | Output validation failure | Check audio device connection |

### Bus Routing Errors

| Error Message | Cause | Solution |
|--------------|-------|----------|
| "Bus BUSNAME: NOT ALLOCATED" | Missing bus allocation | Check bus creation in `allocateResources()` |
| "✗ Effect bus BUSNAME: NOT CONNECTED" | Failed bus connection | Run `~validateBusRouting.value()` |
| "✗ Effect EFFECT: synth NOT CREATED" | Synth creation failed | Check SynthDef loading and server boot |

### OSC Communication Errors

| Error Message | Cause | Solution |
|--------------|-------|----------|
| "Invalid overdriveBias message: %" | Invalid OSC message format | Check message type and parameter range |
| "OSC communication: FAILED" | Network/port issues | Check port 57120 availability and network config |

---

## Performance Issues

### High CPU Usage

**Symptoms**:
- Audio dropouts or glitches
- CPU usage > 80%
- Server warnings in post window

**Solutions**:

1. **Reduce Granular Density**
   ```supercollider
   ~chroma.setGrainDensity(10); // Reduce from default 20
   ```

2. **Disable Unused Effects**
   ```supercollider
   ~chroma.setGranularEnabled(false);
   ~chroma.setReverbEnabled(false);
   ```

3. **Adjust Server Settings**
   ```supercollider
   // Restart server with larger block size
   s.options.blockSize = 128;
   s.reboot;
   ```

**Performance Monitoring**:
```supercollider
// Monitor CPU usage
{ s.avgCPU.poll(1, "cpu") }.play;

// Check server load
s.plotTree; // Show all nodes and UGens
```

### Audio Dropouts

**Symptoms**:
- Clicks, pops, or silence
- Intermittent audio loss
- Glitches during parameter changes

**Solutions**:

1. **Increase Server Memory**
   ```supercollider
   s.options.memSize = 16384 * 4; // Double memory
   s.reboot;
   ```

2. **Reduce Effect Parameters**
   ```supercollider
   ~chroma.setFilterResonance(0.2); // Lower Q to reduce CPU
   ~chroma.setGrainSize(0.2); // Larger grains = less processing
   ```

3. **Optimize Buffer Sizes**
   ```supercollider
   // Check current buffer usage
   ~chroma.fftBuffer.postln;
   ~chroma.grainBuffer.postln;
   ```

---

## OSC Communication Problems

### OSC Messages Not Received

**Symptoms**:
- Parameter changes have no effect
- External controller not working
- No response to OSC commands

**Solutions**:

1. **Check OSC Responders**
   ```supercollider
   OSCFunc.trace(true); // Show all OSC messages
   
   // Check specific responders
   OSCdef(\chromaGain).postln;
   ```

2. **Verify Port Availability**
   ```bash
   netstat -an | grep 57120
   
   # Test OSC manually
   oscsend 127.0.0.1 57120 "/chroma/gain" f 0.8
   ```

3. **Test OSC Communication**
   ```supercollider
   load("test/test_audio_chain.sc");
   ~testOSCCommunication.value;
   ```

### External Controller Issues

**Symptoms**:
- Controller can't connect to Chroma
- Messages sent but no response
- Connection timeouts

**Solutions**:

1. **Verify Network Configuration**
   ```supercollider
   // Check SuperCollider is listening
   Server.default.addr.postln;
   
   // Test external connection
   n = NetAddr("127.0.0.1", 57120);
   n.sendMsg("/chroma/debug", 2);
   ```

2. **Check External Controller Configuration**
   - Ensure controller sends to localhost:57120
   - Verify message format matches OSCdefs
   - Check for network/firewall restrictions

---

## Quick Reference Commands

### Essential Commands

```supercollider
// Start Chroma with debugging
Chroma.start(debug: 2);

// Health check
load("test/manual_test_procedures.sc");
~quickHealthCheck.value;

// Automated tests
load("test/test_audio_chain.sc");
~runAutomatedTests.value;

// Bus monitoring
load("test/audio_diagnostics.sc");
~monitorBus.value('frozenAudio', 3.0);

// Input/output testing
~noAudioInputHelp.value;
~noAudioOutputHelp.value;

// Performance check
~performanceHelp.value;
{ s.avgCPU.poll(1, "cpu") }.play;
```

### Parameter Reset

```supercollider
// Reset all parameters to defaults
~chroma.setInputGain(1.0);
~chroma.setDryWet(0.5);
~chroma.setFilterAmount(0.5);
~chroma.setOverdriveDrive(0.5);
~chroma.setGrainDensity(20);

// Reset effects order
~chroma.setEffectsOrder([\filter, \overdrive, \bitcrush, \granular, \reverb, \delay]);
```

---

## Getting Help

### Community Support

- Check existing issues and solutions
- Share error messages and debug output
- Include system information (OS, audio interface, SuperCollider version)

### Reporting Issues

When reporting problems, include:

1. **System Information**:
   - Operating system
   - Audio interface model
   - SuperCollider version
   - JACK configuration

2. **Error Messages**:
   - Complete error output
   - Debug level 2 output
   - Test results

3. **Steps to Reproduce**:
   - What you were trying to do
   - Expected vs actual behavior
   - Any relevant configuration

### Performance Profile

For performance issues, provide:

```supercollider
// Performance snapshot
s.avgCPU.postln;
s.peakCPU.postln;
s.numUGens.postln;
s.numSynths.postln;
Server.default.options.postln;
```

---

*This troubleshooting guide covers common audio issues in the Chroma system. For additional help, refer to the diagnostic tools in the test/ directory or run the automated test suite.*