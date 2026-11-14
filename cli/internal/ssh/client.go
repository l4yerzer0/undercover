package ssh

import (
	"bytes"
	"crypto/sha256"
	"encoding/base64"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"strings"
	"time"

	"golang.org/x/crypto/ssh"
	"golang.org/x/crypto/ssh/agent"
)

// HostKeyCallback is invoked when an unknown host key is encountered
type HostKeyPromptFunc func(host string, keyType string, fingerprint string) (bool, error)

type ClientConfig struct {
	Host           string
	Port           int
	User           string
	Password       string
	KeyPath        string
	Passphrase     string
	Timeout        time.Duration
	PromptHostKey  HostKeyPromptFunc
	KnownHostsPath string
}

type Client struct {
	cfg        ClientConfig
	sshClient  *ssh.Client
	hostKeySha string
}

// FingerprintSHA256 returns standard OpenSSH-style SHA256 base64 fingerprint
func FingerprintSHA256(key ssh.PublicKey) string {
	hash := sha256.Sum256(key.Marshal())
	b64 := base64.StdEncoding.EncodeToString(hash[:])
	return "SHA256:" + strings.TrimRight(b64, "=")
}

func defaultKnownHostsPath() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	dir := filepath.Join(home, ".config", "undercover")
	if err := os.MkdirAll(dir, 0700); err != nil {
		return "", err
	}
	return filepath.Join(dir, "known_hosts"), nil
}

// Dial connects to the remote host using pure Go crypto/ssh with TOFU host checking
func Dial(cfg ClientConfig) (*Client, error) {
	if cfg.Port <= 0 {
		cfg.Port = 22
	}
	if cfg.User == "" {
		cfg.User = "root"
	}
	if cfg.Timeout <= 0 {
		cfg.Timeout = 15 * time.Second
	}

	knownHostsFile := cfg.KnownHostsPath
	if knownHostsFile == "" {
		var err error
		knownHostsFile, err = defaultKnownHostsPath()
		if err != nil {
			return nil, err
		}
	}

	authMethods := []ssh.AuthMethod{}

	// 1. Try specified Private Key
	if cfg.KeyPath != "" {
		keyBytes, err := os.ReadFile(cfg.KeyPath)
		if err != nil {
			return nil, fmt.Errorf("could not read private key %s: %w", cfg.KeyPath, err)
		}

		var signer ssh.Signer
		if cfg.Passphrase != "" {
			signer, err = ssh.ParsePrivateKeyWithPassphrase(keyBytes, []byte(cfg.Passphrase))
		} else {
			signer, err = ssh.ParsePrivateKey(keyBytes)
		}
		if err != nil {
			return nil, fmt.Errorf("could not parse private key %s: %w", cfg.KeyPath, err)
		}
		authMethods = append(authMethods, ssh.PublicKeys(signer))
	}

	// 2. Try default SSH keys (~/.ssh/id_ed25519, id_rsa) if no key or password specified
	if cfg.KeyPath == "" && cfg.Password == "" {
		if home, err := os.UserHomeDir(); err == nil {
			for _, keyName := range []string{"id_ed25519", "id_rsa", "id_ecdsa"} {
				p := filepath.Join(home, ".ssh", keyName)
				if b, err := os.ReadFile(p); err == nil {
					if signer, err := ssh.ParsePrivateKey(b); err == nil {
						authMethods = append(authMethods, ssh.PublicKeys(signer))
					}
				}
			}
		}
	}

	// 3. Try SSH Agent if present
	if socket := os.Getenv("SSH_AUTH_SOCK"); socket != "" {
		if conn, err := net.Dial("unix", socket); err == nil {
			agentClient := agent.NewClient(conn)
			authMethods = append(authMethods, ssh.PublicKeysCallback(agentClient.Signers))
		}
	}

	// 4. Password auth if provided
	if cfg.Password != "" {
		authMethods = append(authMethods, ssh.Password(cfg.Password))
	}

	if len(authMethods) == 0 {
		return nil, fmt.Errorf("no SSH authentication method available (provide key or password)")
	}

	var capturedFingerprint string

	// TOFU HostKey Verification
	hostKeyCallback := func(hostname string, remote net.Addr, key ssh.PublicKey) error {
		fingerprint := FingerprintSHA256(key)
		capturedFingerprint = fingerprint
		hostPort := fmt.Sprintf("%s:%d", cfg.Host, cfg.Port)

		// Read existing known_hosts
		knownData, err := os.ReadFile(knownHostsFile)
		if err == nil {
			lines := strings.Split(string(knownData), "\n")
			for _, line := range lines {
				line = strings.TrimSpace(line)
				if line == "" || strings.HasPrefix(line, "#") {
					continue
				}
				parts := strings.Fields(line)
				if len(parts) >= 3 && parts[0] == hostPort {
					// Compare public key
					parsedKey, _, _, _, err := ssh.ParseAuthorizedKey([]byte(strings.Join(parts[1:], " ")))
					if err == nil {
						if bytes.Equal(parsedKey.Marshal(), key.Marshal()) {
							// Known and matches!
							return nil
						}
						// Key changed! Potential MITM
						return fmt.Errorf("HOST KEY MISMATCH for %s! Possible man-in-the-middle attack (old key does not match)", hostPort)
					}
				}
			}
		}

		// First time seeing this host (TOFU)
		if cfg.PromptHostKey != nil {
			approved, err := cfg.PromptHostKey(hostPort, key.Type(), fingerprint)
			if err != nil {
				return err
			}
			if !approved {
				return fmt.Errorf("host key verification rejected by user")
			}
		}

		// Save to known_hosts
		entry := fmt.Sprintf("%s %s\n", hostPort, string(ssh.MarshalAuthorizedKey(key)))
		f, err := os.OpenFile(knownHostsFile, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0600)
		if err == nil {
			defer f.Close()
			_, _ = f.WriteString(entry)
		}

		return nil
	}

	sshConfig := &ssh.ClientConfig{
		User:            cfg.User,
		Auth:            authMethods,
		HostKeyCallback: hostKeyCallback,
		Timeout:         cfg.Timeout,
	}

	addr := fmt.Sprintf("%s:%d", cfg.Host, cfg.Port)
	client, err := ssh.Dial("tcp", addr, sshConfig)
	if err != nil {
		return nil, fmt.Errorf("SSH connection to %s failed: %w", addr, err)
	}

	return &Client{
		cfg:        cfg,
		sshClient:  client,
		hostKeySha: capturedFingerprint,
	}, nil
}

