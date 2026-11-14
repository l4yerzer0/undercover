package share

import (
	"fmt"
	"net/url"
)

// VLESSRealityConfig contains parameters needed to assemble a vless:// link
type VLESSRealityConfig struct {
	UUID      string
	Host      string
	Port      int
	PublicKey string
	ServerName string
	ShortID   string
	Alias     string
}

// BuildVLESSRealityLink constructs a standard vless:// share URL
func BuildVLESSRealityLink(cfg VLESSRealityConfig) string {
	q := url.Values{}
	q.Set("security", "reality")
	q.Set("encryption", "none")
	q.Set("pbk", cfg.PublicKey)
	q.Set("headerType", "none")
	q.Set("fp", "chrome")
	q.Set("type", "tcp")
	q.Set("flow", "xtls-rprx-vision")
	q.Set("sni", cfg.ServerName)
	if cfg.ShortID != "" {
		q.Set("sid", cfg.ShortID)
	}

	alias := cfg.Alias
	if alias == "" {
		alias = fmt.Sprintf("Undercover-%s", cfg.Host)
	}

	u := url.URL{
		Scheme:   "vless",
		User:     url.User(cfg.UUID),
		Host:     fmt.Sprintf("%s:%d", cfg.Host, cfg.Port),
		RawQuery: q.Encode(),
		Fragment: alias,
	}

	return u.String()
}

// Hysteria2Config contains parameters needed to assemble a hysteria2:// link
type Hysteria2Config struct {
	Password   string
	Host       string
	Port       int
	ServerName string
	Insecure   bool
	PinSHA256  string
	Alias      string
}

// BuildHysteria2Link constructs a standard hysteria2:// share URL
func BuildHysteria2Link(cfg Hysteria2Config) string {
	q := url.Values{}
	if cfg.ServerName != "" {
		q.Set("sni", cfg.ServerName)
	}
	if cfg.Insecure {
		q.Set("insecure", "1")
	}
	if cfg.PinSHA256 != "" {
		q.Set("pinSHA256", cfg.PinSHA256)
	}

	alias := cfg.Alias
	if alias == "" {
		alias = fmt.Sprintf("Undercover-%s", cfg.Host)
	}

	u := url.URL{
		Scheme:   "hysteria2",
		User:     url.User(cfg.Password),
		Host:     fmt.Sprintf("%s:%d", cfg.Host, cfg.Port),
		RawQuery: q.Encode(),
		Fragment: alias,
	}

	return u.String()
}

// ParsedLink holds normalized information extracted from a share URL
type ParsedLink struct {
	Protocol   string
	Host       string
	Port       int
	ServerName string
	Auth       string // UUID for VLESS, Password for Hysteria2
	PublicKey  string
	ShortID    string
	Insecure   bool
	PinSHA256  string
	Alias      string
}

// ParseShareLink parses vless://, hysteria2:// or hy2:// URLs into structured parameters
func ParseShareLink(rawLink string) (*ParsedLink, error) {
	u, err := url.Parse(rawLink)
	if err != nil {
		return nil, fmt.Errorf("invalid URL format: %w", err)
	}

	var port int
	portStr := u.Port()
	if portStr != "" {
		fmt.Sscanf(portStr, "%d", &port)
	}

	host := u.Hostname()
	query := u.Query()

	switch u.Scheme {
	case "vless":
		if port == 0 {
			port = 443
		}
		uuid := ""
		if u.User != nil {
			uuid = u.User.Username()
		}
		return &ParsedLink{
			Protocol:   "vless-reality",
			Host:       host,
			Port:       port,
			ServerName: query.Get("sni"),
			Auth:       uuid,
			PublicKey:  query.Get("pbk"),
			ShortID:    query.Get("sid"),
			Alias:      u.Fragment,
		}, nil

	case "hysteria2", "hy2":
		if port == 0 {
			port = 443
		}
		password := ""
		if u.User != nil {
			password = u.User.Username()
			if password == "" {
				if pass, ok := u.User.Password(); ok {
					password = pass
				}
			}
		}
		insecure := query.Get("insecure") == "1" || query.Get("insecure") == "true"
		return &ParsedLink{
			Protocol:   "hysteria2",
			Host:       host,
			Port:       port,
			ServerName: query.Get("sni"),
			Auth:       password,
			Insecure:   insecure,
			PinSHA256:  query.Get("pinSHA256"),
			Alias:      u.Fragment,
		}, nil

	default:
		return nil, fmt.Errorf("unsupported protocol scheme: %s", u.Scheme)
	}
}
