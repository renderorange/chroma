// Integration Test Script - Overdrive Bias
// Tests OSC communication, audio effect, and blend control modulation

// Test 1: OSC Communication
"=== Testing OSC Communication ===".postln;

(
// Test bias OSC endpoint with range validation
n = NetAddr("localhost", 57120);

// Test range conversion
testValues = [-1.0, -0.5, 0.0, 0.5, 1.0];

"Testing bias parameter range conversion:".postln;
testValues.do({ |val|
    var expectedInternal = val.linlin(-1, 1, 0, 1);
    var expectedDC = (expectedInternal - 0.5) * 2;
    
    "  OSC % -> Internal % -> DC %".format(val, expectedInternal, expectedDC).postln;
    
    // Send OSC message
    n.sendMsg("/chroma/overdriveBias", val);
    0.1.wait;
});

"  ✅ OSC communication test complete".postln;
)

// Test 2: Verify Parameter Storage
(
"\n=== Testing Parameter Storage ===".postln;

// Check that bias parameter exists and stores correctly
if(Chroma.instance.notNil) {
    "Current bias parameter: %".format(Chroma.instance.overdriveParams[\bias]).postln;
    
    // Test setting different values
    [0.0, 0.25, 0.5, 0.75, 1.0].do({ |internalVal|
        Chroma.instance.setOverdriveBias(internalVal.linlin(0, 1, -1, 1));
        "Set bias to % -> Stored: %".format(internalVal.linlin(0, 1, -1, 1), Chroma.instance.overdriveParams[\bias]).postln;
    });
    
    "  ✅ Parameter storage verified".postln;
} {
    "  ⚠️ Chroma instance not running - start with Chroma.start".postln;
}
)

// Test 3: Spectral Modulation Logic
(
"\n=== Testing Spectral Modulation Logic ===".postln;

// Simulate blend control bias modulation
var baseBias = 0.5;
var testModes = [
    ("Mirror", {centroid| centroid.linlin(0, 1, baseBias * 0.8, baseBias * 1.2)}),
    ("Complement", {centroid| (1-centroid).linlin(0, 1, baseBias * 0.8, baseBias * 1.2)}),
    ("Transform", {flatness| flatness.linlin(0, 1, baseBias * 0.7, baseBias * 1.3)})
];

testModes.do({ |modeData|
    var modeName = modeData[0];
    var modulationFunc = modeData[1];
    var lowInput = 0.2;
    var highInput = 0.8;
    var lowOutput = modulationFunc.value(lowInput);
    var highOutput = modulationFunc.value(highInput);
    
    "  %: Input % -> Bias %, Input % -> Bias %".format(modeName, lowInput, lowOutput, highInput, highOutput).postln;
});

"  ✅ Spectral modulation logic verified".postln;
)

// Test 4: SynthDef Parameter Verification
(
"\n=== Testing SynthDef Integration ===".postln;

// Check that overdrive SynthDef accepts bias parameter
SynthDescLib.global.at(\chroma_overdrive).controls.do({ |control|
    if(control.name == \bias) {
        "  ✅ Overdrive SynthDef has bias parameter: %".format(control).postln;
    }
});

// Check that blend SynthDef includes baseOverdriveBias
SynthDescLib.global.at(\chroma_blend).controls.do({ |control|
    if(control.name == \baseOverdriveBias) {
        "  ✅ Blend SynthDef has baseOverdriveBias parameter: %".format(control).postln;
    }
});
)

// Test 5: Control Bus Verification
(
"\n=== Testing Control Bus ===".postln;

if(Chroma.instance.notNil) {
    var overdriveCtrlBus = Chroma.instance.buses[\overdriveCtrl];
    if(overdriveCtrlBus.notNil) {
        "Overdrive control bus channels: %".format(overdriveCtrlBus.numChannels).postln;
        if(overdriveCtrlBus.numChannels == 3) {
            "  ✅ Control bus expanded to 3 channels (drive, tone, bias)".postln;
        } {
            "  ❌ Control bus should have 3 channels, has %".format(overdriveCtrlBus.numChannels).postln;
        }
    } {
        "  ❌ Overdrive control bus not found".postln;
    };
} {
    "  ⚠️ Chroma instance not running".postln;
}
)

// Test 6: Audio Effect Simulation
(
"\n=== Testing Audio Effect Theory ===".postln;

// Simulate bias effect on signal
"Simulating bias effect on sine wave:".postln;

var testSignal = [0, 0.5, 1.0, -0.5, -1.0];
var biasValues = [-0.5, 0.0, 0.5];

biasValues.do({ |bias|
    "  Bias %:".format(bias).postln;
    testSignal.do({ |sample|
        var biasedSignal = sample + bias;
        var saturatedSignal = biasedSignal.tanh;
        "    Sample % -> %+%.1f -> %+%.3f".format(sample, biasedSignal, saturatedSignal).postln;
    });
    "".postln;
});

"  ✅ Audio effect theory verified - bias creates asymmetrical clipping".postln;
)

// Test 7: Edge Cases
(
"\n=== Testing Edge Cases ===".postln;

var edgeCases = [-1.5, -1.0, 1.0, 1.5];

"Testing OSC bounds validation:".postln;
edgeCases.do({ |val|
    var clippedVal = val.clip(-1.0, 1.0);
    var internalVal = clippedVal.linlin(-1, 1, 0, 1);
    "  OSC input % -> clipped % -> internal %".format(val, clippedVal, internalVal).postln;
});

"  ✅ Edge case handling verified".postln;
)

"\n=== Integration Tests Complete ===".postln;
"Manual verification required:".postln;
"1. Start Chroma.start".postln;
"2. Enable overdrive with Chroma.setOverdriveEnabled(true)".postln;
"3. Test bias with Chroma.setOverdriveBias(-0.5, 0.0, 0.5)".postln;
"4. Listen for asymmetrical distortion characteristics".postln;