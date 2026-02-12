# Chroma Test Directory

This directory contains testing and diagnostic tools for the Chroma audio effects system.

## Files

### Core Testing Files

- **`test_audio_chain.sc`** - Automated test suite with pass/fail reporting
- **`manual_test_procedures.sc`** - Step-by-step interactive testing guides
- **`audio_diagnostics.sc`** - Real-time bus monitoring and diagnostics
- **`test_bias.sc`** - Basic overdrive bias parameter test
- **`bias_parameter_test.sc`** - Bias parameter integration testing
- **`synthdef_compilation_test.sc`** - SynthDef compilation and validation testing
- **`system_integration_test.sc`** - System integration and headless testing
- **`syntax_validation.sc`** - Syntax and structure validation

### Advanced Integration Testing Files

- **`grain_intensity_test.sc`** - Grain intensity functionality testing (subtle/pronounced/extreme modes)
- **`osc_integration_test.sc`** - OSC interface verification and endpoint validation
- **`effect_enabled_toggles_test.sc`** - Effect enabled toggle testing (OSC and methods)
- **`dynamic_effects_reconnection_test.sc`** - Effects reconnection validation and audio continuity

### Documentation

- **`troubleshooting_guide.md`** - Troubleshooting documentation

### Scripts

- **`run_tests.sh`** - Interactive test runner with menu interface
- **`syntax_validation.sh`** - Syntax and compilation validation (shell script)

## Quick Start

### 1. Interactive Test Runner

```bash
cd /path/to/chroma
./test/run_tests.sh
```

This provides a menu to run different test suites interactively, including:
- Core audio chain tests (1-6)
- Advanced integration tests (7-10)
- Syntax validation (11)
- Run all tests (12)

### 2. Direct SuperCollider Testing

```supercollider
// Load and run automated tests
load("test/test_audio_chain.sc");
~runAutomatedTests.value;

// Load manual testing procedures
load("test/manual_test_procedures.sc");
~stepByStepTesting.value;

// Load diagnostics tools
load("test/audio_diagnostics.sc");
~runDiagnostics.value;
```

### 3. Command Line Testing

```bash
# Run specific test file
sclang test/test_audio_chain.sc
sclang test/manual_test_procedures.sc
sclang test/audio_diagnostics.sc
```

## Test Types

### Automated Tests (`test_audio_chain.sc`)

- **Audio Input Testing** - Validates input channel and synth creation
- **Audio Output Testing** - Checks output bus and synth connectivity  
- **Spectral Analysis Testing** - Verifies FFT and band analysis
- **Individual Effects Testing** - Tests each effect synth
- **Signal Chain Testing** - Verifies complete audio path
- **OSC Communication Testing** - Tests external control interface

### Advanced Integration Tests

**Grain Intensity Testing (`grain_intensity_test.sc`)**
- **Intensity Modes** - Tests subtle/pronounced/extreme grain intensity modes
- **OSC Control** - Validates /chroma/grainIntensity endpoint functionality
- **Audio Effect** - Verifies grain intensity affects granular parameters correctly
- **Error Handling** - Tests invalid mode fallback and edge cases

**OSC Integration Testing (`osc_integration_test.sc`)**
- **OSC Endpoint Verification** - Validates all required OSC endpoints exist in code
- **Setter Method Verification** - Confirms all required setter methods are implemented
- **OSC Functionality** - Tests OSC endpoint responsiveness and parameter updates
- **OSC Client Parameters** - Verifies OSC-controllable parameters are available
- **Message Handling** - Tests various OSC message formats and error cases

**Effect Enabled Toggles Testing (`effect_enabled_toggles_test.sc`)**
- **OSC Handler Verification** - Validates effect enabled OSC handlers exist
- **Setter Method Verification** - Confirms all effect setter methods are implemented
- **Toggle Functionality** - Tests enable/disable via methods and OSC messages
- **Audio Impact** - Verifies toggles affect audio processing correctly
- **Parameter Persistence** - Tests toggle state persistence and rapid toggling

**Dynamic Effects Reconnection Testing (`dynamic_effects_reconnection_test.sc`)**
- **Reconnection Methods** - Validates reconnectEffects and getEffectOutputBus methods
- **Integration Points** - Verifies reconnection is called from setEffectsOrder and createSynths
- **Reconnection Functionality** - Tests method functionality and error handling
- **Effects Order Reconnection** - Tests reconnection during effects order changes
- **Audio Continuity** - Verifies audio flow is maintained during reconnections

