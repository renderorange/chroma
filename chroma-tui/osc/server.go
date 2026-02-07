package osc

import (
	"fmt"
	"sync"

	"github.com/hypebeast/go-osc/osc"
)

type State struct {
	Gain                 float32
	InputFrozen          bool
	InputFreezeLength    float32
	FilterAmount         float32
	FilterCutoff         float32
	FilterResonance      float32
	OverdriveDrive       float32
	OverdriveTone        float32
	OverdriveMix         float32
	GranularDensity      float32
	GranularSize         float32
	GranularPitchScatter float32
	GranularPosScatter   float32
	GranularMix          float32
	GranularFrozen       bool
	GrainIntensity       string
	BitcrushEnabled      bool
	BitDepth             float32
	BitcrushSampleRate   float32
	BitcrushDrive        float32
	BitcrushMix          float32
	ReverbEnabled        bool
	ReverbDecayTime      float32
	ReverbMix            float32
	DelayEnabled         bool
	DelayTime            float32
	DelayDecayTime       float32
	ModRate              float32
	ModDepth             float32
	DelayMix             float32
	BlendMode            int
	DryWet               float32

	// Spectrum data (8 bands)
	Spectrum [8]float32
}

type Server struct {
	server       *osc.Server
	stateChan    chan State
	stateMu      sync.RWMutex
	currentState State
}

func NewServer(port int) *Server {
	addr := fmt.Sprintf("127.0.0.1:%d", port)
	s := &Server{
		stateChan: make(chan State, 10), // Buffer for smooth 30fps updates
	}

	d := osc.NewStandardDispatcher()
	d.AddMsgHandler("/chroma/state", func(msg *osc.Message) {
		if len(msg.Arguments) >= 31 {
			state := State{
				Gain:                 toFloat32(msg.Arguments[0]),
				InputFrozen:          toInt(msg.Arguments[1]) == 1,
				InputFreezeLength:    toFloat32(msg.Arguments[2]),
				FilterAmount:         toFloat32(msg.Arguments[3]),
				FilterCutoff:         toFloat32(msg.Arguments[4]),
				FilterResonance:      toFloat32(msg.Arguments[5]),
				OverdriveDrive:       toFloat32(msg.Arguments[6]),
				OverdriveTone:        toFloat32(msg.Arguments[7]),
				OverdriveMix:         toFloat32(msg.Arguments[8]),
				GranularDensity:      toFloat32(msg.Arguments[9]),
				GranularSize:         toFloat32(msg.Arguments[10]),
				GranularPitchScatter: toFloat32(msg.Arguments[11]),
				GranularPosScatter:   toFloat32(msg.Arguments[12]),
				GranularMix:          toFloat32(msg.Arguments[13]),
				GranularFrozen:       toInt(msg.Arguments[14]) == 1,
				GrainIntensity:       toString(msg.Arguments[15]),
				BitcrushEnabled:      toInt(msg.Arguments[16]) == 1,
				BitDepth:             toFloat32(msg.Arguments[17]),
				BitcrushSampleRate:   toFloat32(msg.Arguments[18]),
				BitcrushDrive:        toFloat32(msg.Arguments[19]),
				BitcrushMix:          toFloat32(msg.Arguments[20]),
				ReverbEnabled:        toInt(msg.Arguments[21]) == 1,
				ReverbDecayTime:      toFloat32(msg.Arguments[22]),
				ReverbMix:            toFloat32(msg.Arguments[23]),
				DelayEnabled:         toInt(msg.Arguments[24]) == 1,
				DelayTime:            toFloat32(msg.Arguments[25]),
				DelayDecayTime:       toFloat32(msg.Arguments[26]),
				ModRate:              toFloat32(msg.Arguments[27]),
				ModDepth:             toFloat32(msg.Arguments[28]),
				DelayMix:             toFloat32(msg.Arguments[29]),
				BlendMode:            toInt(msg.Arguments[30]),
				DryWet:               toFloat32(msg.Arguments[31]),
			}
			s.stateMu.Lock()
			existingSpectrum := s.currentState.Spectrum
			s.currentState = state
			s.currentState.Spectrum = existingSpectrum
			s.stateMu.Unlock()
			// Non-blocking send
			select {
			case s.stateChan <- state:
			default:
			}
		}
	})

	// Spectrum data message handler
	d.AddMsgHandler("/chroma/spectrum", func(msg *osc.Message) {
		if len(msg.Arguments) >= 8 {
			var spectrum [8]float32
			for i := 0; i < 8 && i < len(msg.Arguments); i++ {
				if f, ok := msg.Arguments[i].(float32); ok {
					spectrum[i] = f
				} else if f64, ok := msg.Arguments[i].(float64); ok {
					spectrum[i] = float32(f64)
				}
			}
			s.stateMu.Lock()
			s.currentState.Spectrum = spectrum
			state := s.currentState
			s.stateMu.Unlock()
			// Send to channel for real-time updates
			select {
			case s.stateChan <- state:
			default:
				// Channel full, skip update
			}
		}
	})

	s.server = &osc.Server{
		Addr:       addr,
		Dispatcher: d,
	}

	return s
}

func (s *Server) Start() error {
	return s.server.ListenAndServe()
}

func (s *Server) StateChan() <-chan State {
	return s.stateChan
}

func toFloat32(v interface{}) float32 {
	switch val := v.(type) {
	case float32:
		return val
	case float64:
		return float32(val)
	case int32:
		return float32(val)
	case int:
		return float32(val)
	default:
		return 0
	}
}

func toInt(v interface{}) int {
	switch val := v.(type) {
	case int32:
		return int(val)
	case int:
		return val
	case float32:
		return int(val)
	case float64:
		return int(val)
	default:
		return 0
	}
}

func toString(v interface{}) string {
	switch val := v.(type) {
	case string:
		return val
	default:
		return "subtle"
	}
}
