// audio_diagnostics.sc - Audio diagnostics and monitoring tools
// Run this script to troubleshoot Chroma audio signal chain issues

// Load Chroma first
"Loading Chroma for diagnostics...".postln;
Chroma.start(debug: 2); // Verbose debugging

(
// ============ AUDIO DIAGNOSTIC TOOLS ============

~monitorBus = { |busName, duration=2.0|
    var bus, monitor, level;
    
    if(~chroma.buses[busName].notNil) {
        bus = ~chroma.buses[busName];
        ("Monitoring bus % for % seconds...".format(busName, duration)).postln;
        
        monitor = {
            var sig = In.ar(bus);
            level = Amplitude.kr(sig, 0.01, 0.1);
            level.poll(1, "%_level".format(busName));
            Out.ar(0, sig * 0.1); // Monitor output at low volume
        }.play;
        
        duration.wait;
        monitor.free;
        ("Finished monitoring %".format(busName)).postln;
    } {
        ("ERROR: Bus % not found!".format(busName)).error;
    };
};

~validateBusRouting = {
    "=== VALIDATING BUS ROUTING ===".postln;
    
    // Check essential buses
    var essentialBuses = [\inputAudio, \frozenAudio, \bands, \centroid, \spread, \flatness];
    essentialBuses.do { |busName|
        var bus = ~chroma.buses[busName];
        if(bus.notNil) {
            ("✓ Bus %: allocated (index %)".format(busName, bus.index)).postln;
        } {
            ("✗ Bus %: NOT ALLOCATED".format(busName)).error;
        };
    };
    
    // Check effect buses
    var effectBuses = [\filterToOverdrive, \overdriveToBitcrush, \bitcrushToGranular, \granularToReverb, \reverbToDelay, \delayAudio];
    effectBuses.do { |busName|
        var bus = ~chroma.buses[busName];
        if(bus.notNil) {
            ("✓ Effect bus %: allocated (index %)".format(busName, bus.index)).postln;
        } {
            ("✗ Effect bus %: NOT ALLOCATED".format(busName)).error;
        };
    };
    
    "=== BUS VALIDATION COMPLETE ===".postln;
};

~checkSignalLevels = {
    "=== CHECKING SIGNAL LEVELS ===".postln;
    
    // Check input level
    if(~chroma.buses[\inputAmp].notNil) {
        var inputLevelBus = ~chroma.buses[\inputAmp];
        var inputMonitor = {
            var level = In.kr(inputLevelBus);
            level.poll(0.5, "input_amplitude");
            Silent.ar;
        }.play;
        
        3.wait;
        inputMonitor.free;
    };
    
    // Check spectral analysis
    if(~chroma.buses[\bands].notNil) {
        var bandsBus = ~chroma.buses[\bands];
        var bandsMonitor = {
            var bands = In.kr(bandsBus, 8);
            var totalEnergy = bands.sum;
            totalEnergy.poll(0.5, "spectral_energy");
            Silent.ar;
        }.play;
        
        3.wait;
        bandsMonitor.free;
    };
    
    "=== SIGNAL LEVEL CHECK COMPLETE ===".postln;
};

~reportBusStatus = {
    "=== COMPREHENSIVE BUS STATUS ===".postln;
    
    ~chroma.buses.keys.do { |busName|
        var bus = ~chroma.buses[busName];
        if(bus.notNil) {
            var type = if(bus.rate == \audio, {"Audio"}, {"Control"});
            ("Bus %: % type, index %, channels %".format(
                busName, type, bus.index, bus.numChannels)).postln;
        };
    };
    
    "=== BUS STATUS REPORT COMPLETE ===".postln;
};

~testSignalFlow = {
    "=== TESTING SIGNAL FLOW ===".postln;
    
    // Generate test signal (440Hz sine wave at low volume)
    var testSynth = {
        var testSig = SinOsc.ar(440) * 0.1; // Low volume sine wave
        Out.ar(~chroma.buses[\inputAudio].index, testSig);
    }.play;
    
    "Test signal (440Hz sine) injected into input bus".postln;
    "Monitoring output for 5 seconds...".postln;
    
    5.wait;
    testSynth.free;
    "Signal flow test complete".postln;
};

~runDiagnostics = {
    "=== RUNNING FULL AUDIO DIAGNOSTICS ===".postln;
    
    ~validateBusRouting.value;
    ~checkSignalLevels.value;
    ~reportBusStatus.value;
    ~testSignalFlow.value;
    
    "=== DIAGNOSTICS COMPLETE ===".postln;
    "Check the output above for any errors or warnings".postln;
};

// ============ INTERACTIVE COMMANDS ============

"=== AUDIO DIAGNOSTICS LOADED ===".postln;
"Available commands:".postln;
"  ~monitorBus.value('busName', duration)".postln;
"  ~validateBusRouting.value".postln;
"  ~checkSignalLevels.value".postln;
"  ~reportBusStatus.value".postln;
"  ~testSignalFlow.value".postln;
"  ~runDiagnostics.value".postln;
"  ~chroma.setDebugLevel(2)".postln;
"".postln;
"Example usage:".postln;
"  ~runDiagnostics.value; // Run complete diagnostic suite".postln;
"  ~monitorBus.value('frozenAudio', 3.0); // Monitor specific bus".postln;
)