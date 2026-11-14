package ui

import (
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/charmbracelet/lipgloss"
)

// PrintAsciiLogo renders the official Undercover decomposing U logo in high-fidelity Unicode half-blocks
func PrintAsciiLogo(w io.Writer) {
	logoLines := []string{
		"                                ███   ",
		"                               ▀    ▀ ",
		"                     ▄▄▄     ▄▄███    ",
		"  ████████           ███      ▄▄▄ ▀▀  ",
		"  ████████           ███ ▄▄▄  ███▄▄   ",
		"  ████████           ▄▄▄▄█▀▀  ██   ▀▀ ",
		"  ████████           █████ ███  ▄ ▀▀▀ ",
		"  ████████           ▀▀▀▀▀ ███        ",
		"  ████████           █████████        ",
		"  ████████           █████████        ",
		"  ████████▄          █████████        ",
		"  █████████▄▄      ▄█████████▀        ",
		"   ▀████████████████████████▀         ",
		"    ▀▀████████████████████▀           ",
		"       ▀▀█████████████▀▀▀             ",
		"             ▀▀▀▀▀▀                   ",
	}

	indent := strings.Repeat(" ", 18)

	if os.Getenv("NO_COLOR") != "" {
		for _, line := range logoLines {
			fmt.Fprintf(w, "%s%s\n", indent, line)
		}
		return
	}

	creamStyle := lipgloss.NewStyle().Foreground(ColorCream)

	for _, line := range logoLines {
		fmt.Fprintf(w, "%s%s\n", indent, creamStyle.Render(line))
	}
}

// PrintAsciiScene aliases to PrintAsciiLogo for compatibility
func PrintAsciiScene(w io.Writer) {
	PrintAsciiLogo(w)
}
