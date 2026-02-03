package main

import (
	"flag"
	"fmt"
	"os"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/renderorange/chroma/chroma-tui/config"
	"github.com/renderorange/chroma/chroma-tui/midi"
	"github.com/renderorange/chroma/chroma-tui/osc"
	"github.com/renderorange/chroma/chroma-tui/tui"
)

func main() {
	scHost := flag.String("host", "127.0.0.1", "SuperCollider host")
	scPort := flag.Int("port", 57120, "SuperCollider OSC port")
	listenPort := flag.Int("listen", 9000, "Port to listen for state updates")
	noMidi := flag.Bool("no-midi", false, "Disable MIDI input")
	flag.Parse()

	// Create OSC client and server
	client := osc.NewClient(*scHost, *scPort)
	server := osc.NewServer(*listenPort)

	// Start OSC server in background
	go func() {
		if err := server.Start(); err != nil {
			fmt.Fprintf(os.Stderr, "OSC server error: %v\n", err)
		}
	}()

	// Create TUI model
	model := tui.NewModel(client)

	// Start MIDI handler
	var midiHandler *midi.Handler
	if !*noMidi {
		cfg := config.Load()
		midiHandler = midi.NewHandler(client, cfg)
		if err := midiHandler.Start(); err != nil {
			fmt.Fprintf(os.Stderr, "MIDI warning: %v\n", err)
		} else {
			model.SetMidiPort(midiHandler.PortName())
			defer midiHandler.Stop()
		}
	}

	// Create program
	p := tea.NewProgram(model, tea.WithAltScreen())

	// Forward state updates to TUI
	go func() {
		for state := range server.StateChan() {
			p.Send(tui.StateMsg(state))
		}
	}()

	// Request initial state
	client.SendSync()

	if _, err := p.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}
}
