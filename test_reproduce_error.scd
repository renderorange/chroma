// Test script to reproduce call stack error
// This should fail with the current code
SynthDef(\test_condition, { |grainIntensityMultiplier=1.0|
    // This should cause the error
    if(grainIntensityMultiplier >= 6.0) {
        "This won't work".postln;
    };
}).add;