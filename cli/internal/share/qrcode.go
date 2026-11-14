package share

import (
	"strings"

	"github.com/skip2/go-qrcode"
)

// GenerateTerminalQRCode returns a string representing the QR code
// using unicode half-block characters (2 rows per text line) for compact display.
func GenerateTerminalQRCode(content string) (string, error) {
	qr, err := qrcode.New(content, qrcode.Medium)
	if err != nil {
		return "", err
	}

	bitmap := qr.Bitmap()
	size := len(bitmap)

	var sb strings.Builder

	// Add quiet border
	border := 2
	totalSize := size + border*2

	getPixel := func(x, y int) bool {
		bx := x - border
		by := y - border
		if bx < 0 || bx >= size || by < 0 || by >= size {
			return false // white quiet zone
		}
		return bitmap[by][bx] // true = black module
	}

	for y := 0; y < totalSize; y += 2 {
		for x := 0; x < totalSize; x++ {
			top := getPixel(x, y)
			bottom := false
			if y+1 < totalSize {
				bottom = getPixel(x, y+1)
			}

			// In dark terminal (standard for CLI):
			// Foreground (white/cream) is blank/light, background (black) is dark module
			// For standard QR scanners: black module on white background is required.
			// Unicode blocks:
			// '█' = top and bottom black
			// '▀' = top black, bottom white
			// '▄' = top white, bottom black
			// ' ' = both white
			switch {
			case top && bottom:
				sb.WriteString("█")
			case top && !bottom:
				sb.WriteString("▀")
			case !top && bottom:
				sb.WriteString("▄")
			default:
				sb.WriteString(" ")
			}
		}
		sb.WriteString("\n")
	}

	return sb.String(), nil
}
