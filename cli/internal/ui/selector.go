package ui

import (
	"bytes"
	"fmt"
	"strings"

	tea "github.com/charmbracelet/bubbletea"
	"github.com/charmbracelet/lipgloss"
)

type SelectorOption struct {
	ID          string
	Number      string
	Title       string
	Description string
	Badge       string
}

type selectorModel struct {
	headerTitle    string
	headerSubtitle string
	showAsciiScene bool
	version        string
	options        []SelectorOption
	cursor         int
	chosen         string
	quitting       bool
	width          int
	height         int
}

func (m selectorModel) Init() tea.Cmd {
	return nil
}

func (m selectorModel) Update(msg tea.Msg) (tea.Model, tea.Cmd) {
	switch msg := msg.(type) {
	case tea.WindowSizeMsg:
		m.width = msg.Width
		m.height = msg.Height
		return m, nil

	case tea.KeyMsg:
		switch msg.String() {
		case "ctrl+c", "q", "esc":
			m.quitting = true
			return m, tea.Quit

		case "up", "k":
			if m.cursor > 0 {
				m.cursor--
			} else {
				m.cursor = len(m.options) - 1
			}

		case "down", "j":
			if m.cursor < len(m.options)-1 {
				m.cursor++
			} else {
				m.cursor = 0
			}

		case "1", "2", "3", "4", "5", "6", "7", "8", "9":
			idx := int(msg.Runes[0] - '1')
			if idx >= 0 && idx < len(m.options) {
				m.cursor = idx
				m.chosen = m.options[idx].ID
				return m, tea.Quit
			}

		case "enter", " ":
			if m.cursor >= 0 && m.cursor < len(m.options) {
				m.chosen = m.options[m.cursor].ID
			}
			return m, tea.Quit
		}
	}
	return m, nil
}

