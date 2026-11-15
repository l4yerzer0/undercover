package client

import (
	"encoding/json"
	"testing"
	"undercover/internal/state"
)

func TestGenerateClientConfigVLESSReality(t *testing.T) {
	srv := state.LocalServer{
		Host:       "1.2.3.4",
		VPNPort:    443,
		Protocol:   "vless-reality",
		ServerName: "gateway.icloud.com",
		PublicKey:  "test_public_key",
		ShortID:    "12345678abcdef01",
	}

	cfgBytes, err := GenerateClientConfig(srv, "402e6040-cfc6-4309-8b82-45e0f5923179", 2080)
	if err != nil {
		t.Fatalf("failed to generate client config: %v", err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(cfgBytes, &m); err != nil {
		t.Fatalf("failed to parse generated json: %v", err)
	}

	outbounds, ok := m["outbounds"].([]interface{})
	if !ok || len(outbounds) < 1 {
		t.Fatalf("invalid outbounds")
	}

	firstOutbound, ok := outbounds[0].(map[string]interface{})
	if !ok {
		t.Fatalf("outbound[0] not a map")
	}

	tls, ok := firstOutbound["tls"].(map[string]interface{})
	if !ok {
		t.Fatalf("tls not configured")
	}

	utls, ok := tls["utls"].(map[string]interface{})
	if !ok {
		t.Fatalf("utls not configured in reality client outbound")
	}
	if utls["enabled"] != true {
		t.Errorf("expected utls enabled")
	}
}

func TestGenerateClientConfigHysteria2(t *testing.T) {
	srv := state.LocalServer{
		Host:       "1.2.3.4",
		VPNPort:    443,
		Protocol:   "hysteria2",
		ServerName: "gateway.icloud.com",
	}

	cfgBytes, err := GenerateClientConfig(srv, "secret_pass", 2080)
	if err != nil {
		t.Fatalf("failed to generate client config: %v", err)
	}

	var m map[string]interface{}
	if err := json.Unmarshal(cfgBytes, &m); err != nil {
		t.Fatalf("failed to parse generated json: %v", err)
	}

	outbounds, ok := m["outbounds"].([]interface{})
	if !ok || len(outbounds) < 1 {
		t.Fatalf("invalid outbounds")
	}

	firstOutbound, ok := outbounds[0].(map[string]interface{})
	if !ok {
		t.Fatalf("outbound[0] not a map")
	}
	if firstOutbound["type"] != "hysteria2" {
		t.Errorf("expected hysteria2 outbound type, got %v", firstOutbound["type"])
	}
}
