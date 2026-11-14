package singbox

import (
	"encoding/json"
	"strings"
	"testing"
)

func TestBuildServerConfig(t *testing.T) {
	params := ConfigParams{
		Port:       443,
		ServerName: "gateway.icloud.com",
		PrivateKey: "private_key_base64_sample",
		ShortID:    "12345678abcdef01",
		Users: []VLESSUser{
			{
				Name: "alice",
				UUID: "11111111-2222-4333-8444-555555555555",
				Flow: "xtls-rprx-vision",
			},
		},
	}

	cfgBytes, err := BuildServerConfig(params)
	if err != nil {
		t.Fatalf("failed to build server config: %v", err)
	}

	var parsed ServerConfig
	if err := json.Unmarshal(cfgBytes, &parsed); err != nil {
		t.Fatalf("failed to unmarshal generated json: %v", err)
	}

	if parsed.Log.Level != "warn" {
		t.Errorf("expected log level warn, got %s", parsed.Log.Level)
	}

	if len(parsed.Inbounds) != 1 {
		t.Fatalf("expected 1 inbound, got %d", len(parsed.Inbounds))
	}

	inbound := parsed.Inbounds[0]
	if inbound.Type != "vless" {
		t.Errorf("expected inbound type vless, got %s", inbound.Type)
	}
	if inbound.ListenPort != 443 {
		t.Errorf("expected port 443, got %d", inbound.ListenPort)
	}
	if len(inbound.Users) != 1 || inbound.Users[0].Name != "alice" {
		t.Errorf("user alice not properly configured")
	}
	if inbound.TLS == nil || inbound.TLS.Reality == nil {
		t.Fatalf("expected TLS Reality options configured")
	}
	if inbound.TLS.Reality.PrivateKey != "private_key_base64_sample" {
		t.Errorf("private key mismatch")
	}
	if inbound.TLS.Reality.Handshake.Server != "gateway.icloud.com" {
		t.Errorf("expected handshake server gateway.icloud.com, got %s", inbound.TLS.Reality.Handshake.Server)
	}
}

func TestBuildHysteria2ServerConfig(t *testing.T) {
	params := ConfigParams{
		Protocol:   ProtocolHysteria2,
		Port:       443,
		ServerName: "gateway.icloud.com",
		Users: []InboundUser{
			{
				Name:     "bob",
				Password: "secret_password_123",
			},
		},
	}

	cfgBytes, err := BuildServerConfig(params)
	if err != nil {
		t.Fatalf("failed to build hysteria2 config: %v", err)
	}

	var parsed ServerConfig
	if err := json.Unmarshal(cfgBytes, &parsed); err != nil {
		t.Fatalf("failed to unmarshal generated json: %v", err)
	}

	if len(parsed.Inbounds) != 1 {
		t.Fatalf("expected 1 inbound, got %d", len(parsed.Inbounds))
	}

	inbound := parsed.Inbounds[0]
	if inbound.Type != "hysteria2" {
		t.Errorf("expected inbound type hysteria2, got %s", inbound.Type)
	}
	if inbound.ListenPort != 443 {
		t.Errorf("expected port 443, got %d", inbound.ListenPort)
	}
	if len(inbound.Users) != 1 || inbound.Users[0].Name != "bob" || inbound.Users[0].Password != "secret_password_123" {
		t.Errorf("user bob not properly configured: %+v", inbound.Users)
	}
	if inbound.TLS == nil || !inbound.TLS.Enabled {
		t.Fatalf("expected TLS enabled for hysteria2")
	}
	if inbound.TLS.CertificatePath != "/etc/undercover/cert.pem" {
		t.Errorf("expected cert path /etc/undercover/cert.pem, got %s", inbound.TLS.CertificatePath)
	}
	if inbound.TLS.KeyPath != "/etc/undercover/key.pem" {
		t.Errorf("expected key path /etc/undercover/key.pem, got %s", inbound.TLS.KeyPath)
	}
}

func TestCryptoGenerators(t *testing.T) {
	// 1. UUID test
	uuid, err := GenerateUUID()
	if err != nil {
		t.Fatalf("failed to generate UUID: %v", err)
	}
	if len(uuid) != 36 {
		t.Fatalf("expected 36 characters for UUID, got %d (%s)", len(uuid), uuid)
	}
	// Check version 4
	if uuid[14] != '4' {
		t.Errorf("expected UUID version 4, got %c", uuid[14])
	}

	// 2. Reality KeyPair test
	kp, err := GenerateRealityKeyPair()
	if err != nil {
		t.Fatalf("failed to generate reality keypair: %v", err)
	}
	if len(kp.PrivateKey) == 0 || len(kp.PublicKey) == 0 {
		t.Fatal("empty reality keys generated")
	}
	// RawURL base64 should not contain '+' or '/' or '='
	if strings.ContainsAny(kp.PublicKey, "+/=") {
		t.Errorf("public key should be raw url safe base64, got %s", kp.PublicKey)
	}

	// 3. ShortID test
	sid, err := GenerateShortID()
	if err != nil {
		t.Fatalf("failed to generate short ID: %v", err)
	}
	if len(sid) != 16 { // 8 bytes hex = 16 characters
		t.Fatalf("expected 16 hex chars for short ID, got %d (%s)", len(sid), sid)
	}
}

func TestDownloadURLAndChecksum(t *testing.T) {
	url, err := DownloadURL("1.9.7", "amd64")
	if err != nil {
		t.Fatalf("expected valid url, got error: %v", err)
	}
	if !strings.Contains(url, "sing-box-1.9.7-linux-amd64.tar.gz") {
		t.Errorf("unexpected download url: %s", url)
	}

	sum, err := ExpectedChecksum("1.9.7", "amd64")
	if err != nil {
		t.Fatalf("expected valid checksum, got error: %v", err)
	}
	if sum != "d0f4a31b70a194df28d101a05025592a6d26098fa57ffd4f75481968b0fb7878" {
		t.Errorf("unexpected checksum for amd64: %s", sum)
	}
}
