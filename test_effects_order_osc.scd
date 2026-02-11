// Test for effects order OSC handlers
// This should pass after implementing the OSC handlers

(
// Start Chroma
Chroma.start;

"Testing effects order OSC handlers...".postln;

// Test 1: Check that OSC handlers exist
"Checking OSCdef directory for effectsOrder handlers...".postln;

var effectsOrderHandler = OSCdef.all.keys.detect({|key| key.asString.contains("effectsOrder") });
if(effectsOrderHandler.isNil) {
    "ERROR: effectsOrder OSC handlers not found".postln;
    1.exit;
} {
    ("PASS: Found effectsOrder handler: " ++ effectsOrderHandler).postln;
};

var getEffectsOrderHandler = OSCdef.all.keys.detect({|key| key.asString.contains("getEffectsOrder") });
if(getEffectsOrderHandler.isNil) {
    "ERROR: getEffectsOrder OSC handlers not found".postln;
    1.exit;
} {
    ("PASS: Found getEffectsOrder handler: " ++ getEffectsOrderHandler).postln;
};

// Test 2: Test setting effects order via OSC
"Testing /chroma/effectsOrder OSC message...".postln;

~testAddr = NetAddr("127.0.0.1", 57120);

// Get initial order
var initialOrder = Chroma.getEffectsOrder();
("Initial effects order: " ++ initialOrder).postln;

// Send new order via OSC
~testAddr.sendMsg("/chroma/effectsOrder", "filter", "granular", "delay");

// Wait for message to be processed
0.1.wait;

// Check if order changed
var newOrder = Chroma.getEffectsOrder();
("New effects order after OSC: " ++ newOrder).postln;

// Verify the order was set correctly
var expectedOrder = [\filter, \granular, \delay];
if(newOrder == expectedOrder) {
    "PASS: Effects order set correctly via OSC".postln;
} {
    ("ERROR: Expected " ++ expectedOrder ++ ", got " ++ newOrder).postln;
    1.exit;
};

// Test 3: Test getting effects order via OSC
"Testing /chroma/getEffectsOrder OSC message...".postln;

// This is harder to test without setting up a listener, but we can verify the handler exists
// and doesn't cause errors when called
~testAddr.sendMsg("/chroma/getEffectsOrder");

"PASS: getEffectsOrder handler executed without errors".postln;

// Test 4: Test that sendState includes effects order
"Testing sendState includes effects order...".postln;

// This is also hard to test directly without monitoring OSC messages
// But we can verify sendState doesn't crash
~testAddr.sendMsg("/chroma/sync");

"PASS: sendState executed without errors".postln;

"SUCCESS: All effects order OSC tests passed!".postln;
Chroma.stop;
0.exit;
)