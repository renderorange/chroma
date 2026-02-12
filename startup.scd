// startup.scd - Chroma startup script
// Run via ./run.sh for audio device selection

(
var debugLevel;

debugLevel = "CHROMA_DEBUG".getenv;
debugLevel = if(debugLevel.notNil) { debugLevel.asInteger } { 0 };

if(debugLevel >= 1) {
    "startup.scd: Configuring server options...".postln;
};

s.options.sampleRate = 48000;
s.options.blockSize = 64;
s.options.memSize = 8192 * 4;

if(debugLevel >= 1) {
    "startup.scd: sampleRate=%, blockSize=%, memSize=%".format(
        s.options.sampleRate, s.options.blockSize, s.options.memSize
    ).postln;
};

s.waitForBoot {
    "Loading Chroma...".postln;
    Chroma.start(debug: debugLevel);
};
)
