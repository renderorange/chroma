package tui

// Test helper methods for accessing private TUI functionality in tests

// SetFocused allows tests to set the focused control
func (m *Model) SetFocused(ctrl control) {
	m.focused = ctrl
}

// AdjustFocused allows tests to call the private adjustFocused method
func (m *Model) AdjustFocused(delta float32) {
	m.adjustFocused(delta)
}

// ToggleFocused allows tests to call the private toggleFocused method
func (m *Model) ToggleFocused() {
	m.toggleFocused()
}

// SetBlendMode allows tests to call the private setBlendMode method
func (m *Model) SetBlendMode(mode int) {
	m.setBlendMode(mode)
}

// HasPendingChange allows tests to check if a control has pending changes
func (m *Model) HasPendingChange(ctrl control) bool {
	return m.hasPendingChange(ctrl)
}

// TestConstants provides access to control constants for tests
var (
	TestCtrlGain                 = ctrlGain
	TestCtrlInputFreeze          = ctrlInputFreeze
	TestCtrlInputFreezeLen       = ctrlInputFreezeLen
	TestCtrlFilterAmount         = ctrlFilterAmount
	TestCtrlFilterCutoff         = ctrlFilterCutoff
	TestCtrlFilterResonance      = ctrlFilterResonance
	TestCtrlOverdriveDrive       = ctrlOverdriveDrive
	TestCtrlOverdriveTone        = ctrlOverdriveTone
	TestCtrlOverdriveMix         = ctrlOverdriveMix
	TestCtrlGranularDensity      = ctrlGranularDensity
	TestCtrlGranularSize         = ctrlGranularSize
	TestCtrlGranularPitchScatter = ctrlGranularPitchScatter
	TestCtrlGranularPosScatter   = ctrlGranularPosScatter
	TestCtrlGranularMix          = ctrlGranularMix
	TestCtrlGranularFreeze       = ctrlGranularFreeze
	TestCtrlBitcrushEnabled      = ctrlBitcrushEnabled
	TestCtrlBitDepth             = ctrlBitDepth
	TestCtrlBitcrushSampleRate   = ctrlBitcrushSampleRate
	TestCtrlBitcrushDrive        = ctrlBitcrushDrive
	TestCtrlBitcrushMix          = ctrlBitcrushMix
	TestCtrlReverbEnabled        = ctrlReverbEnabled
	TestCtrlReverbDecayTime      = ctrlReverbDecayTime
	TestCtrlReverbMix            = ctrlReverbMix
	TestCtrlDelayEnabled         = ctrlDelayEnabled
	TestCtrlDelayTime            = ctrlDelayTime
	TestCtrlDelayDecayTime       = ctrlDelayDecayTime
	TestCtrlModRate              = ctrlModRate
	TestCtrlModDepth             = ctrlModDepth
	TestCtrlDelayMix             = ctrlDelayMix
	TestCtrlBlendMode            = ctrlBlendMode
	TestCtrlDryWet               = ctrlDryWet
)
