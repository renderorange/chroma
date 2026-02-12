// manual_test_procedures.sc - Manual audio testing procedures
// Step-by-step interactive testing for Chroma audio system

// Load Chroma first
"Loading Chroma for manual testing...".postln;
Chroma.start(debug: 2); // Verbose debugging

(
// ============ MANUAL TESTING PROCEDURES ============

~stepByStepTesting = {
    "=== STEP-BY-STEP AUDIO TESTING ===".postln;
    "This will guide you through testing each component manually.".postln;
    "Press Enter after each step to continue...".postln;
    "".postln;
    
    // Step 1: Server Status
    "Step 1: Checking SuperCollider server status".postln;
    ("Server running: %".format(Server.default.serverRunning)).postln;
    ("Sample rate: % Hz".format(Server.default.sampleRate)).postln;
    ("Input channels: %".format(Server.default.options.numInputBusChannels)).postln;
    ("Output channels: %".format(Server.default.options.numOutputChannels)).postln;
    "".postln;
    "Press Enter to continue...".postln;
    "".postln;
    
    2.wait; // Give user time to read
    
    // Step 2: Basic Audio Input
    "Step 2: Testing basic audio input".postln;
    "Make some noise or play audio into your input device...".postln;
    
    var inputMonitor = {
        var input = SoundIn.ar(~chroma.config[\inputChannel]);
        var amp = Amplitude.kr(input, 0.01, 0.1);
        amp.poll(0.2, "input_level");
        Silent.ar;
    }.play;
    
    "Monitoring input levels for 5 seconds...".postln;
    5.wait;
    inputMonitor.free();
    
    "Did you see input level activity above 0.001?".postln;
    "".postln;
    2.wait;
    
    // Step 3: Effects Chain
    "Step 3: Testing effects chain connectivity".postln;
    
    // Enable one effect at a time to test chain
    "Enabling filter effect...".postln;
    ~chroma.setFilterEnabled(true);
    ~chroma.setFilterAmount(0.5);
    2.wait;
    
    "Enabling overdrive effect...".postln;
    ~chroma.setOverdriveEnabled(true);
    ~chroma.setOverdriveDrive(0.3);
    2.wait;
    
    "Effects chain test complete".postln;
    "".postln;
    2.wait;
    
    // Step 4: Output Test
    "Step 4: Testing audio output".postln;
    "You should hear processed audio output now...".postln;
    "Set dry/wet to 50% to hear effects:".postln;
    ~chroma.setDryWet(0.5);
    
    3.wait;
    
    "Do you hear audio output?".postln;
    "".postln;
    2.wait;
    
    "=== MANUAL TESTING COMPLETE ===".postln;
    "Review the outputs above for any issues".postln;
};

~interactiveTroubleshooting = {
    "=== INTERACTIVE TROUBLESHOOTING ===".postln;
    "Select a troubleshooting option:".postln;
    "1. No audio input".postln;
    "2. No audio output".postln;
    "3. Effects not working".postln;
    "4. OSC communication issues".postln;
    "5. Performance problems".postln;
    "".postln;
    
    // In practice, user would input choice here
    // For now, provide diagnostic info for each issue
};

~noAudioInputHelp = {
    "=== TROUBLESHOOTING: NO AUDIO INPUT ===".postln;
    "".postln;
    "Check the following:".postln;
    "1. Input channel number: %".format(~chroma.config[\inputChannel]).postln;
    "2. Available input channels: %".format(Server.default.options.numInputBusChannels).postln;
    "3. Audio device connection".postln;
    "4. JACK audio server status".postln;
    "".postln;
    
    "Diagnostic commands:".postln;
    "  Server.default.dump".postln;
    "  ~chroma.setDebugLevel(2)".postln;
    "  ~chroma.validateInputChannel(0)".postln;
    "".postln;
    
    "Test with different input channel:".postln;
    "  ~chroma.config[\inputChannel] = 1;".postln;
    "  // Restart Chroma after changing config".postln;
};

~noAudioOutputHelp = {
    "=== TROUBLESHOOTING: NO AUDIO OUTPUT ===".postln;
    "".postln;
    "Check the following:".postln;
    "1. Output channel availability: %".format(Server.default.options.numOutputChannels).postln;
    "2. JACK audio server connection".postln;
    "3. Speaker/headphone connection".postln;
    "4. System audio volume".postln;
    "".postln;
    
    "Diagnostic commands:".postln;
    "  Server.default.options.dump".postln;
    "  s.scope".postln; // Open scope to see audio
    "  s.freqscope".postln; // Open frequency scope
    "".postln;
    
    "Test audio routing:".postln;
    "  { SinOsc.ar(440) * 0.1 }.play; // Test tone".postln;
    "  // You should hear a 440Hz tone".postln;
};

~effectsNotWorkingHelp = {
    "=== TROUBLESHOOTING: EFFECTS NOT WORKING ===".postln;
    "".postln;
    "Check the following:".postln;
    "1. Effects chain order: %".format(~chroma.getEffectsOrder()).postln;
    "2. Individual effect enable status".postln;
    "3. Dry/wet mix: %".format(~chroma.dryWet).postln;
    "".postln;
    
    "Enable each effect individually:".postln;
    "  ~chroma.setFilterEnabled(true)".postln;
    "  ~chroma.setOverdriveEnabled(true)".postln;
    "  ~chroma.setGranularEnabled(true)".postln;
    "  ~chroma.setReverbEnabled(true)".postln;
    "  ~chroma.setDelayEnabled(true)".postln;
    "  ~chroma.setBitcrushEnabled(true)".postln;
    "".postln;
    
    "Check effect parameters:".postln;
    "  ~chroma.filterParams.postln;".postln;
    "  ~chroma.overdriveParams.postln;".postln;
    "  ~chroma.granularParams.postln;".postln;
};

~oscCommunicationHelp = {
    "=== TROUBLESHOOTING: OSC COMMUNICATION ===".postln;
    "".postln;
    "Check the following:".postln;
    "1. OSC responders are registered".postln;
    "2. Port 57120 is available".postln;
    "3. Network connectivity".postln;
    "".postln;
    
    "Test OSC communication:".postln;
    "  n = NetAddr(\"127.0.0.1\", 57120)".postln;
    "  n.sendMsg(\"/chroma/gain\", 0.8)".postln;
    "  n.sendMsg(\"/chroma/filterAmount\", 0.5)".postln;
    "".postln;
    
    "Enable OSC debugging:".postln;
    "  OSCFunc.trace(true)".postln;
    "  // This will show all OSC messages".postln;
};

~performanceHelp = {
    "=== TROUBLESHOOTING: PERFORMANCE PROBLEMS ===".postln;
    "".postln;
    "Check system performance:".postln;
    "1. CPU usage: %".format(s.avgCPU).postln;
    "2. Server load: %".format(s.peakCPU).postln;
    "3. UGen count: %".format(s.numUGens).postln;
    "4. Synth count: %".format(s.numSynths).postln;
    "".postln;
    
    "Performance optimization:".postln;
    "1. Reduce effect density (especially granular)".postln;
    "2. Lower dry/wet mix to reduce processing".postln;
    "3. Disable unused effects".postln;
    "4. Increase server block size".postln;
    "".postln;
    
    "Monitor performance:".postln;
    "  { s.avgCPU.poll(1, \"cpu\") }.play".postln;
    "  s.plotTree".postln; // Show server node tree
};

~quickHealthCheck = {
    "=== QUICK HEALTH CHECK ===".postln;
    "".postln;
    
    // Server status
    ("Server status: %".format(if(Server.default.serverRunning, {"Running"}, {"Stopped"}))).postln;
    ("CPU usage: %%%".format((s.avgCPU * 100).round(1))).postln;
    ("UGens: %, Synths: %".format(s.numUGens, s.numSynths)).postln;
    "".postln;
    
    // Chroma status
    ("Chroma instance: %".format(if(Chroma.instance.notNil, {"Active"}, {"None"}))).postln;
    ("Debug level: %".format(~chroma.debugLevel)).postln;
    ("Input channel: %".format(~chroma.config[\inputChannel])).postln;
    ("Dry/Wet mix: %".format(~chroma.dryWet)).postln;
    "".postln;
    
    // Effect status
    "Effect status:".postln;
    ("  Filter: %".format(if(~chroma.filterParams[\enabled], {"Enabled"}, {"Disabled"}))).postln;
    ("  Overdrive: %".format(if(~chroma.overdriveParams[\enabled], {"Enabled"}, {"Disabled"}))).postln;
    ("  Granular: %".format(if(~chroma.granularParams[\enabled], {"Enabled"}, {"Disabled"}))).postln;
    ("  Reverb: %".format(if(~chroma.reverbParams[\enabled], {"Enabled"}, {"Disabled"}))).postln;
    ("  Delay: %".format(if(~chroma.delayParams[\enabled], {"Enabled"}, {"Disabled"}))).postln;
    ("  Bitcrush: %".format(if(~chroma.bitcrushParams[\enabled], {"Enabled"}, {"Disabled"}))).postln;
    "".postln;
    
    "=== HEALTH CHECK COMPLETE ===".postln;
};

// ============ INTERACTIVE COMMANDS ============

"=== MANUAL TESTING PROCEDURES LOADED ===".postln;
"Available commands:".postln;
"  ~stepByStepTesting.value".postln;
"  ~quickHealthCheck.value".postln;
"  ~noAudioInputHelp.value".postln;
"  ~noAudioOutputHelp.value".postln;
"  ~effectsNotWorkingHelp.value".postln;
"  ~oscCommunicationHelp.value".postln;
"  ~performanceHelp.value".postln;
"".postln;
"Quick start:".postln;
"  ~quickHealthCheck.value; // Check system status".postln;
"  ~stepByStepTesting.value; // Full guided test".postln;
"  ~noAudioInputHelp.value; // Troubleshoot input issues".postln;
)