package ui

import (
	"bytes"
	"strings"
	"testing"
)

func TestPrintAsciiLogo(t *testing.T) {
	var buf bytes.Buffer
	PrintAsciiLogo(&buf)
	out := buf.String()

	if len(out) == 0 {
		t.Fatal("empty logo output")
	}

	if !strings.Contains(out, "████") {
		t.Fatal("expected logo to contain solid block characters")
	}

	if !strings.Contains(out, "▀") || !strings.Contains(out, "▄") {
		t.Fatal("expected logo to contain half-block unicode characters")
	}
}
