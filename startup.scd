// startup.scd - Chroma startup script
// Run via ./run.sh for audio device selection

(
s.options.sampleRate = 48000;
s.options.blockSize = 64;
s.options.memSize = 8192 * 4;

s.waitForBoot {
    "Loading Chroma...".postln;
    Chroma.start;
};
)
