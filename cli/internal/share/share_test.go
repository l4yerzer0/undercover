package share

import (
	"net/url"
	"strings"
	"testing"
)

func TestBuildVLESSRealityLink(t *testing.T) {
	cfg := VLESSRealityConfig{
		UUID:       "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d",
		Host:       "198.51.100.25",
		Port:       443,
		PublicKey:  "test_public_key_x25519_base64",
		ServerName: "gateway.icloud.com",
		ShortID:    "0123456789abcdef",
		Alias:      "MyTestIdentity",
	}

	link := BuildVLESSRealityLink(cfg)

	if !strings.HasPrefix(link, "vless://") {
		t.Fatalf("expected vless:// scheme, got %s", link)
	}

	u, err := url.Parse(link)
	if err != nil {
		t.Fatalf("failed to parse generated link: %v", err)
	}

	if u.User.Username() != cfg.UUID {
		t.Errorf("expected UUID %s, got %s", cfg.UUID, u.User.Username())
	}

	if u.Host != "198.51.100.25:443" {
		t.Errorf("expected host 198.51.100.25:443, got %s", u.Host)
	}

	if u.Fragment != "MyTestIdentity" {
		t.Errorf("expected fragment MyTestIdentity, got %s", u.Fragment)
	}

	q := u.Query()
	if q.Get("security") != "reality" {
		t.Errorf("expected security=reality, got %s", q.Get("security"))
	}
	if q.Get("flow") != "xtls-rprx-vision" {
		t.Errorf("expected flow=xtls-rprx-vision, got %s", q.Get("flow"))
	}
	if q.Get("sni") != "gateway.icloud.com" {
		t.Errorf("expected sni=gateway.icloud.com, got %s", q.Get("sni"))
	}
	if q.Get("pbk") != "test_public_key_x25519_base64" {
		t.Errorf("expected pbk matched, got %s", q.Get("pbk"))
	}
	if q.Get("sid") != "0123456789abcdef" {
		t.Errorf("expected sid matched, got %s", q.Get("sid"))
	}
}

func TestGenerateTerminalQRCode(t *testing.T) {
	qr, err := GenerateTerminalQRCode("vless://test-uuid@1.2.3.4:443?security=reality")
	if err != nil {
		t.Fatalf("failed to generate terminal QR code: %v", err)
	}
	if len(qr) == 0 {
		t.Fatal("empty QR code output")
	}
	if !strings.Contains(qr, "█") && !strings.Contains(qr, "▀") && !strings.Contains(qr, "▄") {
		t.Fatal("expected QR code to contain unicode block characters")
	}
}

func TestBuildHysteria2Link(t *testing.T) {
	cfg := Hysteria2Config{
		Password:   "strong_pass_12345",
		Host:       "203.0.113.10",
		Port:       443,
		ServerName: "gateway.icloud.com",
		Insecure:   true,
		PinSHA256:  "abcdef123456",
		Alias:      "MyHy2Node",
	}

	link := BuildHysteria2Link(cfg)
	if !strings.HasPrefix(link, "hysteria2://") {
		t.Fatalf("expected hysteria2:// scheme, got %s", link)
	}

	u, err := url.Parse(link)
	if err != nil {
		t.Fatalf("failed to parse generated link: %v", err)
	}

	if u.User.Username() != "strong_pass_12345" {
		t.Errorf("expected password strong_pass_12345, got %s", u.User.Username())
	}
	if u.Host != "203.0.113.10:443" {
		t.Errorf("expected host 203.0.113.10:443, got %s", u.Host)
	}
	if u.Fragment != "MyHy2Node" {
		t.Errorf("expected fragment MyHy2Node, got %s", u.Fragment)
	}
	if u.Query().Get("insecure") != "1" {
		t.Errorf("expected insecure=1, got %s", u.Query().Get("insecure"))
	}
	if u.Query().Get("sni") != "gateway.icloud.com" {
		t.Errorf("expected sni=gateway.icloud.com, got %s", u.Query().Get("sni"))
	}
	if u.Query().Get("pinSHA256") != "abcdef123456" {
		t.Errorf("expected pinSHA256 matched, got %s", u.Query().Get("pinSHA256"))
	}

	// Test ParseShareLink on this link
	parsed, err := ParseShareLink(link)
	if err != nil {
		t.Fatalf("failed to parse share link: %v", err)
	}
	if parsed.Protocol != "hysteria2" {
		t.Errorf("expected protocol hysteria2, got %s", parsed.Protocol)
	}
	if parsed.Host != "203.0.113.10" {
		t.Errorf("expected host 203.0.113.10, got %s", parsed.Host)
	}
	if parsed.Port != 443 {
		t.Errorf("expected port 443, got %d", parsed.Port)
	}
	if parsed.Auth != "strong_pass_12345" {
		t.Errorf("expected auth password matched, got %s", parsed.Auth)
	}
	if !parsed.Insecure {
		t.Errorf("expected insecure=true")
	}
	if parsed.PinSHA256 != "abcdef123456" {
		t.Errorf("expected pinSHA256 matched, got %s", parsed.PinSHA256)
	}
}
