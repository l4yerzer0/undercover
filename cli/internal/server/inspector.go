package server

import (
	"fmt"
	"strconv"
	"strings"

	"undercover/internal/executor"
)

// ServerInfo holds target server architecture, OS, and status
type ServerInfo struct {
	OSName           string
	OSVersion        string
	Architecture     string
	IsRoot           bool
	SystemdActive    bool
	TimeSynchronized bool
	ListeningPorts   []int
}

// Inspect collects system environment details over TargetExecutor
func Inspect(exec executor.TargetExecutor) (*ServerInfo, error) {
	info := &ServerInfo{}

	// 1. Check user id
	idOut, err := exec.RunCombined("id -u")
	if err != nil {
		return nil, fmt.Errorf("failed to check user privileges: %w", err)
	}
	info.IsRoot = strings.TrimSpace(idOut) == "0"
	if !info.IsRoot {
		// Test sudo
		sudoOut, sudoErr := exec.RunCombined("sudo -n id -u")
		if sudoErr != nil || strings.TrimSpace(sudoOut) != "0" {
			return nil, fmt.Errorf("running as non-root user without passwordless sudo: root privileges required")
		}
	}

	// 2. Check OS from /etc/os-release
	osRel, _ := exec.RunCombined("cat /etc/os-release")
	for _, line := range strings.Split(osRel, "\n") {
		if strings.HasPrefix(line, "ID=") {
			info.OSName = strings.Trim(strings.TrimPrefix(line, "ID="), `"`)
		}
		if strings.HasPrefix(line, "VERSION_ID=") {
			info.OSVersion = strings.Trim(strings.TrimPrefix(line, "VERSION_ID="), `"`)
		}
	}
	if info.OSName == "" {
		info.OSName = "linux"
	}

	// 3. Architecture
	archOut, err := exec.RunCombined("uname -m")
	if err != nil {
		return nil, fmt.Errorf("failed to detect system architecture: %w", err)
	}
	info.Architecture = strings.TrimSpace(archOut)

	// 4. Systemd check
	systemdOut, _ := exec.RunCombined("systemctl is-system-running 2>/dev/null || [ -d /run/systemd/system ] && echo ok")
	info.SystemdActive = strings.Contains(systemdOut, "running") ||
		strings.Contains(systemdOut, "degraded") ||
		strings.Contains(systemdOut, "ok")

	// 5. Time sync check
	timeOut, _ := exec.RunCombined("timedatectl show --property=NTPSynchronized 2>/dev/null || chronyc tracking 2>/dev/null")
	if strings.Contains(timeOut, "NTPSynchronized=yes") || strings.Contains(timeOut, "Reference time") {
		info.TimeSynchronized = true
	} else {
		// Attempt to query systemd-timesyncd or ntp status
		statusOut, _ := exec.RunCombined("systemctl is-active systemd-timesyncd 2>/dev/null || systemctl is-active chrony 2>/dev/null")
		if strings.Contains(statusOut, "active") {
			info.TimeSynchronized = true
		}
	}

	// 6. Occupied TCP ports
	portsOut, _ := exec.RunCombined("ss -tlnH 2>/dev/null || netstat -tln 2>/dev/null")
	for _, line := range strings.Split(portsOut, "\n") {
		fields := strings.Fields(line)
		for _, field := range fields {
			if idx := strings.LastIndex(field, ":"); idx != -1 {
				portStr := field[idx+1:]
				if p, err := strconv.Atoi(portStr); err == nil && p > 0 && p <= 65535 {
					info.ListeningPorts = append(info.ListeningPorts, p)
				}
			}
		}
	}

	return info, nil
}

// IsPortAvailable checks if a specific port is currently unused
func (s *ServerInfo) IsPortAvailable(port int) bool {
	for _, p := range s.ListeningPorts {
		if p == port {
			return false
		}
	}
	return true
}

// EnsureTimeSync enables timesyncd or chrony if not active
func EnsureTimeSync(exec executor.TargetExecutor) error {
	cmd := `
if command -v timedatectl >/dev/null 2>&1; then
  timedatectl set-ntp true 2>/dev/null || true
fi
if systemctl is-active --quiet systemd-timesyncd 2>/dev/null; then
  exit 0
elif systemctl is-active --quiet chrony 2>/dev/null; then
  exit 0
elif command -v apt-get >/dev/null 2>&1; then
  apt-get update -qq && apt-get install -y -qq systemd-timesyncd >/dev/null 2>&1 || true
  systemctl enable --now systemd-timesyncd 2>/dev/null || true
fi
`
	_, err := exec.RunCombined(cmd)
	return err
}
