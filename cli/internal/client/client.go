package client

import (
	"encoding/json"
	"fmt"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"

	"undercover/internal/state"
	"undercover/internal/ui"
)

const (
	DefaultLocalProxyPort = 2080
)

type ClientStatus struct {
	Connected      bool   `json:"connected"`
	PID            int    `json:"pid,omitempty"`
	Server         string `json:"server,omitempty"`
	LocalProxyAddr string `json:"local_proxy_addr,omitempty"`
	Uptime         string `json:"uptime,omitempty"`
}

func clientDir() (string, error) {
	home, err := os.UserHomeDir()
	if err != nil {
		return "", err
	}
	dir := filepath.Join(home, ".config", "undercover")
	_ = os.MkdirAll(dir, 0700)
	return dir, nil
}

func clientPidFile() (string, error) {
	dir, err := clientDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "client.pid"), nil
}

func clientConfigFile() (string, error) {
	dir, err := clientDir()
	if err != nil {
		return "", err
	}
	return filepath.Join(dir, "client_config.json"), nil
}

// FindOrInstallSingBox finds existing local sing-box or downloads it
func FindOrInstallSingBox() (string, error) {
	// 1. Check PATH
	if path, err := exec.LookPath("sing-box"); err == nil {
		return path, nil
	}

	// 2. Check standard locations
	home, _ := os.UserHomeDir()
	candidates := []string{
		"/usr/local/bin/sing-box",
		"/opt/local/bin/sing-box",
		filepath.Join(home, ".local", "bin", "sing-box"),
		filepath.Join(home, ".local", "share", "undercover", "sing-box"),
	}
	for _, p := range candidates {
		if _, err := os.Stat(p); err == nil {
			return p, nil
		}
	}

	// 3. Download matching OS & Arch
	destDir := filepath.Join(home, ".local", "share", "undercover")
	_ = os.MkdirAll(destDir, 0755)
	destBin := filepath.Join(destDir, "sing-box")

	goos := runtime.GOOS
	goarch := runtime.GOARCH

	ui.StepStatus(false, "Core Download", fmt.Sprintf("Downloading local sing-box client for %s/%s...", goos, goarch))

	downloadURL := fmt.Sprintf("https://github.com/SagerNet/sing-box/releases/download/v1.9.7/sing-box-1.9.7-%s-%s.tar.gz", goos, goarch)
	cmd := fmt.Sprintf(`
curl -sSL -o /tmp/singbox_client.tar.gz "%s"
tar -xzf /tmp/singbox_client.tar.gz -C /tmp
find /tmp -name sing-box -type f -exec cp {} "%s" \;
chmod 0755 "%s"
rm -rf /tmp/singbox_client.tar.gz
`, downloadURL, destBin, destBin)

	c := exec.Command("sh", "-c", cmd)
	if out, err := c.CombinedOutput(); err != nil {
		return "", fmt.Errorf("failed to download local sing-box: %s (%w)", string(out), err)
	}

	ui.StepStatus(true, "Core Download", "Local sing-box client ready")
	return destBin, nil
}

// GenerateClientConfig builds client-side JSON configuration
func GenerateClientConfig(srv state.LocalServer, userAuth string, proxyPort int) ([]byte, error) {
	if proxyPort <= 0 {
		proxyPort = DefaultLocalProxyPort
	}

	var proxyOutbound map[string]interface{}
	if srv.Protocol == "hysteria2" {
		proxyOutbound = map[string]interface{}{
			"type":        "hysteria2",
			"tag":         "proxy-out",
			"server":      srv.Host,
			"server_port": srv.VPNPort,
			"password":    userAuth,
			"tls": map[string]interface{}{
				"enabled":     true,
				"server_name": srv.ServerName,
				"insecure":    true,
			},
		}
	} else {
		proxyOutbound = map[string]interface{}{
			"type":        "vless",
			"tag":         "proxy-out",
			"server":      srv.Host,
			"server_port": srv.VPNPort,
			"uuid":        userAuth,
			"flow":        "xtls-rprx-vision",
			"tls": map[string]interface{}{
				"enabled":     true,
				"server_name": srv.ServerName,
				"reality": map[string]interface{}{
					"enabled":    true,
					"public_key": srv.PublicKey,
					"short_id":   srv.ShortID,
				},
			},
		}
	}

	cfg := map[string]interface{}{
		"log": map[string]interface{}{
			"disabled":  false,
			"level":     "warn",
			"timestamp": true,
		},
		"inbounds": []map[string]interface{}{
			{
				"type":        "mixed",
				"tag":         "mixed-in",
				"listen":      "127.0.0.1",
				"listen_port": proxyPort,
			},
		},
		"outbounds": []map[string]interface{}{
			proxyOutbound,
			{
				"type": "direct",
				"tag":  "direct",
			},
		},
	}

	return json.MarshalIndent(cfg, "", "  ")
}

