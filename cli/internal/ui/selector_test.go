package ui

import (
	"strings"
	"testing"
)

func TestSelectorModelView(t *testing.T) {
	options := []SelectorOption{
		{
			ID:          "opt1",
			Number:      "1",
			Title:       "Option One",
			Description: "First description text",
		},
		{
			ID:          "opt2",
			Number:      "2",
			Title:       "Option Two",
			Description: "Second description text",
		},
	}

	m := selectorModel{
		headerTitle:    "TEST HEADER",
		headerSubtitle: "Test Subtitle",
		showAsciiScene: false,
		options:        options,
		cursor:         0,
		width:          90,
	}

	view := m.View()

	// Verify no double pointers (> >)
	if strings.Contains(view, "> >") {
		t.Errorf("found unwanted double pointer '> >' in view:\n%s", view)
	}

	// Verify pointer is present for active option
	if !strings.Contains(view, "> 1. Option One") {
		t.Errorf("expected pointer before option 1, got:\n%s", view)
	}

	// Verify unselected option has 4 spaces indent aligning with active
	if !strings.Contains(view, "    2. Option Two") {
		t.Errorf("expected 4 spaces indent before option 2, got:\n%s", view)
	}

	// Verify header rendered as pure string without calling fmt.Print
	if !strings.Contains(view, "TEST HEADER") {
		t.Errorf("expected header in view, got:\n%s", view)
	}
	if !strings.Contains(view, "Test Subtitle") {
		t.Errorf("expected subtitle in view, got:\n%s", view)
	}

	// Verify bottom action bar
	if !strings.Contains(view, "ПРОДОЛЖИТЬ [ENTER] ↵") {
		t.Errorf("expected bottom action button, got:\n%s", view)
	}

	// Move cursor to option 2 and verify
	m.cursor = 1
	view2 := m.View()
	if strings.Contains(view2, "> >") {
		t.Errorf("found unwanted double pointer '> >' in view2:\n%s", view2)
	}
	if !strings.Contains(view2, "    1. Option One") {
		t.Errorf("expected 4 spaces indent before option 1 in view2, got:\n%s", view2)
	}
	if !strings.Contains(view2, "> 2. Option Two") {
		t.Errorf("expected pointer before option 2 in view2, got:\n%s", view2)
	}
}
