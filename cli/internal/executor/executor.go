package executor

import (
	"bytes"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"undercover/internal/ssh"
)

// TargetExecutor abstracts command execution and file operations between SSH and Localhost
type TargetExecutor interface {
	Run(cmd string) (string, string, error)
	RunCombined(cmd string) (string, error)
	WriteFile(path string, data []byte, perm os.FileMode) error
	ReadFile(path string) ([]byte, error)
	HostKeyFingerprint() string
	Close() error
	IsLocal() bool
	Host() string
}

// SSHExecutor wraps a remote ssh.Client
type SSHExecutor struct {
	client *ssh.Client
	host   string
}

func NewSSHExecutor(client *ssh.Client, host string) *SSHExecutor {
	return &SSHExecutor{
		client: client,
		host:   host,
	}
}

func (s *SSHExecutor) Run(cmd string) (string, string, error) {
	return s.client.Run(cmd)
}

func (s *SSHExecutor) RunCombined(cmd string) (string, error) {
	return s.client.RunCombined(cmd)
}

func (s *SSHExecutor) WriteFile(path string, data []byte, perm os.FileMode) error {
	return s.client.WriteFile(path, data, perm)
}

func (s *SSHExecutor) ReadFile(path string) ([]byte, error) {
	return s.client.ReadFile(path)
}

func (s *SSHExecutor) HostKeyFingerprint() string {
	return s.client.HostKeyFingerprint()
}

func (s *SSHExecutor) Close() error {
	return s.client.Close()
}

func (s *SSHExecutor) IsLocal() bool {
	return false
}

func (s *SSHExecutor) Host() string {
	return s.host
}

// LocalExecutor runs commands directly on the host machine (e.g. binary installed directly on VPS)
type LocalExecutor struct {
	detectedHost string
}

func NewLocalExecutor(host string) *LocalExecutor {
	if host == "" {
		host = DetectPublicIP()
	}
	return &LocalExecutor{
		detectedHost: host,
	}
}

func (l *LocalExecutor) Run(cmd string) (string, string, error) {
	c := exec.Command("sh", "-c", cmd)
	var stdoutBuf, stderrBuf bytes.Buffer
	c.Stdout = &stdoutBuf
	c.Stderr = &stderrBuf
	err := c.Run()
	return stdoutBuf.String(), stderrBuf.String(), err
}

func (l *LocalExecutor) RunCombined(cmd string) (string, error) {
	c := exec.Command("sh", "-c", cmd)
	out, err := c.CombinedOutput()
	return string(out), err
}

func (l *LocalExecutor) WriteFile(remotePath string, data []byte, perm os.FileMode) error {
	dir := filepath.Dir(remotePath)
	if err := os.MkdirAll(dir, 0700); err != nil {
		return fmt.Errorf("failed to create directory %s: %w", dir, err)
	}

	tmpFile := fmt.Sprintf("%s.tmp.%d", remotePath, time.Now().UnixNano())
	if err := os.WriteFile(tmpFile, data, perm); err != nil {
		return fmt.Errorf("failed to write %s: %w", tmpFile, err)
	}

	if err := os.Chmod(tmpFile, perm); err != nil {
		_ = os.Remove(tmpFile)
		return err
	}

	return os.Rename(tmpFile, remotePath)
}

func (l *LocalExecutor) ReadFile(remotePath string) ([]byte, error) {
	return os.ReadFile(remotePath)
}

func (l *LocalExecutor) HostKeyFingerprint() string {
	return "localhost"
}

func (l *LocalExecutor) Close() error {
	return nil
}

func (l *LocalExecutor) IsLocal() bool {
	return true
}

func (l *LocalExecutor) Host() string {
	if l.detectedHost == "" {
		l.detectedHost = DetectPublicIP()
	}
	return l.detectedHost
}

// DetectPublicIP discovers the server's public IPv4/IPv6 address
func DetectPublicIP() string {
	client := &http.Client{Timeout: 3 * time.Second}
	endpoints := []string{
		"https://api.ipify.org",
		"https://ifconfig.me/ip",
		"https://icanhazip.com",
	}

	for _, ep := range endpoints {
		resp, err := client.Get(ep)
		if err == nil && resp.StatusCode == http.StatusOK {
			body, _ := io.ReadAll(resp.Body)
			resp.Body.Close()
			ip := strings.TrimSpace(string(body))
			if ip != "" && !strings.Contains(ip, "<") {
				return ip
			}
		}
	}
	return "127.0.0.1"
}
