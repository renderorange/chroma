//go:build !cgo

package midi

import (
	"fmt"

	"github.com/renderorange/chroma/chroma-tui/config"
	"github.com/renderorange/chroma/chroma-tui/osc"
)

type Handler struct {
	client *osc.Client
	config config.Config
}

func NewHandler(client *osc.Client, cfg config.Config) *Handler {
	return &Handler{
		client: client,
		config: cfg,
	}
}

func (h *Handler) Start() error {
	return fmt.Errorf("MIDI not available (this binary was built without CGO support)")
}

func (h *Handler) Stop() {
}

func (h *Handler) PortName() string {
	return ""
}
