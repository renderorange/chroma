// startup.scd - Chroma startup script
// Run this file to launch Chroma

(
s.options.sampleRate = 48000;
s.options.blockSize = 64;
s.options.memSize = 8192 * 4;

s.waitForBoot {
    "Loading Chroma...".postln;

    // Load the Chroma class if not already in extensions
    // (thisProcess.nowExecutingPath.dirname +/+ "Chroma.sc").load;

    Chroma.start;
};
)
