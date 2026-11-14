package ui

import (
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/charmbracelet/lipgloss"
)

// Undercover CLI Design System Tokens (from undercover_cli_design_system.html)
var (
	ColorBlack      = lipgloss.Color("#0A0B0D") // Void Black (canvas)
	ColorSurface    = lipgloss.Color("#121417") // Surface / Cards
	ColorPanel      = lipgloss.Color("#181B20") // Panel background
	ColorBorder     = lipgloss.Color("#262A30") // Border subtle
	ColorBorderHov  = lipgloss.Color("#3A3F48") // Border active
	ColorCream      = lipgloss.Color("#EDE8D0") // Dossier Cream (Primary accent)
	ColorCreamMuted = lipgloss.Color("#C7C2AD") // Secondary Cream
	ColorCreamDark  = lipgloss.Color("#9C9885") // Dim Cream
	ColorGray       = lipgloss.Color("#7E8590") // Muted neutral
	ColorDim        = lipgloss.Color("#606670") // Dark muted
	ColorWelcome    = lipgloss.Color("#F7768E") // Claude-style coral/pink welcome

	// Status / Alert Spectrum
	ColorErr  = lipgloss.Color("#FF5555") // Breach Red / Error
	ColorWarn = lipgloss.Color("#E5A84B") // Alert Amber / Warning
	ColorOk   = lipgloss.Color("#50FA7B") // Tunnel Green / Active
	ColorInfo = lipgloss.Color("#61AFEF") // Cipher Blue / Notice
)

var (
	TitleWelcomeStyle = lipgloss.NewStyle().
				Bold(true).
				Foreground(ColorWelcome)

	TitleStyle = lipgloss.NewStyle().
			Bold(true).
			Foreground(ColorCream).
			Background(ColorPanel).
			Padding(0, 1)

	DividerStyle = lipgloss.NewStyle().
			Foreground(ColorDim)

	SubtitleStyle = lipgloss.NewStyle().
			Foreground(ColorGray)

	PromptSymbol = lipgloss.NewStyle().
			Bold(true).
			Foreground(ColorCream)

	// Dashed box styles (Claude Code aesthetic)
	DashedBox = lipgloss.NewStyle().
			Border(lipgloss.NormalBorder()).
			BorderForeground(lipgloss.Color("#3A3F48")).
			Padding(1, 2)

	DashedSubbox = lipgloss.NewStyle().
			Border(lipgloss.RoundedBorder()).
			BorderForeground(ColorBorder).
			Background(ColorSurface).
			Padding(0, 1)

	// Status Badges
	BadgeErr = lipgloss.NewStyle().
			Foreground(ColorErr).
			Background(lipgloss.Color("#2D1418")).
			Border(lipgloss.NormalBorder()).
			BorderForeground(ColorErr).
			Bold(true).
			Padding(0, 1)

	BadgeWarn = lipgloss.NewStyle().
			Foreground(ColorWarn).
			Background(lipgloss.Color("#2B2012")).
			Border(lipgloss.NormalBorder()).
			BorderForeground(ColorWarn).
			Bold(true).
			Padding(0, 1)

	BadgeOk = lipgloss.NewStyle().
			Foreground(ColorOk).
			Background(lipgloss.Color("#112417")).
			Border(lipgloss.NormalBorder()).
			BorderForeground(ColorOk).
			Bold(true).
			Padding(0, 1)

	BadgeInfo = lipgloss.NewStyle().
			Foreground(ColorInfo).
			Background(lipgloss.Color("#132030")).
			Border(lipgloss.NormalBorder()).
			BorderForeground(ColorInfo).
			Bold(true).
			Padding(0, 1)

	BadgeAccent = lipgloss.NewStyle().
			Foreground(ColorBlack).
			Background(ColorCream).
			Bold(true).
			Padding(0, 1)

	SuccessStyle = lipgloss.NewStyle().
			Foreground(ColorOk).
			Bold(true)

	WarningStyle = lipgloss.NewStyle().
			Foreground(ColorWarn).
			Bold(true)

	ErrorStyle = lipgloss.NewStyle().
			Foreground(ColorErr).
			Bold(true)

	MutedStyle = lipgloss.NewStyle().
			Foreground(ColorGray)

	DossierCard = lipgloss.NewStyle().
			Border(lipgloss.RoundedBorder()).
			BorderForeground(ColorBorder).
			Background(ColorSurface).
			Padding(1, 2)
)

