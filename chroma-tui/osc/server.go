package osc

import (
	"fmt"

	"github.com/hypebeast/go-osc/osc"
)

type State struct {
	Gain                 float32
	InputFrozen          bool
	InputFreezeLength    float32
	FilterAmount         float32
	FilterCutoff         float32
	FilterResonance      float32
	GranularDensity      float32
	GranularSize         float32
	GranularPitchScatter float32
	GranularPosScatter   float32
	GranularMix          float32
	GranularFrozen       bool
	ReverbDelayBlend     float32
	DecayTime            float32
	ShimmerPitch         float32
	DelayTime            float32
	ModRate              float32
	ModDepth             float32
	ReverbDelayMix       float32
	BlendMode            int
	DryWet               float32
}

type Server struct {
	server    *osc.Server
	stateChan chan State
}

func NewServer(port int) *Server {
	addr := fmt.Sprintf("127.0.0.1:%d", port)
	s := &Server{
		stateChan: make(chan State, 1),
	}

	d := osc.NewStandardDispatcher()
	d.AddMsgHandler("/chroma/state", func(msg *osc.Message) {
		if len(msg.Arguments) >= 21 {
			state := State{
				Gain:                 toFloat32(msg.Arguments[0]),
				InputFrozen:          toInt(msg.Arguments[1]) == 1,
				InputFreezeLength:    toFloat32(msg.Arguments[2]),
				FilterAmount:         toFloat32(msg.Arguments[3]),
				FilterCutoff:         toFloat32(msg.Arguments[4]),
				FilterResonance:      toFloat32(msg.Arguments[5]),
				GranularDensity:      toFloat32(msg.Arguments[6]),
				GranularSize:         toFloat32(msg.Arguments[7]),
				GranularPitchScatter: toFloat32(msg.Arguments[8]),
				GranularPosScatter:   toFloat32(msg.Arguments[9]),
				GranularMix:          toFloat32(msg.Arguments[10]),
				GranularFrozen:       toInt(msg.Arguments[11]) == 1,
				ReverbDelayBlend:     toFloat32(msg.Arguments[12]),
				DecayTime:            toFloat32(msg.Arguments[13]),
				ShimmerPitch:         toFloat32(msg.Arguments[14]),
				DelayTime:            toFloat32(msg.Arguments[15]),
				ModRate:              toFloat32(msg.Arguments[16]),
				ModDepth:             toFloat32(msg.Arguments[17]),
				ReverbDelayMix:       toFloat32(msg.Arguments[18]),
				BlendMode:            toInt(msg.Arguments[19]),
				DryWet:               toFloat32(msg.Arguments[20]),
			}
			// Non-blocking send
			select {
			case s.stateChan <- state:
			default:
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
