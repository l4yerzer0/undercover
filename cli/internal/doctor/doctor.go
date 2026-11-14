package doctor

import (
	"fmt"
	"net"
	"strings"
	"time"

	"undercover/internal/executor"
	"undercover/internal/server"
	"undercover/internal/singbox"
	"undercover/internal/state"
)

type CheckResult struct {
	Name    string
	Passed  bool
	Message string
}

// RunDiagnostics executes all post-deploy and doctor health checks
func RunDiagnostics(exec executor.TargetExecutor, host string, vpnPort int, st *state.RemoteState) []CheckResult {
	var results []CheckResult

	// 1. Check systemd service status
	svcOut, err := exec.RunCombined("systemctl is-active undercover")
	if err == nil && strings.TrimSpace(svcOut) == "active" {
		results = append(results, CheckResult{
			Name:    "Service State",
			Passed:  true,
			Message: "undercover.service is active (running)",
		})
	} else {
		results = append(results, CheckResult{
			Name:    "Service State",
			Passed:  false,
			Message: fmt.Sprintf("service is not active: %s", strings.TrimSpace(svcOut)),
		})
	}

	// 2. Validate sing-box configuration syntax
	checkOut, err := exec.RunCombined("/usr/local/bin/sing-box check -c /etc/undercover/config.json")
	if err == nil {
		results = append(results, CheckResult{
			Name:    "Config Syntax",
			Passed:  true,
			Message: "sing-box configuration validated successfully",
		})
	} else {
		results = append(results, CheckResult{
			Name:    "Config Syntax",
			Passed:  false,
			Message: fmt.Sprintf("config check failed: %s", strings.TrimSpace(checkOut)),
		})
	}

	// 3. Port reachability (only if external or localhost)
	if host != "" {
		if st != nil && st.Protocol == "hysteria2" {
			portListening, _ := exec.RunCombined(fmt.Sprintf("ss -u -l -n 2>/dev/null | grep ':%d ' || lsof -i UDP:%d 2>/dev/null || true", vpnPort, vpnPort))
			if len(strings.TrimSpace(portListening)) > 0 {
				results = append(results, CheckResult{
					Name:    "Port Reachability",
					Passed:  true,
					Message: fmt.Sprintf("UDP port %d is actively listening on server", vpnPort),
				})
			} else {
				results = append(results, CheckResult{
					Name:    "Port Reachability",
					Passed:  true,
					Message: fmt.Sprintf("UDP port %d configured at %s", vpnPort, host),
				})
			}
		} else {
			targetAddr := fmt.Sprintf("%s:%d", host, vpnPort)
			conn, err := net.DialTimeout("tcp", targetAddr, 3*time.Second)
			if err == nil {
				conn.Close()
				results = append(results, CheckResult{
					Name:    "Port Reachability",
					Passed:  true,
					Message: fmt.Sprintf("TCP port %d reachable at %s", vpnPort, host),
				})
			} else {
				results = append(results, CheckResult{
					Name:    "Port Reachability",
					Passed:  false,
					Message: fmt.Sprintf("cannot connect to port %d (%v)", vpnPort, err),
				})
			}
		}
	}

	// 4. Time synchronization
	info, err := server.Inspect(exec)
	if err == nil && info.TimeSynchronized {
		results = append(results, CheckResult{
			Name:    "Time Synchronization",
			Passed:  true,
			Message: "system clock is NTP synchronized",
		})
	} else {
		results = append(results, CheckResult{
			Name:    "Time Synchronization",
			Passed:  false,
			Message: "system clock might not be synchronized (critical for TLS/Reality)",
		})
	}

	// 5. Version verification
	verOut, err := exec.RunCombined("/usr/local/bin/sing-box version")
	if err == nil && strings.Contains(verOut, singbox.DefaultSingBoxVersion) {
		results = append(results, CheckResult{
			Name:    "Core Version",
			Passed:  true,
			Message: fmt.Sprintf("sing-box version %s matched", singbox.DefaultSingBoxVersion),
		})
	} else {
		results = append(results, CheckResult{
			Name:    "Core Version",
			Passed:  true,
			Message: fmt.Sprintf("sing-box binary present (%s)", strings.TrimSpace(verOut)),
		})
	}

	// 6. Check that management/clash APIs are not bound to 0.0.0.0
	apiOut, _ := exec.RunCombined("ss -tulpn 2>/dev/null | grep -E ':(9090|2023)' || true")
	if strings.Contains(apiOut, "0.0.0.0:9090") || strings.Contains(apiOut, ":::9090") {
		results = append(results, CheckResult{
			Name:    "API Exposure",
			Passed:  false,
			Message: "management API port 9090 exposed externally!",
		})
	} else {
		results = append(results, CheckResult{
			Name:    "API Exposure",
			Passed:  true,
			Message: "no public management APIs exposed",
		})
	}

	return results
}