// PrintBanner renders the Onboarding header with dividers
func PrintBanner(w io.Writer, version string) {
	if os.Getenv("NO_COLOR") != "" {
		fmt.Fprintf(w, "Welcome to Undercover CLI v%s\n", version)
		fmt.Fprintln(w, strings.Repeat("-", 60))
		return
	}

	welcome := TitleWelcomeStyle.Render(fmt.Sprintf("Welcome to Undercover CLI v%s", version))
	divider := DividerStyle.Render(strings.Repeat("-", 76))

	fmt.Fprintln(w, welcome)
	fmt.Fprintln(w, divider)
	PrintAsciiScene(w)
	fmt.Fprintln(w, divider)
	fmt.Fprintln(w, SubtitleStyle.Render("Undercover operates stealth private VPN nodes (VLESS Reality) with zero telemetry."))
	fmt.Fprintln(w)
}

// ClearScreen clears the terminal and resets cursor position
func ClearScreen() {
	fmt.Print("\033[H\033[2J")
}

// RenderDashedHeader renders an embedded badge on a dashed line as a pure string
func RenderDashedHeader(title string, totalWidth int) string {
	if totalWidth <= 0 {
		totalWidth = 88
	}
	if os.Getenv("NO_COLOR") != "" {
		return fmt.Sprintf("--- %s ---", title)
	}
	badge := lipgloss.NewStyle().
		Bold(true).
		Foreground(ColorCream).
		Background(ColorPanel).
		Padding(0, 1).
		Render(title)

	badgeWidth := lipgloss.Width(badge)
	leftWidth := 4
	rightWidth := totalWidth - leftWidth - badgeWidth - 2
	if rightWidth < 4 {
		rightWidth = 4
	}

	leftLine := DividerStyle.Render(strings.Repeat("-", leftWidth))
	rightLine := DividerStyle.Render(strings.Repeat("-", rightWidth))
	return fmt.Sprintf("%s %s %s", leftLine, badge, rightLine)
}

// PrintDashedHeader renders an embedded badge on a dashed line to stdout
func PrintDashedHeader(title string) {
	fmt.Println(RenderDashedHeader(title, 88))
}

// Success prints a success message with [✓] or [OK]
func Success(format string, a ...interface{}) {
	msg := fmt.Sprintf(format, a...)
	if os.Getenv("NO_COLOR") != "" {
		fmt.Printf("[OK] %s\n", msg)
		return
	}
	badge := SuccessStyle.Render("[OK]")
	fmt.Printf("%s %s\n", badge, msg)
}

// Warning prints a warning message with [WARN]
func Warning(format string, a ...interface{}) {
	msg := fmt.Sprintf(format, a...)
	if os.Getenv("NO_COLOR") != "" {
		fmt.Printf("[WARN] %s\n", msg)
		return
	}
	badge := WarningStyle.Render("[WARN]")
	fmt.Printf("%s %s\n", badge, msg)
}

// Error prints a structured error message with [ERR]
func Error(what, why, whatToDo string) {
	if os.Getenv("NO_COLOR") != "" {
		fmt.Printf("[ERR] %s\n  Cause: %s\n  Remedy: %s\n", what, why, whatToDo)
		return
	}
	badge := ErrorStyle.Render("[ERR]")
	fmt.Printf("%s %s\n", badge, ErrorStyle.Render(what))
	if why != "" {
		fmt.Printf("      %s %s\n", MutedStyle.Render("Cause:"), why)
	}
	if whatToDo != "" {
		fmt.Printf("      %s %s\n", SuccessStyle.Render("Remedy:"), whatToDo)
	}
}

// StepStatus prints a step state indicator
func StepStatus(done bool, name, detail string) {
	if os.Getenv("NO_COLOR") != "" {
		status := "DONE"
		if !done {
			status = "WAIT"
		}
		fmt.Printf("[%s] %s: %s\n", status, name, detail)
		return
	}

	badge := SuccessStyle.Render("✓")
	if !done {
		badge = WarningStyle.Render("…")
	}
	fmt.Printf("  %s %-22s %s\n", badge, name, MutedStyle.Render(detail))
}
