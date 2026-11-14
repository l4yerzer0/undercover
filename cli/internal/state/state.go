package state

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"time"
)

// RemoteState is stored on the server at /etc/undercover/state.json (0600)
type RemoteState struct {
	Version        string      `json:"version"`
	SingBoxVersion string      `json:"sing_box_version"`
	Protocol       string      `json:"protocol"`
	Port           int         `json:"port"`
	ServerName     string      `json:"server_name"`
	PublicKey      string      `json:"public_key,omitempty"`
	ShortID        string      `json:"short_id,omitempty"`
	CertPinSha256  string      `json:"cert_pin_sha256,omitempty"`
	CreatedAt      time.Time   `json:"created_at"`
	Users          []StateUser `json:"users"`
}

type StateUser struct {
	Name      string    `json:"name"`
	UUID      string    `json:"uuid,omitempty"`
	Password  string    `json:"password,omitempty"`
	CreatedAt time.Time `json:"created_at"`
}

// LocalStore is stored on the client machine at ~/.config/undercover/servers.json
type LocalStore struct {
	Servers []LocalServer `json:"servers"`
}

type LocalServer struct {
	Host           string    `json:"host"`
	SSHPort        int       `json:"ssh_port,omitempty"`
	User           string    `json:"user,omitempty"`
	KeyPath        string    `json:"key_path,omitempty"`
	Alias          string    `json:"alias"`
	Protocol       string    `json:"protocol,omitempty"`
	VPNPort        int       `json:"vpn_port"`
	ServerName     string    `json:"server_name"`
	PublicKey      string    `json:"public_key,omitempty"`
	ShortID        string    `json:"short_id,omitempty"`
	CertPinSha256  string    `json:"cert_pin_sha256,omitempty"`
	UserAuth       string    `json:"user_auth,omitempty"`
	ShareLink      string    `json:"share_link,omitempty"`
	HostKeySha256  string    `json:"host_key_sha256,omitempty"`
	LastConnected  time.Time `json:"last_connected"`
}

func LocalConfigDir() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	dir := filepath.Join(home, ".config", "undercover")
	if err := os.MkdirAll(dir, 0700); err != nil {
		return "", err
	}
	return dir, nil
}

func LocalServersPath() (string, error) {
	dir, err := LocalConfigDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "servers.json"), nil
}

func LoadLocalStore() (*LocalStore, error) {
	path, err := LocalServersPath()
	if err != nil {
		return nil, err
	}

	data, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return &LocalStore{Servers: []LocalServer{}}, nil
	}
	if err != nil {
		return nil, fmt.Errorf("failed to read local servers store: %w", err)
	}

	var store LocalStore
	if err := json.Unmarshal(data, &store); err != nil {
		return nil, fmt.Errorf("invalid local servers store: %w", err)
	}
	return &store, nil
}

func SaveLocalStore(store *LocalStore) error {
	path, err := LocalServersPath()
	if err != nil {
		return err
	}

	data, err := json.MarshalIndent(store, "", "  ")
	if err != nil {
		return err
	}

	return os.WriteFile(path, data, 0600)
}

func (s *LocalStore) AddOrUpdateServer(srv LocalServer) {
	srv.LastConnected = time.Now()
	for i, existing := range s.Servers {
		match := false
		if srv.Alias != "" && existing.Alias == srv.Alias {
			match = true
		} else if existing.Host == srv.Host && existing.VPNPort == srv.VPNPort {
			match = true
		} else if srv.SSHPort > 0 && existing.Host == srv.Host && existing.SSHPort == srv.SSHPort {
			match = true
		}

		if match {
			if srv.UserAuth == "" {
				srv.UserAuth = existing.UserAuth
			}
			if srv.ShareLink == "" {
				srv.ShareLink = existing.ShareLink
			}
			if srv.SSHPort == 0 {
				srv.SSHPort = existing.SSHPort
			}
			if srv.Alias == "" {
				srv.Alias = existing.Alias
			}
			s.Servers[i] = srv
			return
		}
	}
	s.Servers = append(s.Servers, srv)
}

func (s *LocalStore) FindServer(aliasOrHost string) *LocalServer {
	for i := range s.Servers {
		if s.Servers[i].Alias == aliasOrHost || s.Servers[i].Host == aliasOrHost {
			return &s.Servers[i]
		}
	}
	return nil
}

func (s *LocalStore) RemoveServer(aliasOrHost string) bool {
	for i, existing := range s.Servers {
		if existing.Alias == aliasOrHost || existing.Host == aliasOrHost {
			s.Servers = append(s.Servers[:i], s.Servers[i+1:]...)
			return true
		}
	}
	return false
}