func (m selectorModel) View() string {
	var b strings.Builder

	cardWidth := 90
	if m.width > 0 {
		if m.width-4 > cardWidth {
			cardWidth = m.width - 4
			if cardWidth > 96 {
				cardWidth = 96
			}
		} else if m.width-4 < cardWidth && m.width-4 >= 60 {
			cardWidth = m.width - 4
		}
	}

	// Top Header
	if m.showAsciiScene {
		b.WriteString(TitleWelcomeStyle.Render(fmt.Sprintf("Welcome to Undercover CLI v%s", m.version)))
		b.WriteString("\n")
		divider := DividerStyle.Render(strings.Repeat("-", cardWidth))
		b.WriteString(divider)
		b.WriteString("\n\n")

		var sceneBuf bytes.Buffer
		PrintAsciiLogo(&sceneBuf)
		b.WriteString(sceneBuf.String())
		b.WriteString("\n")

		b.WriteString(divider)
		b.WriteString("\n")
		b.WriteString(SubtitleStyle.Render("Undercover operates stealth anti-censorship nodes (VLESS Reality & Hysteria 2) with zero telemetry and memory-only logs."))
		b.WriteString("\n\n")
	} else if m.headerTitle != "" {
		b.WriteString(RenderDashedHeader(m.headerTitle, cardWidth))
		b.WriteString("\n")
		if m.headerSubtitle != "" {
			b.WriteString(SubtitleStyle.Render(m.headerSubtitle))
			b.WriteString("\n")
		}
		b.WriteString("\n")
	}

	promptTitle := "Select operating mode:"
	if !m.showAsciiScene && m.headerTitle != "" {
		promptTitle = "Select action:"
	}
	b.WriteString(lipgloss.NewStyle().Bold(true).Foreground(ColorCream).Render(promptTitle))
	b.WriteString("\n\n")

	// Render Options in exact Onboarding style
	for i, opt := range m.options {
		isSelected := (i == m.cursor)

		// 1 blank line spacing between options
		if i > 0 {
			b.WriteString("\n")
		}

		numPrefix := ""
		if opt.Number != "" {
			numPrefix = opt.Number + ". "
		}

		if isSelected {
			pointer := PromptSymbol.Render(">") + " "
			titleText := lipgloss.NewStyle().Bold(true).Foreground(ColorCream).Render(numPrefix + opt.Title)
			descText := lipgloss.NewStyle().Foreground(ColorGray).Render(" • " + opt.Description)
			lineContent := pointer + titleText + descText

			// Claude Code Boxed Style: rounded cream border with dark surface background
			card := lipgloss.NewStyle().
				Border(lipgloss.RoundedBorder()).
				BorderForeground(ColorCream).
				Background(ColorSurface).
				Padding(0, 1).
				Width(cardWidth).
				Render(lineContent)

			b.WriteString(card)
			b.WriteString("\n")
		} else {
			indent := "    " // 4 spaces aligns with "│ > " (1 border + 1 pad + 2 pointer)
			titleText := lipgloss.NewStyle().Foreground(ColorCreamMuted).Render(numPrefix + opt.Title)
			descText := lipgloss.NewStyle().Foreground(ColorGray).Render(" • " + opt.Description)
			lineContent := indent + titleText + descText

			b.WriteString(lineContent)
			b.WriteString("\n")
		}
	}

	// Bottom hotkeys / navigation helper matching onboarding.png
	b.WriteString("\n")
	btn := lipgloss.NewStyle().
		Bold(true).
		Foreground(ColorBlack).
		Background(ColorCream).
		Padding(0, 1).
		Render("ПРОДОЛЖИТЬ [ENTER] ↵")

	kbd1 := lipgloss.NewStyle().Foreground(ColorCream).Background(ColorSurface).Padding(0, 1).Render("1")
	kbd2 := lipgloss.NewStyle().Foreground(ColorCream).Background(ColorSurface).Padding(0, 1).Render("2")
	kbdUp := lipgloss.NewStyle().Foreground(ColorCream).Background(ColorSurface).Padding(0, 1).Render("↑")
	kbdDown := lipgloss.NewStyle().Foreground(ColorCream).Background(ColorSurface).Padding(0, 1).Render("↓")

	var navText string
	if len(m.options) == 2 {
		navText = lipgloss.NewStyle().Foreground(ColorGray).Render("  Навигация: клавиши ") +
			kbd1 + lipgloss.NewStyle().Foreground(ColorGray).Render(", ") +
			kbd2 + lipgloss.NewStyle().Foreground(ColorGray).Render(" или стрелки ") +
			kbdUp + " " + kbdDown
	} else {
		maxKey := fmt.Sprintf("%d", len(m.options))
		navText = lipgloss.NewStyle().Foreground(ColorGray).Render("  Навигация: клавиши 1-") +
			lipgloss.NewStyle().Foreground(ColorCream).Background(ColorSurface).Padding(0, 1).Render(maxKey) +
			lipgloss.NewStyle().Foreground(ColorGray).Render(" или стрелки ") +
			kbdUp + " " + kbdDown +
			lipgloss.NewStyle().Foreground(ColorGray).Render("  •  Выход [q]")
	}

	b.WriteString(btn + navText)
	b.WriteString("\n")

	return b.String()
}

// RunOnboardingMenu displays the exact Claude Code onboarding screen from the design system
func RunOnboardingMenu(version string) (string, error) {
	options := []SelectorOption{
		{
			ID:          "manager",
			Number:      "1",
			Title:       "Менеджер",
			Description: "Развертывание, администрирование VPS-узлов и выпуск профилей доступа",
		},
		{
			ID:          "client",
			Number:      "2",
			Title:       "Клиент",
			Description: "Подключение рабочей станции к периметру, туннелирование и Direct-правила",
		},
	}

	m := selectorModel{
		showAsciiScene: true,
		version:        version,
		options:        options,
		cursor:         0,
	}

	p := tea.NewProgram(m)
	finalModel, err := p.Run()
	if err != nil {
		return "", err
	}

	fm, ok := finalModel.(selectorModel)
	if !ok || fm.quitting {
		return "exit", nil
	}

	return fm.chosen, nil
}

// RunCustomSelector displays a styled menu with the exact design system aesthetic
func RunCustomSelector(title, subtitle string, options []SelectorOption) (string, error) {
	m := selectorModel{
		headerTitle:    title,
		headerSubtitle: subtitle,
		showAsciiScene: false,
		options:        options,
		cursor:         0,
	}

	p := tea.NewProgram(m)
	finalModel, err := p.Run()
	if err != nil {
		return "", err
	}

	fm, ok := finalModel.(selectorModel)
	if !ok || fm.quitting {
		return "back", nil
	}

	return fm.chosen, nil
}