// HostKeyFingerprint returns the verified SHA256 fingerprint of the remote host
func (c *Client) HostKeyFingerprint() string {
	return c.hostKeySha
}

// Close closes the underlying SSH connection
func (c *Client) Close() error {
	if c.sshClient != nil {
		return c.sshClient.Close()
	}
	return nil
}

// Run executes a remote command and returns stdout and stderr
func (c *Client) Run(cmd string) (string, string, error) {
	session, err := c.sshClient.NewSession()
	if err != nil {
		return "", "", fmt.Errorf("failed to create SSH session: %w", err)
	}
	defer session.Close()

	var stdoutBuf, stderrBuf bytes.Buffer
	session.Stdout = &stdoutBuf
	session.Stderr = &stderrBuf

	err = session.Run(cmd)
	return stdoutBuf.String(), stderrBuf.String(), err
}

// RunCombined executes a remote command and returns combined output
func (c *Client) RunCombined(cmd string) (string, error) {
	session, err := c.sshClient.NewSession()
	if err != nil {
		return "", fmt.Errorf("failed to create SSH session: %w", err)
	}
	defer session.Close()

	out, err := session.CombinedOutput(cmd)
	return string(out), err
}

// WriteFile writes data to a remote file with exact permissions (e.g. 0600)
func (c *Client) WriteFile(remotePath string, data []byte, perm os.FileMode) error {
	dir := filepath.Dir(remotePath)
	// Ensure directory exists with 0700
	if _, err := c.RunCombined(fmt.Sprintf("mkdir -p '%s' && chmod 0700 '%s'", dir, dir)); err != nil {
		return fmt.Errorf("failed to create directory %s: %w", dir, err)
	}

	// Write directly via base64 decoding with strict permissions set upfront
	b64 := base64.StdEncoding.EncodeToString(data)
	cmd := fmt.Sprintf("install -m %04o /dev/null '%s' && echo '%s' | base64 -d > '%s'", perm, remotePath, b64, remotePath)
	if out, err := c.RunCombined(cmd); err != nil {
		return fmt.Errorf("failed to write %s: %s (%w)", remotePath, out, err)
	}
	return nil
}

// ReadFile reads the contents of a remote file
func (c *Client) ReadFile(remotePath string) ([]byte, error) {
	cmd := fmt.Sprintf("base64 '%s'", remotePath)
	out, err := c.RunCombined(cmd)
	if err != nil {
		return nil, fmt.Errorf("failed to read %s: %s (%w)", remotePath, out, err)
	}

	cleaned := strings.ReplaceAll(strings.TrimSpace(out), "\n", "")
	data, err := base64.StdEncoding.DecodeString(cleaned)
	if err != nil {
		return nil, fmt.Errorf("failed to decode base64 file content: %w", err)
	}
	return data, nil
}