### Manual Tests (`manual_test_procedures.sc`)

- **Step-by-Step Testing** - Guided interactive testing
- **Health Check** - Quick system status overview
- **Troubleshooting Help** - Specific problem guidance
- **Performance Monitoring** - CPU and system load analysis

### Diagnostics (`audio_diagnostics.sc`)

- **Bus Monitoring** - Real-time signal level monitoring
- **Bus Routing Validation** - Check connections and allocations
- **Signal Flow Testing** - Inject test signals into audio chain
- **Comprehensive Status** - Complete system overview

## Usage Examples

### Debugging No Audio Input

```supercollider
load("test/manual_test_procedures.sc");
~noAudioInputHelp.value;    // Get specific guidance
~quickHealthCheck.value;     // Check system status

load("test/audio_diagnostics.sc");
~checkSignalLevels.value;     // Monitor input levels
~monitorBus.value('inputAmp', 5.0); // Watch input bus
```

### Debugging Effects Not Working

```supercollider
load("test/test_audio_chain.sc");
~runAutomatedTests.value;    // Run full test suite
~testEachEffect.value;        // Test individual effects

load("test/audio_diagnostics.sc");
~validateBusRouting.value;    // Check signal connections
~reportBusStatus.value;      // Comprehensive bus overview
```

### Performance Troubleshooting

```supercollider
load("test/manual_test_procedures.sc");
~performanceHelp.value;       // Performance guidance
~quickHealthCheck.value;      // Check CPU usage

// Real-time performance monitoring
{ s.avgCPU.poll(1, "cpu") }.play;
s.plotTree;                   // Show server node tree
```

## Test Results Interpretation

### Automated Test Results

- **PASS** - Component working correctly
- **FAIL** - Issue detected (check error message)
- **Success Rate** - Percentage of tests that passed

### Manual Test Results

- **Input Level** - Should show values > 0.001 when making noise
- **Output Audio** - Should hear processed audio at 50% dry/wet mix
- **Effect Response** - Parameters should change sound character

### Diagnostic Output

- **✓** - Component found and working
- **✗** - Component missing or failed
- **[BUS]** - Bus allocation and routing status
- **[SIGNAL]** - Audio signal level and activity

## Common Workflows

### New Setup Validation

```bash
./test/run_tests.sh
# Select option 5 (Run All Tests)
```

### Troubleshooting Specific Issues

```supercollider
load("test/manual_test_procedures.sc");
// Choose appropriate help function based on symptom
~noAudioInputHelp.value;     // No input signal
~noAudioOutputHelp.value;    // No output audio
~effectsNotWorkingHelp.value; // Effects not processing
~oscCommunicationHelp.value; // External controller issues
```

### Before Performance Critical Use

```supercollider
load("test/test_audio_chain.sc");
~runAutomatedTests.value;    // Verify all systems
~performanceHelp.value;       // Check optimization settings

load("test/audio_diagnostics.sc");
~runDiagnostics.value;        // Comprehensive health check
```

## Integration with Main System

These tests are designed to work with the main Chroma system:

1. **Load Chroma First**: Tests automatically start Chroma with debug level 2
2. **Non-Destructive**: Tests use low-volume test signals and cleanup properly
3. **Compatible**: Work with existing configuration and audio setup
4. **Safe**: Include input validation and error recovery

## Error Recovery

If tests fail:

1. **Check SuperCollider Server**: Ensure server is running and responding
2. **Verify Audio Interface**: Check JACK connection and device selection
3. **Review Test Output**: Look for specific error messages and guidance
4. **Consult Documentation**: See `troubleshooting_guide.md` for detailed help

## Development and Extensions

To add new tests:

1. **Add to Appropriate File**: 
   - Automated tests → `test_audio_chain.sc`
   - Manual procedures → `manual_test_procedures.sc`  
   - Diagnostic tools → `audio_diagnostics.sc`

2. **Follow Naming Convention**:
   - Test functions: `~testFunctionName.value`
   - Help functions: `~problemHelp.value`
   - Diagnostic functions: `~diagnosticFunction.value`

3. **Include Documentation**: Add examples and expected results

---

*For troubleshooting, see `troubleshooting_guide.md` in this directory.*