// Connect starts the local background VPN client process
func Connect(srv state.LocalServer, userAuth string, proxyPort int) error {
	// Check if already running
	st := GetClientStatus()
	if st.Connected {
		return fmt.Errorf("VPN client is already running (PID %d)", st.PID)
	}

	binPath, err := FindOrInstallSingBox()
	if err != nil {
		return err
	}

	cfgBytes, err := GenerateClientConfig(srv, userAuth, proxyPort)
	if err != nil {
		return err
	}

	cfgPath, err := clientConfigFile()
	if err != nil {
		return err
	}
	if err := os.WriteFile(cfgPath, cfgBytes, 0600); err != nil {
		return err
	}

	// Start background daemon process
	cmd := exec.Command(binPath, "run", "-c", cfgPath)
	setSysProcAttr(cmd)

	if err := cmd.Start(); err != nil {
		return fmt.Errorf("failed to start sing-box client: %w", err)
	}

	pid := cmd.Process.Pid

	pidPath, _ := clientPidFile()
	_ = os.WriteFile(pidPath, []byte(strconv.Itoa(pid)), 0600)

	// Wait up to 3 seconds for port to open
	addr := fmt.Sprintf("127.0.0.1:%d", proxyPort)
	opened := false
	for i := 0; i < 30; i++ {
		time.Sleep(100 * time.Millisecond)
		conn, err := net.DialTimeout("tcp", addr, 150*time.Millisecond)
		if err == nil {
			conn.Close()
			opened = true
			break
		}
	}

	if !opened {
		// Clean up
		_ = cmd.Process.Kill()
		_ = os.Remove(pidPath)
		return fmt.Errorf("sing-box started but local proxy port %d failed to open", proxyPort)
	}

	// Try enabling macOS system proxy automatically
	if runtime.GOOS == "darwin" {
		enableMacOSProxy(proxyPort)
	}

	return nil
}

// Disconnect stops the local client process and restores network settings
func Disconnect() error {
	pidPath, err := clientPidFile()
	if err != nil {
		return err
	}

	data, err := os.ReadFile(pidPath)
	if os.IsNotExist(err) {
		return fmt.Errorf("VPN client is not currently connected")
	}

	pid, err := strconv.Atoi(strings.TrimSpace(string(data)))
	if err == nil && pid > 0 {
		// Stop client process
		killProcess(pid)
	}

	_ = os.Remove(pidPath)

	if runtime.GOOS == "darwin" {
		disableMacOSProxy()
	}

	return nil
}

// GetClientStatus queries the current local client process state
func GetClientStatus() *ClientStatus {
	pidPath, err := clientPidFile()
	if err != nil {
		return &ClientStatus{Connected: false}
	}

	data, err := os.ReadFile(pidPath)
	if os.IsNotExist(err) {
		return &ClientStatus{Connected: false}
	}

	pid, err := strconv.Atoi(strings.TrimSpace(string(data)))
	if err != nil || pid <= 0 {
		return &ClientStatus{Connected: false}
	}

	// Check if process exists and is alive
	if !isProcessAlive(pid) {
		// Process died, clean stale pid file
		_ = os.Remove(pidPath)
		return &ClientStatus{Connected: false}
	}

	return &ClientStatus{
		Connected:      true,
		PID:            pid,
		LocalProxyAddr: fmt.Sprintf("127.0.0.1:%d", DefaultLocalProxyPort),
	}
}

func enableMacOSProxy(port int) {
	// Detect default network interface (usually Wi-Fi)
	cmd := fmt.Sprintf(`
for service in "Wi-Fi" "Ethernet"; do
  if networksetup -getnetworkserviceenabled "$service" 2>/dev/null | grep -q "Enabled"; then
    networksetup -setsocksfirewallproxy "$service" 127.0.0.1 %d 2>/dev/null || true
    networksetup -setsocksfirewallproxystate "$service" on 2>/dev/null || true
    break
  fi
done
`, port)
	_ = exec.Command("sh", "-c", cmd).Run()
}

func disableMacOSProxy() {
	cmd := `
for service in "Wi-Fi" "Ethernet"; do
  networksetup -setsocksfirewallproxystate "$service" off 2>/dev/null || true
done
`
	_ = exec.Command("sh", "-c", cmd).Run()
}
