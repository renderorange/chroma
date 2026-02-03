package main

import (
	"flag"
	"fmt"
	"os"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/renderorange/chroma/chroma-tui/osc"
	"github.com/renderorange/chroma/chroma-tui/tui"
)

func main() {
	scHost := flag.String("host", "127.0.0.1", "SuperCollider host")
	scPort := flag.Int("port", 57120, "SuperCollider OSC port")
	listenPort := flag.Int("listen", 9000, "Port to listen for state updates")
	flag.Parse()

	// Create OSC client
	client := osc.NewClient(*scHost, *scPort)

	// Create and run TUI
	model := tui.NewModel(client)

	// Request initial state
	client.SendSync()

	p := tea.NewProgram(model, tea.WithAltScreen())
	if _, err := p.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "Error: %v\n", err)
		os.Exit(1)
	}

	_ = listenPort // Will be used in Task 8
}
