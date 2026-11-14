package singbox

import (
	"encoding/json"
)

// ServerConfig represents the top-level sing-box JSON configuration
type ServerConfig struct {
	Log       LogConfig       `json:"log"`
	Inbounds  []InboundConfig `json:"inbounds"`
	Outbounds []Outbound      `json:"outbounds"`
}

type LogConfig struct {
	Disabled  bool   `json:"disabled"`
	Level     string `json:"level"`
	Timestamp bool   `json:"timestamp"`
}

type InboundConfig struct {
	Type                  string        `json:"type"`
	Tag                   string        `json:"tag"`
	Listen                string        `json:"listen"`
	ListenPort            int           `json:"listen_port"`
	Users                 []InboundUser `json:"users,omitempty"`
	TLS                   *InboundTLS   `json:"tls,omitempty"`
	IgnoreClientBandwidth bool          `json:"ignore_client_bandwidth,omitempty"`
}

type InboundUser struct {
	Name     string `json:"name,omitempty"`
	UUID     string `json:"uuid,omitempty"`
	Flow     string `json:"flow,omitempty"`
	Password string `json:"password,omitempty"`
}

// VLESSUser alias for backward compatibility
type VLESSUser = InboundUser

type InboundTLS struct {
	Enabled         bool         `json:"enabled"`
	ServerName      string       `json:"server_name,omitempty"`
	CertificatePath string       `json:"certificate_path,omitempty"`
	KeyPath         string       `json:"key_path,omitempty"`
	Reality         *RealityOpts `json:"reality,omitempty"`
}

type RealityOpts struct {
	Enabled    bool          `json:"enabled"`
	Handshake  HandshakeOpts `json:"handshake"`
	PrivateKey string        `json:"private_key"`
	ShortID    []string      `json:"short_id"`
}

type HandshakeOpts struct {
	Server     string `json:"server"`
	ServerPort int    `json:"server_port"`
}

type Outbound struct {
	Type string `json:"type"`
	Tag  string `json:"tag"`
}

// ConfigParams holds parameters needed to generate the full server config
type ConfigParams struct {
	Protocol        string
	Port            int
	ServerName      string
	PrivateKey      string
	ShortID         string
	CertificatePath string
	KeyPath         string
	Users           []InboundUser
}

// BuildServerConfig creates a hardened sing-box JSON configuration
func BuildServerConfig(params ConfigParams) ([]byte, error) {
	if params.Port <= 0 {
		params.Port = 443
	}
	if params.ServerName == "" {
		params.ServerName = RecommendedDomains[0]
	}

	var inbound InboundConfig
	if params.Protocol == ProtocolHysteria2 {
		certPath := params.CertificatePath
		if certPath == "" {
			certPath = "/etc/undercover/cert.pem"
		}
		keyPath := params.KeyPath
		if keyPath == "" {
			keyPath = "/etc/undercover/key.pem"
		}

		inbound = InboundConfig{
			Type:       "hysteria2",
			Tag:        "hy2-in",
			Listen:     "::",
			ListenPort: params.Port,
			Users:      params.Users,
			TLS: &InboundTLS{
				Enabled:         true,
				ServerName:      params.ServerName,
				CertificatePath: certPath,
				KeyPath:         keyPath,
			},
		}
	} else {
		inbound = InboundConfig{
			Type:       "vless",
			Tag:        "vless-in",
			Listen:     "::",
			ListenPort: params.Port,
			Users:      params.Users,
			TLS: &InboundTLS{
				Enabled:    true,
				ServerName: params.ServerName,
				Reality: &RealityOpts{
					Enabled: true,
					Handshake: HandshakeOpts{
						Server:     params.ServerName,
						ServerPort: 443,
					},
					PrivateKey: params.PrivateKey,
					ShortID:    []string{params.ShortID},
				},
			},
		}
	}

	cfg := ServerConfig{
		Log: LogConfig{
			Disabled:  false,
			Level:     "warn",
			Timestamp: true,
		},
		Inbounds: []InboundConfig{inbound},
		Outbounds: []Outbound{
			{
				Type: "direct",
				Tag:  "direct",
			},
			{
				Type: "block",
				Tag:  "block",
			},
		},
	}

	return json.MarshalIndent(cfg, "", "  ")
}

// SystemdUnitContent returns the hardened systemd unit file content
func SystemdUnitContent() string {
	return `[Unit]
Description=Undercover VPN Service
After=network.target network-online.target nss-lookup.target
Wants=network-online.target

[Service]
Type=simple
User=undercover
Group=undercover
LimitNOFILE=65535
ExecStart=/usr/local/bin/sing-box run -c /etc/undercover/config.json
Restart=on-failure
RestartSec=3s
NoNewPrivileges=yes
ProtectSystem=strict
ProtectHome=yes
ReadWritePaths=/etc/undercover
AmbientCapabilities=CAP_NET_BIND_SERVICE
CapabilityBoundingSet=CAP_NET_BIND_SERVICE

[Install]
WantedBy=multi-user.target
`
}
